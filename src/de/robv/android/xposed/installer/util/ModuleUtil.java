package de.robv.android.xposed.installer.util;

import java.util.HashMap;
import java.util.Map;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.ModuleVersion;


public final class ModuleUtil {
	private static ModuleUtil mInstance = null;
	private final XposedApp mApp;
	private final PackageManager mPm;
	private final String mFrameworkPackage;
	private Map<String, InstalledModule> mInstalledModules;
	private boolean mIsReloading = false;

	private ModuleUtil() {
		mApp = XposedApp.getInstance();
		mPm = mApp.getPackageManager();
		mFrameworkPackage = mApp.getPackageName();
	}

	public static synchronized ModuleUtil getInstance() {
		if (mInstance == null) {
			mInstance = new ModuleUtil();
			mInstance.reloadInstalledModules();
		}
		return mInstance;
	}

	public void reloadInstalledModules() {
		synchronized (this) {
			if (mIsReloading)
				return;
			mIsReloading = true;
		}
		mApp.updateProgressIndicator();

		Map<String, InstalledModule> modules = new HashMap<String, ModuleUtil.InstalledModule>();

		for (PackageInfo pkg : mPm.getInstalledPackages(PackageManager.GET_META_DATA)) {
			ApplicationInfo app = pkg.applicationInfo;

			if (app.metaData != null && app.metaData.containsKey("xposedmodule"))
				modules.put(pkg.packageName, new InstalledModule(pkg, false));
			else if (isFramework(pkg.packageName))
				modules.put(pkg.packageName, new InstalledModule(pkg, true));

		}

		mInstalledModules = modules;
		synchronized (this) {
			mIsReloading = false;
		}
		mApp.updateProgressIndicator();
	}

	public InstalledModule reloadSingleModule(String packageName) {
		PackageInfo pkg;
		try {
			pkg = mPm.getPackageInfo(packageName, PackageManager.GET_META_DATA);
		} catch (NameNotFoundException e) {
			mInstalledModules.remove(packageName);
			return null;
		}

		ApplicationInfo app = pkg.applicationInfo;
		if (app.metaData != null && app.metaData.containsKey("xposedmodule")) {
			InstalledModule module = new InstalledModule(pkg, false);
			mInstalledModules.put(packageName, module);
			return module;
		} else {
			mInstalledModules.remove(packageName);
			return null;
		}
	}

	public synchronized boolean isLoading() {
		return mIsReloading;
	}

	public InstalledModule getFramework() {
		return getModule(mFrameworkPackage);
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
		for (ModuleVersion version : module.versions) {
			if (version.downloadLink != null)
				return version;
		}
		return null;
	}



	public class InstalledModule {
		public ApplicationInfo app;
		public final String packageName;
		public final boolean isFramework;
		public final String versionName;
		public final int versionCode;
		public final String minVersion;
		public final String description;

		private String appName; // loaded lazyily

		private InstalledModule(PackageInfo pkg, boolean isFramework) {
			this.app = pkg.applicationInfo;
			this.packageName = pkg.packageName;
			this.isFramework = isFramework;
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
