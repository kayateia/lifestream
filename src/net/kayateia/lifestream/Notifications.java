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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

/**
 * @author kaya
 *
 */
public class Notifications {
	private static final String LOG_TAG = "LifeStream/Notifications";

	static Intent GetBaseIntent(Context context, final String imagePath) {
		Intent intent;
		if (imagePath == null) {
			intent = new Intent(context, LifeStreamActivity.class);
		} else {
			intent = new Intent();
			intent.setAction(Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.parse("file://" + imagePath), "image/*");
		}
		return intent;
	}

	static public void NotifyError(Context context, int id, boolean replace, final String tickerText, final String title, final String text) {
		Intent intent = GetBaseIntent(context, null);
		NotifyCommon(context, id, replace, tickerText, title, text, android.R.drawable.stat_notify_error, intent, true, null);
	}

	static public void NotifyDownload(Context context, int id, boolean replace, final String tickerText, final String title, final String text, final Uri imageUri) {
		Intent intent = GetBaseIntent(context, "");
		intent.setDataAndType(imageUri, "image/*");
		NotifyCommon(context, id, replace, tickerText, title, text, R.drawable.ic_stat_success_dn, intent, true, imageUri);
	}

	static public void NotifyUpload(Context context, int id, boolean replace, final String tickerText, final String title, final String text, final String imagePath, final String thumbnailPath) {
		Intent intent = GetBaseIntent(context, imagePath);
		NotifyCommon(context, id, replace, tickerText, title, text, R.drawable.ic_stat_success_up, intent, false, Uri.parse("file://" + thumbnailPath));
	}

	static public void NotifyCommon(Context context, int id, boolean replace,
		final String tickerText, final String title, final String text, final int icon,
		final Intent notificationIntent, final boolean showLed, final Uri contentUri)
	{
		int flags = 0;
		if (replace)
			flags = PendingIntent.FLAG_CANCEL_CURRENT;
		PendingIntent contentIntent = PendingIntent.getActivity(context, id, notificationIntent, flags);

		Settings settings = new Settings(context);
		boolean vibration = settings.getVibration();
        boolean soundsEnabled = settings.getSoundsEnabled();
		int defaults = 0;
		if (vibration && soundsEnabled && showLed)
			defaults = Notification.DEFAULT_ALL;
		else {
            defaults = (soundsEnabled) ? Notification.DEFAULT_SOUND : 0;
            defaults |= (vibration) ? Notification.DEFAULT_VIBRATE : 0;
            defaults |= (showLed) ? Notification.DEFAULT_LIGHTS : 0;
		}

		Notification.Builder notificationBuilder = new Notification.Builder(context)
			.setDefaults(defaults)
			.setContentTitle(title)
			.setTicker(tickerText)
			.setContentText(text)
			.setContentIntent(contentIntent)
			.setSmallIcon(icon, 0)
			.setAutoCancel(true);

		if (!vibration)
			notificationBuilder.setVibrate(new long[] { 0, 0 });

		Notification notification = null;
		if (contentUri != null) {
			try {
				Notification.BigPictureStyle picBuilder = new Notification.BigPictureStyle(notificationBuilder)
					.bigPicture(MediaStore.Images.Media.getBitmap(context.getContentResolver(), contentUri));
				notification = picBuilder.build();
			} catch (Exception e) {
				Log.e(LOG_TAG, "Unable to decode incoming image for big notification: " + e);
			}
		}

		if (notification == null)
			notification = notificationBuilder.build();

		final NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(id, notification);
		Log.w(LOG_TAG, title + ":" + text);
	}
}
