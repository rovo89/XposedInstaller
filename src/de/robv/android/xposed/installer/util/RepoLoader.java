package de.robv.android.xposed.installer.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
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
import de.robv.android.xposed.installer.repo.ModuleGroup;
import de.robv.android.xposed.installer.repo.ModuleVersion;
import de.robv.android.xposed.installer.repo.ReleaseType;
import de.robv.android.xposed.installer.repo.RepoParser;
import de.robv.android.xposed.installer.repo.Repository;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;

public class RepoLoader {
	private static RepoLoader mInstance = null;
	private XposedApp mApp = null;
	private SharedPreferences mPref;
	private SharedPreferences mModulePref;
	private ConnectivityManager mConMgr;

	private Map<String, ModuleGroup> mModules = new HashMap<String, ModuleGroup>(0);
	private boolean mIsLoading = false;
	private boolean mReloadTriggeredOnce = false;
	private boolean mFirstLoadFinished = false;
	private Object mFirstLoadFinishedLock = new Object();
	private final List<String> mMessages = new LinkedList<String>();
	private final List<RepoListener> mListeners = new CopyOnWriteArrayList<RepoListener>();

	private ReleaseType mGlobalReleaseType = ReleaseType.STABLE;
	private final Map<String, ReleaseType> mLocalReleaseTypes = new HashMap<String, ReleaseType>();

	private RepoLoader() {
		mApp = XposedApp.getInstance();
		mPref = mApp.getSharedPreferences("repo", Context.MODE_PRIVATE);
		mModulePref = mApp.getSharedPreferences("module_settings", Context.MODE_PRIVATE);
		mConMgr = (ConnectivityManager) mApp.getSystemService(Context.CONNECTIVITY_SERVICE);

		setReleaseTypeGlobal(XposedApp.getPreferences().getString("release_type_global", "stable"));
	}

	public static synchronized RepoLoader getInstance() {
		if (mInstance == null)
			mInstance = new RepoLoader();
		return mInstance;
	}

