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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.os.FileObserver;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

public class MediaListenerService extends Service {
	private static final String LOG_TAG = "LifeStream/MediaListenerService";
	private static final int NOTIFY_ID = 77;
	private static final int WAKELOCK_TIMEOUT = 10000;

	private Settings _settings;
	private PhotoMediaObserver _mediaObserver;
	private WatchedPaths _watched;
	private File _baseStoragePath;
	private boolean _started;
	private Hashtable<String,PhotoFileObserver> _fileObservers;
	private ProcessedImages _db;
	private ImageQueue _queue;

	// Manually starts the listener service from elsewhere. Typically only used in the main activity.
	static public void Start(Context context) {
		final Intent listenerServiceIntent = new Intent(context, MediaListenerService.class);
		context.startService(listenerServiceIntent);
	}

	public MediaListenerService() {
		super();
		_started = false;
	}

	@Override
	public IBinder onBind(Intent intent) { return null; }

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (!_started) {
			_baseStoragePath = Media.GetBaseStorage(this);
			_watched = new WatchedPaths(new Settings(this));
			_mediaObserver = new PhotoMediaObserver(this);
			_db = ProcessedImages.GetSingleton(getBaseContext());
			_queue = ImageQueue.GetSingleton(getBaseContext());

			// Start the stream check alarm. (FIXME)
			CheckAlarm.SetAlarm(this);

			getApplicationContext().getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, _mediaObserver);

			final File sdCard = Environment.getExternalStorageDirectory();
			_fileObservers = new Hashtable<String,PhotoFileObserver>();
			for (final WatchedPaths.Path p : _watched.getPaths()) {
				PhotoFileObserver obs = new PhotoFileObserver(this, sdCard + p.path, FileObserver.CLOSE_WRITE);
				obs.startWatching();
			}

