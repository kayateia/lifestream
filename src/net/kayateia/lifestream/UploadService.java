/**
	LifeStream - Instant Photo Sharing
	Copyright (C) 2014 Kayateia

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.kayateia.lifestream;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class UploadService extends Service {
	private static final String LOG_TAG = "LifeStream/UploadService";
	private static final int NOTIFY_ID = 79;
	private static final int WAKELOCK_TIMEOUT = 60000;
	private static final String NOTIFICATION_THUMBNAIL_PATH = Media.BASE_DIR + "/tempthumb";
	private File _storagePath, _tempThumbPath;

	// Manually kicks the stream service from elsewhere.
	static public void Kick(Context context) {
		final Intent uploadServiceIntent = new Intent(context, UploadService.class);
		context.startService(uploadServiceIntent);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(LOG_TAG, "UploadService kicked");

		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");

		// See CaptureService for comments on this wake lock arrangement.
		boolean needsRelease = true;
		try {
			// If we don't release it within the timeout somehow, release it anyway.
			// This may interrupt a long backlog upload or something, but this
			// is really about as long as we can reasonably expect to take.
			Log.v(LOG_TAG, "Acquire wakelock");
			if (wl != null)
				wl.acquire(WAKELOCK_TIMEOUT);
			if (!Network.IsActive(this)) {
				Log.i(LOG_TAG, "No network active, giving up");
				return START_NOT_STICKY;
			}

			// Do the check in a thread.
			new AsyncTask<UploadService, Void, Void>() {
				@Override
				protected Void doInBackground(UploadService... svc) {
					svc[0].checkUploads();
					return null;
				}

				@Override
				protected void onPostExecute(Void foo) {
					Log.v(LOG_TAG, "Release wakelock by worker");
					if (wl != null && wl.isHeld())
						wl.release();
				}
			}.execute(this);
			needsRelease = false;
		} finally {
			if (needsRelease) {
				Log.v(LOG_TAG, "Release wakelock by default");
				if (wl != null && wl.isHeld())
					wl.release();
			}
		}

		// There's no need to worry about this.. we'll get re-kicked by the alarm.
		return START_NOT_STICKY;
	}

	// Check for new things locally that we need to upload.
	private void checkUploads() {
		Log.i(LOG_TAG, "Checking for images to upload to the server");
		if (!initStorage())
			return;

		// Retrieve our auth token and user parameters. If they're not there, we can't do anything.
		Settings settings = new Settings(this);
		String authToken = settings.getAuthToken();
		if (authToken.isEmpty() || !settings.getEnabled()) {
			Log.i(LOG_TAG, "Not doing upload check: not configured, or disabled");
			return;
		}

		// Enumerate files that don't end in .temp, and aren't .nomedia
		for (File f : _storagePath.listFiles()) {
			if (f.getName().endsWith(CaptureService.TEMP_EXTENSION))
				continue;
			if (f.getName().equals(Media.NO_MEDIA))
				continue;

			try {
				Log.i(LOG_TAG, "Uploading " + f.getName());

				// Upload it to its home on the server.
				HashMap<String, String> params = new HashMap<String, String>();
				params.put("auth", authToken);

				String baseUrl = Settings.GetBaseUrl();
				String result = HttpMultipartUpload.Upload(new URL(baseUrl + "upload.php"),
					f,
					"uploadedfile", params, getBaseContext());

				if (result != null) {
					final Resources res = getResources();
					String notificationMsg = res.getString(R.string.upload_detail);

					// Verify that we got a proper result, because the server
					// might have failed to process, too.
					try {
						JSONObject root = new JSONObject(result);
						String success = root.optString("success");
						if (success.equals(""))
							throw new IOException("Bad server answer: " + root.optString("message"));

						String nmsg = root.optString("message");
						if (!nmsg.equals(""))
							notificationMsg = nmsg;
					} catch (JSONException e) {
						if (settings.getVerbose()) {
							Notifications.NotifyError(this, NOTIFY_ID, true, res.getString(R.string.fail_ticker),
								res.getString(R.string.fail_title), res.getString(R.string.fail_download));
						}
						Log.w(LOG_TAG, "Can't parse download response: " + result);
						return;
					}

					if (settings.getUploadNotifications()) {
						// See if we can find the original image path.
						ProcessedImages processed = ProcessedImages.GetSingleton(this);
						String fullPath = processed.getFullPath(f.getName());

						// Copy the file so we can use it as a notification. It's okay if we
						// overwrite another because we're overwriting notifications too.
						final File thumb = new File(_tempThumbPath.getAbsolutePath().concat(File.separator).concat("tempthumb.jpg"));
						Media.FileCopy(f, thumb);

						Notifications.NotifyUpload(this, NOTIFY_ID, true,
								res.getString(R.string.upload_ticker),
								res.getString(R.string.upload_title),
								notificationMsg, fullPath, thumb.getAbsolutePath());
					}
				} else {
					// Result was null, meaning there was a 0 size image to upload... Do we want to do anything more here?
				}

				// Upload was successful, so delete the image.
				Log.i(LOG_TAG, "Deleting successfully uploaded " + f.getName());
				f.delete();
			} catch (final IOException ex) {
				if (settings.getVerbose()) {
					final Resources res = getResources();
					Notifications.NotifyError(getBaseContext(), NOTIFY_ID, false,
							res.getString(R.string.fail_ticker),
							res.getString(R.string.fail_title),
							res.getString(R.string.fail_upload));
				}
				Log.w(LOG_TAG, "Upload failed", ex);
			}
		}
		Log.i(LOG_TAG, "Upload check complete");
	}

	private boolean initStorage() {
		try {
			if (!Media.IsMediaMounted()) {
				Log.i(LOG_TAG, "External storage not mounted; trying again later.");
				return false;
			}
			_storagePath = Media.InitStorage(this, CaptureService.UPLOAD_DIRECTORY_NAME, false);
			_tempThumbPath = Media.InitStorage(this, NOTIFICATION_THUMBNAIL_PATH, true);
		} catch (Exception e) {
			final Resources res = getResources();
			Notifications.NotifyError(this, NOTIFY_ID, false, res.getString(R.string.fail_ticker),
					res.getString(R.string.fail_title), e.getMessage());
			Log.e(LOG_TAG, "Couldn't init storage: " + e);
		}

		return _storagePath != null;
	}
}

