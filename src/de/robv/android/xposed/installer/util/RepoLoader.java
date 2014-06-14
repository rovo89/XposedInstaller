package de.robv.android.xposed.installer.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.widget.Toast;
import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.ModuleVersion;
import de.robv.android.xposed.installer.repo.ReleaseType;
import de.robv.android.xposed.installer.repo.RepoDb;
import de.robv.android.xposed.installer.repo.RepoParser;
import de.robv.android.xposed.installer.repo.RepoParser.RepoParserCallback;
import de.robv.android.xposed.installer.util.DownloadsUtil.SyncDownloadInfo;

public class RepoLoader {
	private static RepoLoader mInstance = null;
	private XposedApp mApp = null;
	private SharedPreferences mPref;
	private SharedPreferences mModulePref;
	private ConnectivityManager mConMgr;

	private boolean mIsLoading = false;
	private boolean mReloadTriggeredOnce = false;
	private final List<String> mMessages = new LinkedList<String>();
	private final List<RepoListener> mListeners = new CopyOnWriteArrayList<RepoListener>();

	private ReleaseType mGlobalReleaseType = ReleaseType.STABLE;
	private final Map<String, ReleaseType> mLocalReleaseTypes = new HashMap<String, ReleaseType>();

	private RepoLoader() {
		mInstance = this;
		mApp = XposedApp.getInstance();
		mPref = mApp.getSharedPreferences("repo", Context.MODE_PRIVATE);
		mModulePref = mApp.getSharedPreferences("module_settings", Context.MODE_PRIVATE);
		mConMgr = (ConnectivityManager) mApp.getSystemService(Context.CONNECTIVITY_SERVICE);
		RepoDb.init(mApp);

		setReleaseTypeGlobal(XposedApp.getPreferences().getString("release_type_global", "stable"));
	}

	public static synchronized RepoLoader getInstance() {
		if (mInstance == null)
			new RepoLoader();
		return mInstance;
	}

	public void setReleaseTypeGlobal(String relTypeString) {
		ReleaseType relType = ReleaseType.fromString(relTypeString);
		if (mGlobalReleaseType != relType) {
			mGlobalReleaseType = relType;
			// TODO Update latest version in DB for all modules
			notifyListeners();
		}
	}

	public void setReleaseTypeLocal(String packageName, String relTypeString) {
		boolean notify = false;
		synchronized (mLocalReleaseTypes) {
			if (!mLocalReleaseTypes.containsKey(packageName))
				return;

			ReleaseType relType = (!TextUtils.isEmpty(relTypeString)) ? ReleaseType.fromString(relTypeString) : null;
			if (mLocalReleaseTypes.get(packageName) != relType) {
				mLocalReleaseTypes.put(packageName, relType);
				notify = true;
			}
		}

		if (notify)
			// TODO Update latest version in DB for this module
			notifyListeners();
	}

	private ReleaseType getReleaseTypeLocal(String packageName) {
		synchronized (mLocalReleaseTypes) {
			if (mLocalReleaseTypes.containsKey(packageName))
				return mLocalReleaseTypes.get(packageName);

			String value = mModulePref.getString(packageName + "_release_type", null);
			ReleaseType result = (!TextUtils.isEmpty(value)) ? ReleaseType.fromString(value) : null;
			mLocalReleaseTypes.put(packageName, result);
			return result;
		}
	}

	public Module getModule(String packageName) {
		return RepoDb.getModuleByPackageName(packageName);
	}

	public ModuleVersion getLatestVersion(Module module) {
		if (module == null || module.versions.isEmpty())
			return null;

		for (ModuleVersion version : module.versions) {
			if (version.downloadLink != null && isVersionShown(version))
				return version;
		}
		return null;
	}

	public boolean isVersionShown(ModuleVersion version) {
		ReleaseType localSetting = getReleaseTypeLocal(version.module.packageName);
		if (localSetting != null)
			return version.relType.ordinal() <= localSetting.ordinal();
		else
			return version.relType.ordinal() <= mGlobalReleaseType.ordinal();
	}

