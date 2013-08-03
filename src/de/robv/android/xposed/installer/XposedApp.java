package de.robv.android.xposed.installer;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.RepoLoader;

public class XposedApp extends Application implements ActivityLifecycleCallbacks {
	private boolean mIsUiLoaded = false;

	public void onCreate() {
		super.onCreate();

		// some stuff is only needed if UI is needed, not for receivers etc.
		registerActivityLifecycleCallbacks(this);

		RepoLoader.init(this);
		ModuleUtil.init(this);
	}

	public boolean enableDownloads() {
		if (checkCallingOrSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED)
			return false;

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		return prefs.getBoolean("enable_downloads", true);
	}


	@Override
	public synchronized void onActivityCreated(Activity activity, Bundle savedInstanceState) {
		if (mIsUiLoaded)
			return;

		RepoLoader.getInstance().triggerFirstLoadIfNecessary();
		mIsUiLoaded = true;
	}

	@Override public void onActivityStarted(Activity activity) {}
	@Override public void onActivityResumed(Activity activity) {}
	@Override public void onActivityPaused(Activity activity) {}
	@Override public void onActivityStopped(Activity activity) {}
	@Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
	@Override public void onActivityDestroyed(Activity activity) {}
}
