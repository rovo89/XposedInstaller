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
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.ModuleGroup;
import de.robv.android.xposed.installer.repo.RepoParser;
import de.robv.android.xposed.installer.repo.Repository;

public class RepoLoader {
	private static RepoLoader mInstance = null;
	private XposedApp mApp = null;
	private SharedPreferences mPref;
	
	private Map<String, ModuleGroup> mModules = new HashMap<String, ModuleGroup>(0);
	private boolean mIsLoading = false;
	private final List<String> mMessages = new LinkedList<String>();
	private final List<RepoListener> mListeners = new CopyOnWriteArrayList<RepoListener>();
	
	private RepoLoader(XposedApp app) {
		mApp = app;
		mPref = mApp.getSharedPreferences("repo", Context.MODE_PRIVATE);
		triggerReload();
	}
	
	/** call this only once (from the Application) */
	public static void init(XposedApp app) {
		if (mInstance != null)
			throw new IllegalStateException("this class must only be initialized once");
		
		mInstance = new RepoLoader(app);
	}
	
	public static RepoLoader getInstance() {
		return mInstance;
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

	public void triggerReload() {
		if (!mApp.enableDownloads())
			return;

		synchronized (this) {
			if (mIsLoading)
				return;
			mIsLoading = true;
		}
		
		new Thread("RepositoryReload") {
			public void run() {
				downloadFiles();
				parseFiles();
				for (RepoListener listener : mListeners) {
					listener.onRepoReloaded(mInstance);
				}
				synchronized (this) {
					mIsLoading = false;
				}
			}
		}.start();
	}

	public void clear() {
		synchronized (this) {
			if (mIsLoading)
				return;

			mModules = new HashMap<String, ModuleGroup>();
		}
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
	
	private File getRepoCacheFile(String repo) {
		String filename = "repo_" + HashUtil.md5(repo) + ".xml";
		if (repo.endsWith(".gz"))
			filename += ".gz";
		return new File(mApp.getCacheDir(), filename);
	}
	
	private void downloadFiles() {
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
						mMessages.add(String.format("Downloading %s failed: %d (%s)", repo, responseCode, httpConnection.getResponseMessage()));
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
				mMessages.add(String.format("Downloading %s failed: %s", repo, t.getMessage()));

			} finally {
				if (connection != null && connection instanceof HttpURLConnection)
					((HttpURLConnection) connection).disconnect();
				if (in != null)
	                try { in.close(); } catch (IOException ignored) {}
				if (out != null)
					try { out.close(); } catch (IOException ignored) {}
			}
		}
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
				mMessages.add(String.format("Cannot load repository from %s:\n%s", repo, t.getMessage()));

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
	
	public interface RepoListener {
		/**
		 * Called whenever the list of modules from repositories has been successfully reloaded
		 */
		public void onRepoReloaded(RepoLoader loader);
	}
}
