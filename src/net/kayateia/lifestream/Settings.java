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

import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

/**
 * @author kaya
 *
 * Helps us find and use the app settings.
 */
public class Settings {
	public static final String PREFS_NAME = "LifeStreamPrefs";
	public static final String PREFS_USER = "username";
	public static final String PREFS_PASS = "password";
	public static final String PREFS_AUTH = "authToken";
	public static final String PREFS_GCM = "gcmId";
	public static final String PREFS_LAST_CHECK = "lastCheck";
	public static final String PREFS_ENABLED = "enabled";
	public static final String PREFS_VERBOSE = "verbose";
	public static final String PREFS_UPLOAD_NOTIFICATIONS = "uploadNotifications";
	public static final String PREFS_VIBRATION = "vibration";
    public static final String PREFS_SOUNDS = "sounds";
	public static final String PREFS_PATHS = "paths";
	public static final String PREFS_QUALITY = "hqscale";
	public static final String PREFS_IMAGE_SIZE = "imageSize";
	public static final String LAST_IMAGE_TIMESTAMP = "lastImageTimestamp";
    
	// Default time, in seconds, to set the "last timestamp" if none exists
	public static final long DEFAULT_DURATION = 1 * 24 * 60 * 60;

    public static final String BASE_URL = "<LifeStream Base URL>";

	// Thunk allows us to swap out URLs later if we want me to.
	static public String GetBaseUrl() {
		return BASE_URL;
	}

	// Get a unique ID for this device.
	static public String GetAndroidID(Context context) {
		String id = android.provider.Settings.Secure.getString(context.getContentResolver(),
			android.provider.Settings.Secure.ANDROID_ID);
		if (id == null || id.equals("")) {
			UUID uuid = UUID.randomUUID();
			id = uuid.toString();
		}
		return id;
	}

	Context _context;
	SharedPreferences _settings;
	SharedPreferences.Editor _editor;

	public Settings(Context context) {
		_context = context;
		_settings = _context.getSharedPreferences(PREFS_NAME, 0);
	}

	public String getUserName() { return _settings.getString(PREFS_USER, ""); }
	public void setUserName(String n) {
		edit();
		_editor.putString(PREFS_USER, n);
	}

	public String getPassword() { return _settings.getString(PREFS_PASS, ""); }
	public void setPassword(String p) {
		edit();
		_editor.putString(PREFS_PASS, p);
	}

	public String getAuthToken() { return _settings.getString(PREFS_AUTH, ""); }
	public void setAuthToken(String t) {
		edit();
		_editor.putString(PREFS_AUTH, t);
	}

	public String getGcmId() { return _settings.getString(PREFS_GCM, ""); }
	public void setGcmId(String t) {
		edit();
		_editor.putString(PREFS_GCM, t);
	}

	public int getLastCheckInt() { return _settings.getInt(PREFS_LAST_CHECK, 0); }
	public Date getLastCheck() { return new Date((long)_settings.getInt(PREFS_LAST_CHECK, 0) * 1000); }
	public void setLastCheck(Date d) {
		edit();
		_editor.putInt(PREFS_LAST_CHECK, (int)(d.getTime() / 1000));
	}

	public boolean getEnabled() { return _settings.getBoolean(PREFS_ENABLED, false); }
	public void setEnabled(boolean e) {
		edit();
		_editor.putBoolean(PREFS_ENABLED, e);
	}

	public boolean getUploadNotifications() { return _settings.getBoolean(PREFS_UPLOAD_NOTIFICATIONS, true); }
	public void setUploadNotifications(boolean e) {
		edit();
		_editor.putBoolean(PREFS_UPLOAD_NOTIFICATIONS, e);
	}

	public boolean getVerbose() { return _settings.getBoolean(PREFS_VERBOSE, false); }
	public void setVerbose(boolean v) {
		edit();
		_editor.putBoolean(PREFS_VERBOSE, v);
	}

	public boolean getVibration() { return _settings.getBoolean(PREFS_VIBRATION, true); }
	public void setVibration(boolean v) {
		edit();
		_editor.putBoolean(PREFS_VIBRATION, v);
	}

    public boolean getSoundsEnabled() { return _settings.getBoolean(PREFS_SOUNDS, true); }
    public void setSoundsEnabled(boolean v) {
        edit().putBoolean(PREFS_SOUNDS, v);
    }

	// This is stored as a JSON array. WatchedPaths interprets it.
	public String getPaths() { return _settings.getString(PREFS_PATHS, ""); }
	public void setPaths(String p) {
		edit();
		_editor.putString(PREFS_PATHS, p);
	}

	public boolean getHighQualityScale() { return _settings.getBoolean(PREFS_QUALITY, true); }
	public void setHighQualityScale(boolean v) {
		edit();
		_editor.putBoolean(PREFS_QUALITY, v);
	}

	public int getImageSize() { return _settings.getInt(PREFS_IMAGE_SIZE, 800); }
	public void setImageSize(int size) {
		edit();
		_editor.putInt(PREFS_IMAGE_SIZE, size);
	}
	
	public long getLastImageProcessedTimestamp() { return _settings.getLong(LAST_IMAGE_TIMESTAMP, (System.currentTimeMillis() / 1000L) - DEFAULT_DURATION); }
	public void setLastImageProcessedTimestamp(long timestamp) {
		edit();
		_editor.putLong(LAST_IMAGE_TIMESTAMP, timestamp);
	}

	public void commit() {
		edit();
		_editor.commit();
		_editor = null;
	}

	SharedPreferences.Editor edit() {
		if (_editor == null)
			_editor = _settings.edit();
        return _editor;
	}
}
