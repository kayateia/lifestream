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
import java.util.Date;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/**
 * @author kaya
 *
 */
public class StreamService extends Service {
	private static final String LOG_TAG = "LifeStream/StreamService";
	private static final String DIRECTORY_NAME = Media.BASE_DIR;
	private static final int NOTIFY_ID = 78;
	private static final int WAKELOCK_TIMEOUT = 60000;
	private File _storagePath;
	private static final String USERDIR_PREFIX = "Lifestream_";

	// Manually kicks the stream service from elsewhere.
	static public void Kick(Context context) {
		final Intent serviceStartIntent = new Intent(context, StreamService.class);
		context.startService(serviceStartIntent);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.v(LOG_TAG, "Kicked");

		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");

		// See CaptureService for comments on this wake lock arrangement.
		boolean needsRelease = true;
		try {
			// If we don't release it within the timeout somehow, release it anyway.
			// This may interrupt a long backlog download or something, but this
			// is really about as long as we can reasonably expect to take.
			Log.v(LOG_TAG, "Acquire wakelock");
			if (wl != null)
				wl.acquire(WAKELOCK_TIMEOUT);

			if (!Network.IsActive(this)) {
				Log.i(LOG_TAG, "No network active, giving up");
				return START_NOT_STICKY;
			}

			// Do the check in a thread.
			new AsyncTask<StreamService, Void, Void>() {
				@Override
				protected Void doInBackground(StreamService... svc) {
					svc[0].checkNewImages();
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

	// Check for new things on the server that we need to download.
	private void checkNewImages() {
		Log.i(LOG_TAG, "Checking for new images on server");
		if (!initStorage())
			return;

		// Retrieve our auth token and user parameters. If they're not there, we can't do anything.
		Settings settings = new Settings(this);
		String authToken = settings.getAuthToken();
		if (authToken.isEmpty() || !settings.getEnabled()) {
			Log.i(LOG_TAG, "Not doing download check: not configured, or disabled");
			return;
		}

		// Allow a week back to make sure we didn't miss some due to crashes/race conditions/etc.
		int lastCheck = settings.getLastCheckInt() - (60*60*24*7);
		if (lastCheck < 0)
			lastCheck = ((int)((new Date()).getTime() / 1000)) - (60*60*24*7);

		final Resources res = getResources();
		final StreamService us = this;
		MediaScannerWrapper scanner = new MediaScannerWrapper(this) {
			@Override
			protected void scanned(String path, Uri uri) {
				// The user directory will be the last path component. We can hash
				// that and make a unique notification ID. This isn't guaranteed to be
				// unique, but for our test purposes, it should work.

				// Convolution: Java has it. This pulls the last component of the path, and then
				// further pulls the "Lifestream_" prefix to get the actual user name.
				File pathFile = new File(path);
				File withoutFilenameFile = pathFile.getParentFile();
				String withoutParentDir = withoutFilenameFile.getParent();
				String userDir = path.substring(withoutParentDir.length() + 1, withoutFilenameFile.getPath().length() );
				userDir = userDir.substring(USERDIR_PREFIX.length());

				// Make a hash of it.
				int hash = userDir.hashCode() % 100000;
				int notifyId = NOTIFY_ID + 100 + hash;

				Notifications.NotifyDownload(us, notifyId, true, res.getString(R.string.download_ticker),
					/*res.getString(R.string.download_title)*/ "From " + userDir, getMessage(path),
					uri);
			}
		};
		try {
			HashMap<String, String> params = new HashMap<String, String>();
			params.put("auth", authToken);
			params.put("date", ""+lastCheck);

			// Save the current time as a cutoff for future checks. We do it here so as
			// to be conservative when setting the cutoff for the next check.
			Date newLastCheck = new Date();

			String notificationMsg = res.getString(R.string.download_detail);

			String baseUrl = Settings.GetBaseUrl();
			URL url = new URL(baseUrl + "check-images.php");
			String result = HttpMultipartUpload.DownloadString(url, params, this);
			String[] users, files, paths;
			try {
				JSONObject root = new JSONObject(result);
				JSONArray images = root.getJSONArray("images");
				users = new String[images.length()];
				files = new String[images.length()];
				paths = new String[images.length()];

				for (int i=0; i<images.length(); ++i) {
					JSONObject obj = images.getJSONObject(i);
					users[i] = obj.getString("user");
					files[i] = obj.getString("file");
					paths[i] = obj.getString("path");
				}

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

			// We'll just save the last one so that it will open the top image in the stack.
			String galleryPath = null;
			for (int i=0; i<users.length; ++i) {
				// We're expecting here: user, image name, relative URL path
				String user = users[i];
				String imageName = files[i];
				String urlPath = paths[i];

				// Turn the file.jpg into file_user.jpg.
				// http://stackoverflow.com/questions/4545937/java-splitting-the-filename-into-a-base-and-extension
				String[] fileParts = imageName.split("\\.(?=[^\\.]+$)");
				String localFileName = fileParts[0] + "_" + user + "." + fileParts[1];
				String localPath = getStorageForUser(user).concat(File.separator).concat(localFileName);

				// Does it exist already?
				File localFile = new File(localPath);
				if (localFile.exists()) {
					Log.i(LOG_TAG, "Skipping " + urlPath + " because it already exists");
				} else {
					Log.i(LOG_TAG, "Download file: " + urlPath + " to " + localFileName);
					URL dlurl = new URL(baseUrl + urlPath);
					HttpMultipartUpload.DownloadFile(dlurl, localFile, null, this);
					scanner.addFile(localFile.getAbsolutePath(), notificationMsg);
					galleryPath = localFile.getAbsolutePath();
				}
			}
			scanner.scan();
			Log.i(LOG_TAG, "Download check complete");

			// Success! Update the last check time.
			settings.setLastCheck(newLastCheck);
			settings.commit();
		} catch (IOException e) {
			if (settings.getVerbose()) {
				Notifications.NotifyError(this, NOTIFY_ID, true, res.getString(R.string.fail_ticker),
					res.getString(R.string.fail_title), res.getString(R.string.fail_download));
			}
			Log.w(LOG_TAG, "Download failed", e);
		}
	}

	private boolean initStorage() {
		try {
			if (!Media.IsMediaMounted()) {
				Log.i(LOG_TAG, "External storage not mounted; trying again later.");
				return false;
			}
			_storagePath = Media.InitStorage(this, DIRECTORY_NAME, false);
		} catch (Exception e) {
			final Resources res = getResources();
			Notifications.NotifyError(this, NOTIFY_ID, false, res.getString(R.string.fail_ticker),
					res.getString(R.string.fail_title), e.getMessage());
			Log.e(LOG_TAG, "Couldn't init storage: " + e);
		}

		return _storagePath != null;
	}

	private String getStorageForUser(String username) {
		String path = _storagePath.getAbsolutePath().concat(File.separator).concat(USERDIR_PREFIX).concat(username);
		File pathFile = new File(path);
		if (!pathFile.isDirectory() && !pathFile.mkdirs()) {
			Log.w(LOG_TAG, "Failed to create user path " + path);
			return _storagePath.getAbsolutePath();
		}

		return path;
	}
}