			_started = true;
			Log.i(LOG_TAG, "Service started");
		} else {
			Log.i(LOG_TAG, "Service previously started");
		}

		return START_STICKY;
	}

	// Returns true if this one should kick the capture service.
	private boolean processFile(File source) {
		// Make sure it's not one of ours.
		if (source.getAbsolutePath().startsWith(_baseStoragePath.getAbsolutePath())) {
			Log.i(LOG_TAG, "Ignoring own file " + source);
			return false;
		} else {
			final File[] extras = _watched.checkPath(source);
			if (extras == null) {
				Log.i(LOG_TAG, "Ignoring path-excluded file " + source);
				return false;
			}
			if (extras.length > 0) {
				Log.i(LOG_TAG, "Received " + extras.length + " extras:");
				for (File f : extras) {
					Log.i(LOG_TAG, "   " + f.getAbsolutePath());
				}
			}

			// Check the database too.
			if (_db.haveProcessed(source.getName())) {
				Log.w(LOG_TAG, "Already have " + source + " in the database. Skipping. (This shouldn't happen.)");
				return false;
			}

			// Pass! Put it on the queue.
			_queue.addToQueue(source.getAbsolutePath());
			Log.i(LOG_TAG, "Added item " + source.getAbsolutePath() + " to the processing queue");
			return true;
		}
	}

	private class PhotoFileObserver extends FileObserver {
		String _basePath;
		MediaListenerService _service;

		public PhotoFileObserver(MediaListenerService service, String path, int mask) {
			super(path, mask);
			_service = service;
			_basePath = path;
			Log.i(LOG_TAG, "Starting photo file observer on " + path);
		}

		@Override
		public void onEvent(int event, String path) {
			// The wacky acquire/release cycle is unfortunately needed because
			// things happen through an AsyncTask. We assume a release is required
			// unless onChangeInner informs us (by returning false) that the
			// duty has been passed on to its AsyncTask. In theory, AsyncTask
			// should prevent it from being forgotten.
			PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
			final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");

			try {
				// If we somehow don't release it within the timeout, do it anyway.
				Log.v(LOG_TAG, "PhotoFileObserver: Acquire wakelock");
				if (wl != null)
					wl.acquire(WAKELOCK_TIMEOUT);

				onChangeInner(path);
			} finally {
				Log.v(LOG_TAG, "PhotoFileObserver: Release wakelock by default");
				if (wl != null && wl.isHeld())
					wl.release();
			}
		}

		void onChangeInner(String path) {
			Log.i(LOG_TAG, "Received file event: " + path);
			if (_service.processFile(new File(_basePath + File.separatorChar + path)))
				CaptureService.Kick(getBaseContext());
		}
	}

	private class PhotoMediaObserver extends ContentObserver {
		MediaListenerService _service;

		public PhotoMediaObserver(MediaListenerService service) {
			super(null);
			_service = service;
		}

		@Override
		public void onChange(boolean selfChange) {
			// We have to hold a wake lock here, or the user may lock the
			// phone right after a picture, preventing us from even determining
			// what it is, let alone scaling it. This may result in a weird
			// notification flood later, or pictures strangely being delayed.
			//
			// The wacky acquire/release cycle is unfortunately needed because
			// things happen through an AsyncTask. We assume a release is required
			// unless onChangeInner informs us (by returning false) that the
			// duty has been passed on to its AsyncTask. In theory, AsyncTask
			// should prevent it from being forgotten.
			PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
			final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");

			try {
				// If we somehow don't release it within the timeout, do it anyway.
				Log.v(LOG_TAG, "PhotoMediaServer: Acquire wakelock");
				if (wl != null)
					wl.acquire(WAKELOCK_TIMEOUT);
				super.onChange(selfChange);
				onChangeInner();
			} finally {
				Log.v(LOG_TAG, "PhotoMediaServer: Release wakelock by default");
				if (wl != null && wl.isHeld())
					wl.release();
			}
		}

		private void onChangeInner() {
			final List<Media> mediaList = readFromMediaStore(getApplicationContext(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
			boolean kickCapture = false;

			for (final Media media : mediaList) {
				// Build the paths
				final File source = media.getFile();
				kickCapture = _service.processFile(source) || kickCapture;
			}

			// Kick off the capture service if we need to
			if (kickCapture) {
				CaptureService.Kick(getBaseContext());
			}
		}

		private List<Media> readFromMediaStore(Context context, Uri uri) {
			final List<Media> newMedia = new ArrayList<Media>();
			boolean firstTime = true;
			
			// Most recent processed (that we know about)
			if (_settings == null) {
				_settings = new Settings(context);
			}
			long lastImageTimestamp = _settings.getLastImageProcessedTimestamp();
			
			// New last timestamp?
			long newLastTimestamp = -1;
			
			// Column indexes
			int dataColumn = -1;
			int mimeTypeColumn = -1;
			int timestampColumn = -1;
			
			final Cursor cursor = context.getContentResolver().query(uri, null, null, null, "date_added DESC");
			while (cursor.moveToNext()) {
				// The column indexes will stay the same, so just get them once
				if (firstTime) {
					dataColumn = cursor.getColumnIndexOrThrow(MediaColumns.DATA);
					mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaColumns.MIME_TYPE);
					timestampColumn = cursor.getColumnIndexOrThrow(MediaColumns.DATE_ADDED);
					firstTime = false;
				}
				
				// Grab the details
				String filePath = cursor.getString(dataColumn);
				String mimeType = cursor.getString(mimeTypeColumn);
				long timestamp = cursor.getLong(timestampColumn);
				
				// This should only happen once because the records should be sorted, but >_>
				if (timestamp > newLastTimestamp) {
					newLastTimestamp = timestamp;
				}
				
				if (timestamp > lastImageTimestamp) {
					// Create the "file" reference and do a sanity check
					final File file = new File(filePath);
					if (file.exists() && file.isFile() && file.canRead() && (file.length() > 0)) {
						// Add the file
						newMedia.add(new Media(file, mimeType));
					}
				}
			}
			cursor.close();
			
			// Update?
			if (newLastTimestamp > lastImageTimestamp) {
				_settings.setLastImageProcessedTimestamp(newLastTimestamp);
				_settings.commit();
			}
			
			// Return the list
			return newMedia;
		}
	}
}
