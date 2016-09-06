package de.robv.android.xposed.installer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import java.util.Map;

import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.util.PrefixedSharedPreferences;
import de.robv.android.xposed.installer.util.RepoLoader;

public class DownloadDetailsSettingsFragment extends PreferenceFragment {
    private DownloadDetailsActivity mActivity;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = (DownloadDetailsActivity) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Module module = mActivity.getModule();
        if (module == null)
            return;

        final String packageName = module.packageName;

        PreferenceManager prefManager = getPreferenceManager();
        prefManager.setSharedPreferencesName("module_settings");
        PrefixedSharedPreferences.injectToPreferenceManager(prefManager, module.packageName);
        addPreferencesFromResource(R.xml.module_prefs);

        SharedPreferences prefs = getActivity().getSharedPreferences("module_settings", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (prefs.getBoolean("no_global", true)) {
            for (Map.Entry<String, ?> k : prefs.getAll().entrySet()) {
                if (prefs.getString(k.getKey(), "").equals("global")) {
                    editor.putString(k.getKey(), "").apply();
                }
            }

            editor.putBoolean("no_global", false).apply();
        }

        findPreference("release_type").setOnPreferenceChangeListener(
                new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference,
                                                      Object newValue) {
                        RepoLoader.getInstance().setReleaseTypeLocal(packageName, (String) newValue);
                        return true;
                    }
                });
    }
}
