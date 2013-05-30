package de.robv.android.xposed.installer.util;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import android.app.Application;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.ModuleGroup;
import de.robv.android.xposed.installer.repo.RepoParser;
import de.robv.android.xposed.installer.repo.Repository;

public class RepoLoader {
	private static RepoLoader mInstance = null;
	private Application mApp = null;
	
	private Map<String, ModuleGroup> mModules = new HashMap<String, ModuleGroup>(0);
	private boolean mIsLoading = false;
	private final List<String> mMessages = new LinkedList<String>();
	private final List<RepoListener> mListeners = new CopyOnWriteArrayList<RepoListener>();
	
	private RepoLoader() {}
	
	/** call this only once (from the Application) */
	public static void init(Application app) {
		if (mInstance != null)
			throw new IllegalStateException("this class must only be initialized once");
		
		mInstance = new RepoLoader();
		mInstance.mApp = app;
		mInstance.triggerReload();
	}
	
	public static RepoLoader getInstance() {
		return mInstance;
	}
	
	public Map<String, ModuleGroup> getModules() {
		return mModules;
	}
	
	public ModuleGroup getModule(String packageName) {
		return mModules.get(packageName);
	}
	
	public void triggerReload() {
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
	
	private void downloadFiles() {
		// TODO implement me
	}
	
	private void parseFiles() {
		Map<String, ModuleGroup> modules = new HashMap<String, ModuleGroup>();
		
		// TODO do this for all downloaded repo.xml files...
		try {
			InputStream is = mApp.getResources().getAssets().open("repo.xml");
			RepoParser parser = new RepoParser(is);
			Repository repo = parser.parse();
	
			for (Module mod : repo.modules.values()) {
				ModuleGroup existing = modules.get(mod.packageName);
				if (existing != null)
					existing.addModule(mod);
				else
					modules.put(mod.packageName, new ModuleGroup(mod));
			}
		} catch (Throwable t) {
			mMessages.add(String.format("cannot load repository from %s:\n%s", "FIXME", t.getMessage()));
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
