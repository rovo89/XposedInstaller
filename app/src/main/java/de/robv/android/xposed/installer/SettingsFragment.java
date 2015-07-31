package de.robv.android.xposed.installer;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.widget.Toast;
import de.robv.android.xposed.installer.util.RepoLoader;

public class SettingsFragment extends PreferenceFragment {
	private static final File mDisableResourcesFlag = new File(XposedApp.BASE_DIR + "conf/disable_resources");

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();
		if (activity instanceof XposedDropdownNavActivity)
			((XposedDropdownNavActivity) activity).setNavItem(XposedDropdownNavActivity.TAB_SETTINGS);
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
					RepoLoader.getInstance().refreshRepositories();
					RepoLoader.getInstance().triggerReload(true);
				} else {
					RepoLoader.getInstance().clear(true);
				}
				return true;
			}
		});

		findPreference("release_type_global").setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				RepoLoader.getInstance().setReleaseTypeGlobal((String) newValue);
				return true;
			}
		});

		CheckBoxPreference prefDisableResources = (CheckBoxPreference) findPreference("disable_resources");
		prefDisableResources.setChecked(mDisableResourcesFlag.exists());
		prefDisableResources.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean enabled = (Boolean) newValue;
				if (enabled) {
					try {
						mDisableResourcesFlag.createNewFile();
					} catch (IOException e) {
						Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
					}
				} else {
					mDisableResourcesFlag.delete();
				}
				return (enabled == mDisableResourcesFlag.exists());
			}
		});

		Preference prefTheme = findPreference("theme");
		prefTheme.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				getActivity().recreate();
				return true;
			}
		});
	}
}
