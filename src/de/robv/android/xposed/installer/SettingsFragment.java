package de.robv.android.xposed.installer;

import android.app.Activity;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import de.robv.android.xposed.installer.util.RepoLoader;

public class SettingsFragment extends PreferenceFragment {

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();
		if (activity instanceof XposedInstallerActivity)
			((XposedInstallerActivity) activity).setNavItem(XposedInstallerActivity.TAB_SETTINGS, null);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.prefs);

		findPreference("enable_downloads").setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean enabled = (Boolean) newValue;
				if (enabled) {
					preference.getEditor().putBoolean("enable_downloads", enabled).apply();
					RepoLoader.getInstance().triggerReload(true);
				} else {
					RepoLoader.getInstance().clear();
				}
				return true;
			}
		});
	}
}
