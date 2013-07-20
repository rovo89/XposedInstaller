package de.robv.android.xposed.installer;

import android.Manifest;
import android.app.Application;
import android.content.pm.PackageManager;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.RepoLoader;

public class XposedApp extends Application {
	public static boolean SUPPORTS_INTERNET = true;

	public void onCreate() {
		super.onCreate();

		SUPPORTS_INTERNET = (checkCallingOrSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED);

		RepoLoader.init(this);
		ModuleUtil.init(this);
	}
}