	public void triggerReload(final boolean force) {
		mReloadTriggeredOnce = true;

		if (force)
			resetLastUpdateCheck();

		if (!mApp.areDownloadsEnabled())
			return;

		synchronized (this) {
			if (mIsLoading)
				return;
			mIsLoading = true;
		}
		mApp.updateProgressIndicator();

		new Thread("RepositoryReload") {
			public void run() {
				mMessages.clear();

				downloadFiles();

				for (final String message : mMessages) {
					XposedApp.runOnUiThread(new Runnable() {
						public void run() {
							Toast.makeText(mApp, message, Toast.LENGTH_LONG).show();
						}
					});
				}

				notifyListeners();

				synchronized (this) {
					mIsLoading = false;
				}
				mApp.updateProgressIndicator();
			}
		}.start();
	}

	public void triggerFirstLoadIfNecessary() {
		if (!mReloadTriggeredOnce)
			triggerReload(false);
	}

	public void resetLastUpdateCheck() {
		mPref.edit().remove("last_update_check").commit();
	}

	public synchronized boolean isLoading() {
		return mIsLoading;
	}

	public void clear() {
		synchronized (this) {
			if (mIsLoading)
				return;

			// TODO Should we clear the DB here?
		}
		notifyListeners();
	}

	public String[] getRepositories() {
		return mPref.getString("repositories", "http://dl.xposed.info/repo.xml.gz").split("\\|");
	}

	public void setRepositories(String... repos) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < repos.length; i++) {
			if (i > 0)
				sb.append("|");
			sb.append(repos[i]);
		}
		mPref.edit().putString("repositories", sb.toString()).commit();
	}

	public boolean hasModuleUpdates() {
		if (!mApp.areDownloadsEnabled())
			return false;

		return RepoDb.hasModuleUpdates();
	}

	public String getFrameworkUpdateVersion() {
		if (!mApp.areDownloadsEnabled())
			return null;

		return RepoDb.getFrameworkUpdateVersion();
	}

	private File getRepoCacheFile(String repo) {
		String filename = "repo_" + HashUtil.md5(repo) + ".xml";
		if (repo.endsWith(".gz"))
			filename += ".gz";
		return new File(mApp.getCacheDir(), filename);
	}

	private void downloadFiles() {
		long lastUpdateCheck = mPref.getLong("last_update_check", 0);
		int UPDATE_FREQUENCY = 24 * 60 * 60 * 1000; // TODO make this configurable
		if (System.currentTimeMillis() < lastUpdateCheck + UPDATE_FREQUENCY)
			return;

		NetworkInfo netInfo = mConMgr.getActiveNetworkInfo();
		if (netInfo == null || !netInfo.isConnected())
			return;

		String[] repos = getRepositories();
		for (String repo : repos) {
			File cacheFile = getRepoCacheFile(repo);
			SyncDownloadInfo info = DownloadsUtil.downloadSynchronously(repo, cacheFile);

			if (info.status != SyncDownloadInfo.STATUS_SUCCESS) {
				if (info.errorMessage != null)
					mMessages.add(info.errorMessage);
				continue;
			}

			InputStream in = null;
			try {
				in = new FileInputStream(cacheFile);
				if (repo.endsWith(".gz"))
					in = new GZIPInputStream(in);

				final long repoId = RepoDb.getOrInsertRepository(repo);
				RepoParser parser = new RepoParser(in);
				parser.parse(new RepoParserCallback() {
					@Override
					public void newModule(Module module) {
						RepoDb.insertModule(repoId, module);
					}
				});

			} catch (Throwable t) {
				mMessages.add(mApp.getString(R.string.repo_load_failed, repo, t.getMessage()));
				DownloadsUtil.clearCache(repo);

			} finally {
				if (in != null)
					try { in.close(); } catch (IOException ignored) {}
				cacheFile.delete();
			}
		}

		mPref.edit().putLong("last_update_check", System.currentTimeMillis()).commit();

		// TODO Set ModuleColumns.PREFERRED for modules which appear in multiple repositories
		// TODO Remove outdated repositories
	}


	public void addListener(RepoListener listener, boolean triggerImmediately) {
		if (!mListeners.contains(listener))
			mListeners.add(listener);

		if (triggerImmediately)
			listener.onRepoReloaded(this);
	}

	public void removeListener(RepoListener listener) {
		mListeners.remove(listener);
	}

	private void notifyListeners() {
		for (RepoListener listener : mListeners) {
			listener.onRepoReloaded(mInstance);
		}
	}

	public interface RepoListener {
		/**
		 * Called whenever the list of modules from repositories has been successfully reloaded
		 */
		public void onRepoReloaded(RepoLoader loader);
	}
}
