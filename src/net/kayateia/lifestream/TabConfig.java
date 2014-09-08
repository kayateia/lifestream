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

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;

public class TabConfig
	extends Fragment
	implements OnCheckedChangeListener
{
	// private final static String LOG_TAG = "LifeStream/PhotoCaptureActivity";

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);

		_view = inflater.inflate(R.layout.configuration, container, false);

		// Load our prefs, if they're there.
		_settings = new Settings(getActivity());

		// Set the UI to our config UI.
		setFromConfig();

		Button b = (Button)_view.findViewById(R.id.btnLogin);
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) { onLogin(); }
		});
		b = (Button)_view.findViewById(R.id.btnPathEditor);
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) { onPathEditor(); }
		});

		int[] checkboxes = new int[] {
			R.id.cbEnabled,
			R.id.cbUploadNotifications,
			R.id.cbVerboseNotifications,
			R.id.cbVibration,
			R.id.cbHighQualityImage
		};
		for (int id : checkboxes) {
			CheckBox cb = (CheckBox)_view.findViewById(id);
			cb.setOnCheckedChangeListener(this);
		}
		EditText tbImageSize = (EditText)_view.findViewById(R.id.maxImageSize);
		tbImageSize.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) { }
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				onCheckedChanged(null, false);
			}
		});

		return _view;
	}

	void setFromConfig() {
		// If we have an auth token, set that we're already logged in.
		final String authToken = _settings.getAuthToken();
		final TextView lblLoggedIn = (TextView)_view.findViewById(R.id.loggedin);
		final View loginForm = _view.findViewById(R.id.loginForm);
		
		if (authToken != null && !authToken.isEmpty()) {
			lblLoggedIn.setText(R.string.config_loggedin);
			loginForm.setVisibility(View.GONE);
		} else {
			lblLoggedIn.setText("");
			loginForm.setVisibility(View.VISIBLE);
		}

		String username = _settings.getUserName();
		EditText tbUsername = (EditText)_view.findViewById(R.id.username);
		if (username != null)
			tbUsername.setText(username);

		String password = _settings.getPassword();
		EditText tbPassword = (EditText)_view.findViewById(R.id.password);
		if (password != null)
			tbPassword.setText(password);

		CheckBox cbEnabled = (CheckBox)_view.findViewById(R.id.cbEnabled);
		cbEnabled.setChecked(_settings.getEnabled());

		CheckBox cbUploads = (CheckBox)_view.findViewById(R.id.cbUploadNotifications);
		cbUploads.setChecked(_settings.getUploadNotifications());

		CheckBox cbVerbose = (CheckBox)_view.findViewById(R.id.cbVerboseNotifications);
		cbVerbose.setChecked(_settings.getVerbose());

		CheckBox cbVibration = (CheckBox)_view.findViewById(R.id.cbVibration);
		cbVibration.setChecked(_settings.getVibration());

		CheckBox cbSound = (CheckBox)_view.findViewById(R.id.cbSounds);
		cbSound.setChecked(_settings.getSoundsEnabled());

		CheckBox cbHqImage = (CheckBox)_view.findViewById(R.id.cbHighQualityImage);
		cbHqImage.setChecked(_settings.getHighQualityScale());

		EditText tbImageSize = (EditText)_view.findViewById(R.id.maxImageSize);
		tbImageSize.setText("" + _settings.getImageSize());
	}

	void onLogin() {
		// Find our controls.
		final TextView loggedIn = (TextView)_view.findViewById(R.id.loggedin);
		final View loginForm = _view.findViewById(R.id.loginForm);
		
		EditText username = (EditText)_view.findViewById(R.id.username);
		EditText password = (EditText)_view.findViewById(R.id.password);

		final String userText = username.getText().toString();
		final String passText = password.getText().toString();

		final ProgressDialog progress = new ProgressDialog(getActivity(), ProgressDialog.STYLE_SPINNER);
		progress.setTitle("Logging into LifeStream");
		new AsyncTask<Boolean, String, String>() {
			@Override
			protected void onPreExecute() {
				progress.show();
			}

			@Override
			protected String doInBackground(Boolean... foo) {
				String result = Network.DoLogin(getActivity(), userText, passText); 
				if (result != null)
					return getResources().getString(R.string.config_loggedin_as, result);
				else
					return null;
			}

			@Override
			protected void onProgressUpdate(String... message) {
				progress.setTitle(message[0]);
			}

			@Override
			protected void onPostExecute(String message) {
				progress.dismiss();
				if (message != null) {
					loginForm.setVisibility(View.GONE);
					loggedIn.setText(message);
					CheckBox enabled = (CheckBox)_view.findViewById(R.id.cbEnabled);
					if (enabled.isChecked()) {
						UploadService.Kick(getActivity());
						StreamService.Kick(getActivity());
					}
				} else {
					loginForm.setVisibility(View.VISIBLE);
					loggedIn.setText(getResources().getString(R.string.config_badlogin));
				}
			}
		}.execute();
	}

	void onPathEditor() {
		Intent intent = new Intent(getActivity(), PathsActivity.class);
		startActivity(intent);
	}

	public void onCheckedChanged(CompoundButton b, boolean st) {
		CheckBox enabled = (CheckBox)_view.findViewById(R.id.cbEnabled);
		CheckBox uploads = (CheckBox)_view.findViewById(R.id.cbUploadNotifications);
		CheckBox verbose = (CheckBox)_view.findViewById(R.id.cbVerboseNotifications);
		CheckBox vibration = (CheckBox)_view.findViewById(R.id.cbVibration);
		CheckBox sounds = (CheckBox)_view.findViewById(R.id.cbSounds);
		CheckBox hq = (CheckBox)_view.findViewById(R.id.cbHighQualityImage);
		EditText imageSize = (EditText)_view.findViewById(R.id.maxImageSize);

		Settings settings = new Settings(getActivity());
		settings.setEnabled(enabled.isChecked());
		settings.setUploadNotifications(uploads.isChecked());
		settings.setVerbose(verbose.isChecked());
		settings.setVibration(vibration.isChecked());
		settings.setSoundsEnabled(sounds.isChecked());
		settings.setHighQualityScale(hq.isChecked());
		try {
			settings.setImageSize(Integer.parseInt(imageSize.getText().toString()));
		} catch (NumberFormatException e) {
			// Do nothing here... just leave it as it is until they fix the value.
		}
		settings.commit();

		if (b == enabled && enabled.isChecked()) {
			UploadService.Kick(getActivity());
			StreamService.Kick(getActivity());
		}
	}

	View _view;
	Settings _settings;
}