	public void setReleaseTypeGlobal(String relTypeString) {
		ReleaseType relType = ReleaseType.fromString(relTypeString);
		if (mGlobalReleaseType != relType) {
			mGlobalReleaseType = relType;
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

	public Map<String, ModuleGroup> getModules() {
		return mModules;
	}

	public ModuleGroup getModuleGroup(String packageName) {
		return mModules.get(packageName);
	}

	public Module getModule(String packageName) {
		ModuleGroup group = mModules.get(packageName);
		if (group == null)
			return null;
		return group.getModule();
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

	public ModuleVersion getLatestVersion(String packageName) {
		Module module = getModule(packageName);
		return (module != null) ? getLatestVersion(module) : null;
	}

	public void triggerReload(final boolean force) {
		mReloadTriggeredOnce = true;

		if (force)
			resetLastUpdateCheck();

		if (!mApp.areDownloadsEnabled()) {
			notifyFirstLoadFinished();
			return;
		}

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
				parseFiles();

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
				notifyFirstLoadFinished();
				mApp.updateProgressIndicator();
			}
		}.start();
	}

	public void triggerFirstLoadIfNecessary() {
		if (!mReloadTriggeredOnce)
			triggerReload(false);
	}

	public RepoLoader waitForFirstLoadFinished() {
		synchronized (mFirstLoadFinishedLock) {
			while (!mFirstLoadFinished) {
				try {
					mFirstLoadFinishedLock.wait();
				} catch (InterruptedException ignored) {}
			}
		}
		return this;
	}

	private void notifyFirstLoadFinished() {
		synchronized (mFirstLoadFinishedLock) {
			mFirstLoadFinished = true;
			mFirstLoadFinishedLock.notifyAll();
		}
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

			mModules = new HashMap<String, ModuleGroup>();
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

		Map<String, InstalledModule> installedModules = ModuleUtil.getInstance().getModules();
		for (InstalledModule installed : installedModules.values()) {
			Module download = getModule(installed.packageName);
			if (download == null)
				continue;

			if (installed.isUpdate(getLatestVersion(download)))
				return true;
		}
		return false;
	}

	public String getFrameworkUpdateVersion() {
		if (!mApp.areDownloadsEnabled())
			return null;

		InstalledModule installed = ModuleUtil.getInstance().getFramework();
		if (installed == null) // would be strange if this happened...
			return null;

		Module download = getModule(installed.packageName);
		if (download == null)
			return null;

		ModuleVersion version = getLatestVersion(download);
		return installed.isUpdate(version) ? version.name : null;
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
			URLConnection connection = null;
			InputStream in = null;
			FileOutputStream out = null;
			try {
				File cacheFile = getRepoCacheFile(repo);

				connection = new URL(repo).openConnection();
				connection.setDoOutput(false);
				connection.setConnectTimeout(30000);
				connection.setReadTimeout(30000);

				if (connection instanceof HttpURLConnection) {
					// disable transparent gzip encoding for gzipped files
					if (repo.endsWith(".gz"))
						connection.addRequestProperty("Accept-Encoding", "identity");

					if (cacheFile.exists()) {
						String modified = mPref.getString("repo_" + repo + "_modified", null);
						String etag = mPref.getString("repo_" + repo + "_etag", null);

						if (modified != null)
							connection.addRequestProperty("If-Modified-Since", modified);
						if (etag != null)
							connection.addRequestProperty("If-None-Match", etag);
					}
				}

				connection.connect();

				if (connection instanceof HttpURLConnection) {
					HttpURLConnection httpConnection = (HttpURLConnection) connection;
					int responseCode = httpConnection.getResponseCode();
					if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
						continue;
					} else if (responseCode < 200 || responseCode >= 300) {
						mMessages.add(mApp.getString(R.string.repo_download_failed_http, repo, responseCode, httpConnection.getResponseMessage()));
						continue;
					}
				}

				in = connection.getInputStream();
				out = new FileOutputStream(cacheFile);
				byte buf[] = new byte[1024];
				int read;
				while ((read = in.read(buf)) != -1) {
					out.write(buf, 0, read);
				}

				if (connection instanceof HttpURLConnection) {
					HttpURLConnection httpConnection = (HttpURLConnection) connection;
					String modified = httpConnection.getHeaderField("Last-Modified");
					String etag = httpConnection.getHeaderField("ETag");

					mPref.edit()
						.putString("repo_" + repo + "_modified", modified)
						.putString("repo_" + repo + "_etag", etag)
						.commit();
				}

			} catch (Throwable t) {
				mMessages.add(mApp.getString(R.string.repo_download_failed, repo, t.getMessage()));

			} finally {
				if (connection != null && connection instanceof HttpURLConnection)
					((HttpURLConnection) connection).disconnect();
				if (in != null)
					try { in.close(); } catch (IOException ignored) {}
				if (out != null)
					try { out.close(); } catch (IOException ignored) {}
			}
		}

		mPref.edit().putLong("last_update_check", System.currentTimeMillis()).commit();
	}

	private void removeRepoFile(String repo) {
		getRepoCacheFile(repo).delete();

		mPref.edit()
			.remove("repo_" + repo + "_modified")
			.remove("repo_" + repo + "_etag")
			.commit();
	}

	private void parseFiles() {
		Map<String, ModuleGroup> modules = new HashMap<String, ModuleGroup>();

		String[] repos = getRepositories();
		for (String repo : repos) {
			InputStream in = null;
			try {
				File cacheFile = getRepoCacheFile(repo);
				if (!cacheFile.exists())
					continue;

				in = new FileInputStream(cacheFile);
				if (repo.endsWith(".gz"))
					in = new GZIPInputStream(in);

				RepoParser parser = new RepoParser(in);
				Repository repository = parser.parse();

				for (Module mod : repository.modules.values()) {
					ModuleGroup existing = modules.get(mod.packageName);
					if (existing != null)
						existing.addModule(mod);
					else
						modules.put(mod.packageName, new ModuleGroup(mod));
				}

			} catch (Throwable t) {
				mMessages.add(mApp.getString(R.string.repo_load_failed, repo, t.getMessage()));
				removeRepoFile(repo);

			} finally {
				if (in != null)
					try { in.close(); } catch (IOException ignored) {}
			}
		}

		mModules = modules;
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
