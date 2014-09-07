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

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.google.android.gcm.GCMRegistrar;

public class Network {
	static final String LOG_TAG = "LifeStream/Login";

	static public synchronized String DoLogin(Context context, String userText, String passText) {
		Settings settings = new Settings(context);

		// See if we have a GCM ID first.
		String gcmId = settings.getGcmId();
		if (gcmId.equals(""))
			gcmId = GCMRegistrar.getRegistrationId(context);
		if (gcmId.equals("")) {
			// Request one. We'll pick up its contents and re-register later.
			GCMRegistrar.register(context, GCMIntentService.GCM_SENDER_ID);
		} else {
			String oldId = settings.getGcmId();
			if (!oldId.equals("") && !gcmId.equals(oldId)) {
				Log.w(LOG_TAG, "GCMRegistrar returned a different ID ("+gcmId
						+") than we thought we had ("+oldId+")");
			}
		}
		Log.i(LOG_TAG, "Our GCM ID for user login is: " + gcmId);

		// Do we have an auth ID?
		String authId = settings.getAuthToken();
		if (authId.equals("")) {
			authId = userText + "+" + Settings.GetAndroidID(context);
		}

		// Now contact the LifeStream server.
		HashMap<String, String> parameters = new HashMap<String, String>();
		parameters.put("login", userText);
		parameters.put("pass", passText);
		parameters.put("gcm", gcmId);
		parameters.put("auth", authId);
		String resultText = "";
		try {
			String baseUrl = Settings.GetBaseUrl();
			resultText = HttpMultipartUpload.DownloadString(
				new URL(baseUrl + "login.php"),
				parameters, context);

			// Crack the JSON. If we can't parse it, we fail below. If we
			// parse it but get a 'message', we failed too. Otherwise we
			// should have some login info.
			JSONObject result = new JSONObject(resultText);
			String message = result.optString("message");
			if (message != null && message.length() > 0) {
				Log.e(LOG_TAG, "Couldn't log in: " + message);
				return null;
			}

			settings.setUserName(userText);
			settings.setPassword(passText);
			settings.setGcmId(gcmId);
			settings.setAuthToken(authId);
			settings.commit();

			return result.optString("name");
		} catch (IOException e) {
			Log.e(LOG_TAG, "Couldn't log in: " + e);
			return null;
		} catch (JSONException e) {
			Log.e(LOG_TAG, "Couldn't parse login response: " + resultText);
			return null;
		}
	}

	static public boolean IsActive(Context context) {
		ConnectivityManager cm =
			(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		if (activeNetwork == null)
			return false;
		else
			return activeNetwork.isConnectedOrConnecting();
	}
}
