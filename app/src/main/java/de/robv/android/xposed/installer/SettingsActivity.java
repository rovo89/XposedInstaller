package de.robv.android.xposed.installer;

import static de.robv.android.xposed.installer.XposedApp.darkenColor;
import static de.robv.android.xposed.installer.XposedApp.getColor;

import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.ThemeUtil;
import de.robv.android.xposed.installer.util.UIUtil;

public class SettingsActivity extends XposedBaseActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ThemeUtil.setTheme(this);
		setContentView(R.layout.activity_container);

		Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(mToolbar);

		mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				finish();
			}
		});

		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setTitle(R.string.nav_item_settings);
			ab.setDisplayHomeAsUpEnabled(true);
		}

		if (savedInstanceState == null) {
			getFragmentManager().beginTransaction()
					.add(R.id.container, new SettingsFragment()).commit();
		}

	}

	public static class SettingsFragment extends PreferenceFragment
			implements Preference.OnPreferenceChangeListener {
		private static final File mDisableResourcesFlag = new File(
				XposedApp.BASE_DIR + "conf/disable_resources");

		public SettingsFragment() {
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.prefs);

			Preference nav_bar = findPreference("nav_bar");
			if (Build.VERSION.SDK_INT < 21) {
				Preference heads_up = findPreference("heads_up");

				heads_up.setEnabled(false);
				nav_bar.setEnabled(false);
				heads_up.setSummary(heads_up.getSummary() + " LOLLIPOP+");
				nav_bar.setSummary("LOLLIPOP+");
			}

			findPreference("enable_downloads").setOnPreferenceChangeListener(
					new Preference.OnPreferenceChangeListener() {
						@Override
						public boolean onPreferenceChange(Preference preference,
								Object newValue) {
							boolean enabled = (Boolean) newValue;
							if (enabled) {
								preference.getEditor()
										.putBoolean("enable_downloads", true)
										.apply();
								RepoLoader.getInstance().refreshRepositories();
								RepoLoader.getInstance().triggerReload(true);
							} else {
								RepoLoader.getInstance().clear(true);
							}
							return true;
						}
					});

			findPreference("release_type_global").setOnPreferenceChangeListener(
					new Preference.OnPreferenceChangeListener() {
						@Override
						public boolean onPreferenceChange(Preference preference,
								Object newValue) {
							RepoLoader.getInstance()
									.setReleaseTypeGlobal((String) newValue);
							return true;
						}
					});

			CheckBoxPreference prefDisableResources = (CheckBoxPreference) findPreference(
					"disable_resources");
			prefDisableResources.setChecked(mDisableResourcesFlag.exists());
			prefDisableResources.setOnPreferenceChangeListener(
					new Preference.OnPreferenceChangeListener() {
						@Override
						public boolean onPreferenceChange(Preference preference,
								Object newValue) {
							boolean enabled = (Boolean) newValue;
							if (enabled) {
								try {
									mDisableResourcesFlag.createNewFile();
								} catch (IOException e) {
									Toast.makeText(getActivity(),
											e.getMessage(), Toast.LENGTH_SHORT)
											.show();
								}
							} else {
								mDisableResourcesFlag.delete();
							}
							return (enabled == mDisableResourcesFlag.exists());
						}
					});

			Preference prefTheme = findPreference("theme");
			Preference colorPref = findPreference("colors");

			prefTheme.setOnPreferenceChangeListener(this);
			colorPref.setOnPreferenceChangeListener(this);
			nav_bar.setOnPreferenceChangeListener(this);
		}

		@Override
		public void onResume() {
			super.onResume();

			if (UIUtil.isLollipop())
				getActivity().getWindow().setStatusBarColor(
						darkenColor(getColor(getActivity()), 0.85f));
		}

		@Override
		public boolean onPreferenceChange(Preference preference,
				Object newValue) {
			getActivity().recreate();
			return true;
		}
	}
}