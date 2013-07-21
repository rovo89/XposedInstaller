package de.robv.android.xposed.installer;

import android.Manifest;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.RepoLoader;

public class XposedApp extends Application {
	public void onCreate() {
		super.onCreate();

		RepoLoader.init(this);
		ModuleUtil.init(this);
	}

	public boolean enableDownloads() {
		if (checkCallingOrSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED)
			return false;

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		return prefs.getBoolean("enable_downloads", true);
	}
}
