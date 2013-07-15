package de.robv.android.xposed.installer.util;

import java.util.HashMap;
import java.util.Map;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.ModuleVersion;


public final class ModuleUtil {
	private static ModuleUtil mInstance = null;
	private final Application mApp;
	private final PackageManager mPm;
	private final String mFrameworkPackage;
	private Map<String, InstalledModule> mInstalledModules;
	private boolean mIsReloading = false;

	private ModuleUtil(Application app) {
		mApp = app;
		mPm = mApp.getPackageManager();
		mFrameworkPackage = mApp.getPackageName();
		reloadInstalledModules();
	}

	/** call this only once (from the Application) */
	public static void init(Application app) {
		if (mInstance != null)
			throw new IllegalStateException("this class must only be initialized once");

		mInstance = new ModuleUtil(app);
	}

	public static ModuleUtil getInstance() {
		return mInstance;
	}

	public void reloadInstalledModules() {
		synchronized (this) {
			if (mIsReloading)
				return;
			mIsReloading = true;
		}

		Map<String, InstalledModule> modules = new HashMap<String, ModuleUtil.InstalledModule>();

		for (PackageInfo pkg : mPm.getInstalledPackages(PackageManager.GET_META_DATA)) {
			ApplicationInfo app = pkg.applicationInfo;

			if (app.metaData != null && app.metaData.containsKey("xposedmodule"))
				modules.put(pkg.packageName, new InstalledModule(pkg, false));
			else
				modules.put(pkg.packageName, new InstalledModule(pkg, true));

		}

		mInstalledModules = modules;
		synchronized (this) {
			mIsReloading = false;
		}
	}

	public boolean isFramework(String packageName) {
		return mFrameworkPackage.equals(packageName);
	}

	public boolean isInstalled(String packageName) {
		return mInstalledModules.containsKey(packageName);
	}

	public InstalledModule getModule(String packageName) {
		return mInstalledModules.get(packageName);
	}

	public Map<String, InstalledModule> getModules() {
		return mInstalledModules;
	}

	public ModuleVersion getLatestVersion(Module module) {
		if (module == null || module.versions.isEmpty())
			return null;

		// TODO implement logic for branches
		return module.versions.get(0);
	}



	public class InstalledModule {
		public ApplicationInfo app;
		public final String packageName;
		public final String versionName;
		public final int versionCode;
		public final String minVersion;
		public final String description;

		private String appName; // loaded lazyily

		private InstalledModule(PackageInfo pkg, boolean isFramework) {
			this.app = pkg.applicationInfo;
			this.packageName = pkg.packageName;
			this.versionName = pkg.versionName;
			this.versionCode = pkg.versionCode;

			if (isFramework) {
				this.minVersion = "";
				this.description = "";
			} else {
				this.minVersion = app.metaData.getString("xposedminversion");
				Object descriptionRaw = app.metaData.get("xposeddescription");
				String description = null;
				if (descriptionRaw instanceof String) {
					description = ((String) descriptionRaw).trim();
				} else if (descriptionRaw instanceof Integer) {
					try {
						int resId = (Integer) descriptionRaw;
						if (resId != 0)
							description = mPm.getResourcesForApplication(app).getString(resId).trim();
					} catch (Exception ignored) {}
				}
				this.description = (description != null) ? description : "";
			}
		}

		public String getAppName() {
			if (appName == null)
				appName = app.loadLabel(mPm).toString();
			return appName;
		}

		public boolean isUpdate(ModuleVersion version) {
			return (version != null) ? version.code > versionCode : false;
		}

		public boolean isUpdate(Module module) {
			return isUpdate(getLatestVersion(module));
		}

		public Drawable getIcon() {
			return app.loadIcon(mPm);
		}

		@Override
		public String toString() {
			return String.format("%s [%s]", getAppName(), versionName);
		}
	}
}
