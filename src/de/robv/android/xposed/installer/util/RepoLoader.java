package de.robv.android.xposed.installer.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import de.robv.android.xposed.installer.repo.Repository;
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

	private static final String DEFAULT_REPOSITORIES = "http://dl.xposed.info/repo/full.xml.gz";
	private Map<Long,Repository> mRepositories = null;

	private ReleaseType mGlobalReleaseType = ReleaseType.STABLE;
	private final Map<String, ReleaseType> mLocalReleaseTypes = new HashMap<String, ReleaseType>();

	private RepoLoader() {
		mInstance = this;
		mApp = XposedApp.getInstance();
		mPref = mApp.getSharedPreferences("repo", Context.MODE_PRIVATE);
		mModulePref = mApp.getSharedPreferences("module_settings", Context.MODE_PRIVATE);
		mConMgr = (ConnectivityManager) mApp.getSystemService(Context.CONNECTIVITY_SERVICE);
		RepoDb.init(mApp);

		refreshRepositories();
		setReleaseTypeGlobal(XposedApp.getPreferences().getString("release_type_global", "stable"));
	}

	public static synchronized RepoLoader getInstance() {
		if (mInstance == null)
			new RepoLoader();
		return mInstance;
	}

	private boolean refreshRepositories() {
		mRepositories = RepoDb.getRepositories();

		// Unlikely case (usually only during initial load): DB state doesn't fit to configuration
		boolean needReload = false;
		String[] config = mPref.getString("repositories", DEFAULT_REPOSITORIES).split("\\|");
		if (mRepositories.size() != config.length) {
			needReload = true;
		} else {
			int i = 0;
			for (Repository repo : mRepositories.values()) {
				if (!repo.url.equals(config[i++])) {
					needReload = true;
					break;
				}
			}
		}

		if (!needReload)
			return false;

		clear(false);
		for (String url : config) {
			RepoDb.insertRepository(url);
		}
		mRepositories = RepoDb.getRepositories();
		return true;
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

	public Repository getRepository(long repoId) {
		return mRepositories.get(repoId);
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

				downloadAndParseFiles();

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

	public void clear(boolean notify) {
		synchronized (this) {
			// TODO Stop reloading repository when it should be cleared
			if (mIsLoading)
				return;

			RepoDb.deleteRepositories();
			DownloadsUtil.clearCache(null);
			resetLastUpdateCheck();
		}

		if (notify)
			notifyListeners();
	}

	public void setRepositories(String... repos) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < repos.length; i++) {
			if (i > 0)
				sb.append("|");
			sb.append(repos[i]);
		}
		mPref.edit().putString("repositories", sb.toString()).commit();
		if (refreshRepositories())
			triggerReload(true);
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

	private void downloadAndParseFiles() {
		long lastUpdateCheck = mPref.getLong("last_update_check", 0);
		int UPDATE_FREQUENCY = 24 * 60 * 60 * 1000; // TODO make this configurable
		if (System.currentTimeMillis() < lastUpdateCheck + UPDATE_FREQUENCY)
			return;

		NetworkInfo netInfo = mConMgr.getActiveNetworkInfo();
		if (netInfo == null || !netInfo.isConnected())
			return;

		for (Entry<Long, Repository> repoEntry : mRepositories.entrySet()) {
			final long repoId = repoEntry.getKey();
			final Repository repo = repoEntry.getValue();

			String url = (repo.partialUrl != null && repo.version != null)
					? String.format(repo.partialUrl, repo.version) : repo.url;

			File cacheFile = getRepoCacheFile(url);
			SyncDownloadInfo info = DownloadsUtil.downloadSynchronously(url, cacheFile);

			if (info.status != SyncDownloadInfo.STATUS_SUCCESS) {
				if (info.errorMessage != null)
					mMessages.add(info.errorMessage);
				continue;
			}

			InputStream in = null;
			try {
				in = new FileInputStream(cacheFile);
				if (url.endsWith(".gz"))
					in = new GZIPInputStream(in);

				RepoParser.parse(in, new RepoParserCallback() {
					@Override
					public void onRepositoryMetadata(Repository repository) {
						if (!repository.isPartial)
							RepoDb.deleteAllModules(repoId);
					}

					@Override
					public void onNewModule(Module module) {
						RepoDb.insertModule(repoId, module);
					}

					@Override
					public void onRemoveModule(String packageName) {
						RepoDb.deleteModule(repoId, packageName);
					}

					@Override
					public void onCompleted(Repository repository) {
						if (!repository.isPartial) {
							RepoDb.updateRepository(repoId, repository);
							repo.name = repository.name;
							repo.partialUrl = repository.partialUrl;
							repo.version = repository.version;
						} else {
							RepoDb.updateRepositoryVersion(repoId, repository.version);
							repo.version = repository.version;
						}
					}
				});

			} catch (Throwable t) {
				mMessages.add(mApp.getString(R.string.repo_load_failed, url, t.getMessage()));
				DownloadsUtil.clearCache(url);

			} finally {
				if (in != null)
					try { in.close(); } catch (IOException ignored) {}
				cacheFile.delete();
			}
		}

		mPref.edit().putLong("last_update_check", System.currentTimeMillis()).commit();

		// TODO Set ModuleColumns.PREFERRED for modules which appear in multiple repositories
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
