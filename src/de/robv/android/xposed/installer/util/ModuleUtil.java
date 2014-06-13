package de.robv.android.xposed.installer.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.FileUtils;
import android.util.Log;
import android.widget.Toast;
import de.robv.android.xposed.installer.InstallerFragment;
import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.repo.ModuleVersion;
import de.robv.android.xposed.installer.repo.RepoDb;


public final class ModuleUtil {
	private static ModuleUtil mInstance = null;
	private final XposedApp mApp;
	private SharedPreferences mPref;
	private final PackageManager mPm;
	private final String mFrameworkPackageName;
	private InstalledModule mFramework = null;
	private Map<String, InstalledModule> mInstalledModules;
	private boolean mIsReloading = false;
	private final List<ModuleListener> mListeners = new CopyOnWriteArrayList<ModuleListener>();

	public static int MIN_MODULE_VERSION = 2; // reject modules with xposedminversion below this
	private static final String MODULES_LIST_FILE = XposedApp.BASE_DIR + "conf/modules.list";

	private ModuleUtil() {
		mApp = XposedApp.getInstance();
		mPref = mApp.getSharedPreferences("enabled_modules", Context.MODE_PRIVATE);
		mPm = mApp.getPackageManager();
		mFrameworkPackageName = mApp.getPackageName();
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
		RepoDb.beginTransation();
		try {
			RepoDb.deleteAllInstalledModules();

			for (PackageInfo pkg : mPm.getInstalledPackages(PackageManager.GET_META_DATA)) {
				ApplicationInfo app = pkg.applicationInfo;
				if (!app.enabled)
					continue;

				InstalledModule installed = null;
				if (app.metaData != null && app.metaData.containsKey("xposedmodule")) {
					installed = new InstalledModule(pkg, false);
					modules.put(pkg.packageName, installed);
				} else if (isFramework(pkg.packageName)) {
					mFramework = installed = new InstalledModule(pkg, true);
				}

				if (installed != null)
					RepoDb.insertInstalledModule(installed);
			}

			RepoDb.setTransactionSuccessful();
		} finally {
			RepoDb.endTransation();
		}

		mInstalledModules = modules;
		synchronized (this) {
			mIsReloading = false;
		}
		mApp.updateProgressIndicator();
		for (ModuleListener listener : mListeners) {
			listener.onInstalledModulesReloaded(mInstance);
		}
	}

	public InstalledModule reloadSingleModule(String packageName) {
		PackageInfo pkg;
		try {
			pkg = mPm.getPackageInfo(packageName, PackageManager.GET_META_DATA);
		} catch (NameNotFoundException e) {
			RepoDb.deleteInstalledModule(packageName);
			InstalledModule old = mInstalledModules.remove(packageName);
			if (old != null) {
				for (ModuleListener listener : mListeners) {
					listener.onSingleInstalledModuleReloaded(mInstance, packageName, null);
				}
			}
			return null;
		}

		ApplicationInfo app = pkg.applicationInfo;
		if (app.enabled && app.metaData != null && app.metaData.containsKey("xposedmodule")) {
			InstalledModule module = new InstalledModule(pkg, false);
			RepoDb.insertInstalledModule(module);
			mInstalledModules.put(packageName, module);
			for (ModuleListener listener : mListeners) {
				listener.onSingleInstalledModuleReloaded(mInstance, packageName, module);
			}
			return module;
		} else {
			RepoDb.deleteInstalledModule(packageName);
			InstalledModule old = mInstalledModules.remove(packageName);
			if (old != null) {
				for (ModuleListener listener : mListeners) {
					listener.onSingleInstalledModuleReloaded(mInstance, packageName, null);
				}
			}
			return null;
		}
	}

	public synchronized boolean isLoading() {
		return mIsReloading;
	}

	public InstalledModule getFramework() {
		return mFramework;
	}

	public String getFrameworkPackageName() {
		return mFrameworkPackageName;
	}

	public boolean isFramework(String packageName) {
		return mFrameworkPackageName.equals(packageName);
	}

	public boolean isInstalled(String packageName) {
		return mInstalledModules.containsKey(packageName) || isFramework(packageName);
	}

	public InstalledModule getModule(String packageName) {
		return mInstalledModules.get(packageName);
	}

	public Map<String, InstalledModule> getModules() {
		return mInstalledModules;
	}

	public void setModuleEnabled(String packageName, boolean enabled) {
		if (enabled)
			mPref.edit().putInt(packageName, 1).commit();
		else
			mPref.edit().remove(packageName).commit();
	}

	public boolean isModuleEnabled(String packageName) {
		return mPref.contains(packageName);
	}

