package de.robv.android.xposed.installer.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

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

public class RepoLoader extends OnlineLoader<RepoLoader> {
    private static final String DEFAULT_REPOSITORIES = "http://dl.xposed.info/repo/full.xml.gz";
    private static RepoLoader mInstance = null;
    private static final XposedApp sApp = XposedApp.getInstance();
    private final Map<String, ReleaseType> mLocalReleaseTypesCache = new HashMap<>();
    private SharedPreferences mModulePref;
    private Map<Long, Repository> mRepositories = null;
    private ReleaseType mGlobalReleaseType;

    private RepoLoader() {
        mInstance = this;
        mPref = sApp.getSharedPreferences("repo", Context.MODE_PRIVATE);
        mPrefKeyLastUpdateCheck = "last_update_check";
        mModulePref = sApp.getSharedPreferences("module_settings", Context.MODE_PRIVATE);
        mGlobalReleaseType = ReleaseType.fromString(XposedApp.getPreferences().getString("release_type_global", "stable"));
        refreshRepositories();
    }

    public static synchronized RepoLoader getInstance() {
        if (mInstance == null)
            new RepoLoader();
        return mInstance;
    }

    public boolean refreshRepositories() {
        mRepositories = RepoDb.getRepositories();

		// Unlikely case (usually only during initial load): DB state doesn't
		// fit to configuration
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
        if (mGlobalReleaseType == relType)
            return;

        mGlobalReleaseType = relType;

        // Updating the latest version for all modules takes a moment
        new Thread("DBUpdate") {
            @Override
            public void run() {
                RepoDb.updateAllModulesLatestVersion();
                notifyListeners();
            }
        }.start();
    }

    public void setReleaseTypeLocal(String packageName, String relTypeString) {
        ReleaseType relType = (!TextUtils.isEmpty(relTypeString)) ? ReleaseType.fromString(relTypeString) : null;

        if (getReleaseTypeLocal(packageName) == relType)
            return;

        synchronized (mLocalReleaseTypesCache) {
            mLocalReleaseTypesCache.put(packageName, relType);
        }

        RepoDb.updateModuleLatestVersion(packageName);
        notifyListeners();
    }

    private ReleaseType getReleaseTypeLocal(String packageName) {
        synchronized (mLocalReleaseTypesCache) {
            if (mLocalReleaseTypesCache.containsKey(packageName))
                return mLocalReleaseTypesCache.get(packageName);

            String value = mModulePref.getString(packageName + "_release_type",
                    null);
            ReleaseType result = (!TextUtils.isEmpty(value)) ? ReleaseType.fromString(value) : null;
            mLocalReleaseTypesCache.put(packageName, result);
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
        return version.relType
                .ordinal() <= getMaxShownReleaseType(version.module.packageName).ordinal();
    }

    public ReleaseType getMaxShownReleaseType(String packageName) {
        ReleaseType localSetting = getReleaseTypeLocal(packageName);
        if (localSetting != null)
            return localSetting;
        else
            return mGlobalReleaseType;
    }

    @Override
    protected void onClear() {
        super.onClear();
        RepoDb.deleteRepositories();
        mRepositories = new LinkedHashMap<>(0);
        DownloadsUtil.clearCache(null);
    }

    public void setRepositories(String... repos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < repos.length; i++) {
            if (i > 0)
                sb.append("|");
            sb.append(repos[i]);
        }
        mPref.edit().putString("repositories", sb.toString()).apply();
        if (refreshRepositories())
            triggerReload(true);
    }

    public boolean hasModuleUpdates() {
        return RepoDb.hasModuleUpdates();
    }

    public String getFrameworkUpdateVersion() {
        return RepoDb.getFrameworkUpdateVersion();
    }

    private File getRepoCacheFile(String repo) {
        String filename = "repo_" + HashUtil.md5(repo) + ".xml";
        if (repo.endsWith(".gz"))
            filename += ".gz";
        return new File(sApp.getCacheDir(), filename);
    }

    @Override
    protected boolean onReload() {
        final List<String> messages = new LinkedList<>();

        boolean hasChanged = downloadAndParseFiles(messages);
        if (!messages.isEmpty()) {
            XposedApp.runOnUiThread(new Runnable() {
                public void run() {
                    for (String message : messages) {
                        Toast.makeText(sApp, message, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        return hasChanged;
    }

    private boolean downloadAndParseFiles(List<String> messages) {
        // These variables don't need to be atomic, just mutable
        final AtomicBoolean hasChanged = new AtomicBoolean(false);
        final AtomicInteger insertCounter = new AtomicInteger();
        final AtomicInteger deleteCounter = new AtomicInteger();

        for (Entry<Long, Repository> repoEntry : mRepositories.entrySet()) {
            final long repoId = repoEntry.getKey();
            final Repository repo = repoEntry.getValue();

            String url = (repo.partialUrl != null && repo.version != null) ? String.format(repo.partialUrl, repo.version) : repo.url;

            File cacheFile = getRepoCacheFile(url);
            SyncDownloadInfo info = DownloadsUtil.downloadSynchronously(url,
                    cacheFile);

            Log.i(XposedApp.TAG, String.format(
                    "Downloaded %s with status %d (error: %s), size %d bytes",
                    url, info.status, info.errorMessage, cacheFile.length()));

            if (info.status != SyncDownloadInfo.STATUS_SUCCESS) {
                if (info.errorMessage != null)
                    messages.add(info.errorMessage);
                continue;
            }

            InputStream in = null;
            RepoDb.beginTransation();
            try {
                in = new FileInputStream(cacheFile);
                if (url.endsWith(".gz"))
                    in = new GZIPInputStream(in);

                RepoParser.parse(in, new RepoParserCallback() {
                    @Override
                    public void onRepositoryMetadata(Repository repository) {
                        if (!repository.isPartial) {
                            RepoDb.deleteAllModules(repoId);
                            hasChanged.set(true);
                        }
                    }

                    @Override
                    public void onNewModule(Module module) {
                        RepoDb.insertModule(repoId, module);
                        hasChanged.set(true);
                        insertCounter.incrementAndGet();
                    }

                    @Override
                    public void onRemoveModule(String packageName) {
                        RepoDb.deleteModule(repoId, packageName);
                        hasChanged.set(true);
                        deleteCounter.decrementAndGet();
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

                        Log.i(XposedApp.TAG, String.format(
                                "Updated repository %s to version %s (%d new / %d removed modules)",
                                repo.url, repo.version, insertCounter.get(),
                                deleteCounter.get()));
                    }
                });

                RepoDb.setTransactionSuccessful();
            } catch (Throwable t) {
                Log.e(XposedApp.TAG, "Cannot load repository from " + url, t);
                messages.add(sApp.getString(R.string.repo_load_failed, url, t.getMessage()));
                DownloadsUtil.clearCache(url);
            } finally {
                if (in != null)
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                cacheFile.delete();
                RepoDb.endTransation();
            }
        }

        // TODO Set ModuleColumns.PREFERRED for modules which appear in multiple
        // repositories
        return hasChanged.get();
    }
}
