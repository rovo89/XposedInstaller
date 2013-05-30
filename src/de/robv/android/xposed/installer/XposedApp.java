package de.robv.android.xposed.installer;

import android.app.Application;
import de.robv.android.xposed.installer.util.RepoLoader;

public class XposedApp extends Application {
	public void onCreate() {
		super.onCreate();
		RepoLoader.init(this);
	}
}