	public List<InstalledModule> getEnabledModules() {
		LinkedList<InstalledModule> result = new LinkedList<InstalledModule>();

		for (String packageName : mPref.getAll().keySet()) {
			InstalledModule module = getModule(packageName);
			if (module != null)
				result.add(module);
			else
				setModuleEnabled(packageName, false);
		}

		return result;
	}

	public synchronized void updateModulesList(boolean showToast) {
		try {
			Log.i(XposedApp.TAG, "updating modules.list");
			int installedXposedVersion = InstallerFragment.getJarInstalledVersion();
			if (installedXposedVersion == 0) {
				Toast.makeText(mApp, "The xposed framework is not installed", Toast.LENGTH_SHORT).show();
				return;
			}

			PrintWriter modulesList = new PrintWriter(MODULES_LIST_FILE);
			List<InstalledModule> enabledModules = getEnabledModules();
			for (InstalledModule module : enabledModules) {
				if (module.minVersion > installedXposedVersion || module.minVersion < MIN_MODULE_VERSION)
					continue;

				modulesList.println(module.app.sourceDir);
			}
			modulesList.close();

			FileUtils.setPermissions(MODULES_LIST_FILE, 00664, -1, -1);

			if (showToast)
				Toast.makeText(mApp, R.string.xposed_module_list_updated, Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			Log.e(XposedApp.TAG, "cannot write " + MODULES_LIST_FILE, e);
			Toast.makeText(mApp, "cannot write " +  MODULES_LIST_FILE, Toast.LENGTH_SHORT).show();
		}
	}

	public static int extractIntPart(String str) {
		int result = 0, length = str.length();
		for (int offset = 0; offset < length; offset++) {
			char c = str.charAt(offset);
			if ('0' <= c && c <= '9')
				result = result * 10 + (c - '0');
			else
				break;
		}
		return result;
	}



	public class InstalledModule {
		public ApplicationInfo app;
		public final String packageName;
		public final boolean isFramework;
		public final String versionName;
		public final int versionCode;
		public final int minVersion;

		private String appName; // loaded lazyily
		private String description; // loaded lazyily

		private Drawable.ConstantState iconCache = null;

		private InstalledModule(PackageInfo pkg, boolean isFramework) {
			this.app = pkg.applicationInfo;
			this.packageName = pkg.packageName;
			this.isFramework = isFramework;
			this.versionName = pkg.versionName;
			this.versionCode = pkg.versionCode;

			if (isFramework) {
				this.minVersion = 0;
				this.description = "";
			} else {
				Object minVersionRaw = app.metaData.get("xposedminversion");
				if (minVersionRaw instanceof Integer) {
					this.minVersion = (Integer) minVersionRaw;
				} else if (minVersionRaw instanceof String) {
					this.minVersion = extractIntPart((String) minVersionRaw);
				} else {
					this.minVersion = 0;
				}
			}
		}

		public String getAppName() {
			if (appName == null)
				appName = app.loadLabel(mPm).toString();
			return appName;
		}

		public String getDescription() {
			if (this.description == null) {
				Object descriptionRaw = app.metaData.get("xposeddescription");
				String descriptionTmp = null;
				if (descriptionRaw instanceof String) {
					descriptionTmp = ((String) descriptionRaw).trim();
				} else if (descriptionRaw instanceof Integer) {
					try {
						int resId = (Integer) descriptionRaw;
						if (resId != 0)
							descriptionTmp = mPm.getResourcesForApplication(app).getString(resId).trim();
					} catch (Exception ignored) {}
				}
				this.description = (descriptionTmp != null) ? descriptionTmp : "";
			}
			return this.description;
		}

		public boolean isUpdate(ModuleVersion version) {
			return (version != null) ? version.code > versionCode : false;
		}

		public Drawable getIcon() {
			if (iconCache != null)
				return iconCache.newDrawable();

			Drawable result = app.loadIcon(mPm);
			iconCache = result.getConstantState();
			return result;
		}

		@Override
		public String toString() {
			return getAppName();
		}
	}



	public void addListener(ModuleListener listener) {
		if (!mListeners.contains(listener))
			mListeners.add(listener);
	}

	public void removeListener(ModuleListener listener) {
		mListeners.remove(listener);
	}

	public interface ModuleListener {
		/**
		 * Called whenever one (previously or now) installed module has been reloaded
		 */
		public void onSingleInstalledModuleReloaded(ModuleUtil moduleUtil, String packageName, InstalledModule module);

		/**
		 * Called whenever all installed modules have been reloaded
		 */
		public void onInstalledModulesReloaded(ModuleUtil moduleUtil);
	}
}
