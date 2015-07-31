package de.robv.android.xposed.installer.repo;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Pair;
import de.robv.android.xposed.installer.repo.RepoDbDefinitions.InstalledModulesColumns;
import de.robv.android.xposed.installer.repo.RepoDbDefinitions.InstalledModulesUpdatesColumns;
import de.robv.android.xposed.installer.repo.RepoDbDefinitions.ModuleVersionsColumns;
import de.robv.android.xposed.installer.repo.RepoDbDefinitions.ModulesColumns;
import de.robv.android.xposed.installer.repo.RepoDbDefinitions.MoreInfoColumns;
import de.robv.android.xposed.installer.repo.RepoDbDefinitions.OverviewColumns;
import de.robv.android.xposed.installer.repo.RepoDbDefinitions.OverviewColumnsIndexes;
import de.robv.android.xposed.installer.repo.RepoDbDefinitions.RepositoriesColumns;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.RepoLoader;

public final class RepoDb extends SQLiteOpenHelper {
	public static final int SORT_STATUS = 0;
	public static final int SORT_UPDATED = 1;
	public static final int SORT_CREATED = 2;

	private static RepoDb mInstance;
	private static SQLiteDatabase mDb;
	private static RepoLoader mRepoLoader;

	public synchronized static void init(Context context, RepoLoader repoLoader) {
		if (mInstance != null)
			throw new IllegalStateException(RepoDb.class.getSimpleName() + " is already initialized");

		mRepoLoader = repoLoader;
		mInstance = new RepoDb(context);
		mDb = mInstance.getWritableDatabase();
		mDb.execSQL("PRAGMA foreign_keys=ON");
		mInstance.createTempTables(mDb);
	}

	private RepoDb(Context context) {
		super(context, new File(context.getCacheDir(), RepoDbDefinitions.DATABASE_NAME).getPath(),
				null, RepoDbDefinitions.DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(RepoDbDefinitions.SQL_CREATE_TABLE_REPOSITORIES);
		db.execSQL(RepoDbDefinitions.SQL_CREATE_TABLE_MODULES);
		db.execSQL(RepoDbDefinitions.SQL_CREATE_TABLE_MODULE_VERSIONS);
		db.execSQL(RepoDbDefinitions.SQL_CREATE_INDEX_MODULE_VERSIONS_MODULE_ID);
		db.execSQL(RepoDbDefinitions.SQL_CREATE_TABLE_MORE_INFO);

		mRepoLoader.clear(false);
	}

	private void createTempTables(SQLiteDatabase db) {
		db.execSQL(RepoDbDefinitions.SQL_CREATE_TEMP_TABLE_INSTALLED_MODULES);
		db.execSQL(RepoDbDefinitions.SQL_CREATE_TEMP_VIEW_INSTALLED_MODULES_UPDATES);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// This is only a cache, so simply drop & recreate the tables
		db.execSQL("DROP TABLE IF EXISTS " + RepositoriesColumns.TABLE_NAME);
		db.execSQL("DROP TABLE IF EXISTS " + ModulesColumns.TABLE_NAME);
		db.execSQL("DROP TABLE IF EXISTS " + ModuleVersionsColumns.TABLE_NAME);
		db.execSQL("DROP TABLE IF EXISTS " + MoreInfoColumns.TABLE_NAME);

		db.execSQL("DROP TABLE IF EXISTS " + InstalledModulesColumns.TABLE_NAME);
		db.execSQL("DROP VIEW IF EXISTS "  + InstalledModulesUpdatesColumns.VIEW_NAME);

		onCreate(db);
	}

	@Override
	public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		onUpgrade(db, oldVersion, newVersion);
	}

	public static void beginTransation() {
		mDb.beginTransaction();
	}

	public static void setTransactionSuccessful() {
		mDb.setTransactionSuccessful();
	}

	public static void endTransation() {
		mDb.endTransaction();
	}

	private static String getString(String table, String searchColumn, String searchValue, String resultColumn) {
		String[] projection = new String[] { resultColumn };
		String where = searchColumn + " = ?";
		String[] whereArgs = new String[] { searchValue };
		Cursor c = mDb.query(table, projection, where, whereArgs, null, null, null, "1");
		if (c.moveToFirst()) {
			String result = c.getString(c.getColumnIndexOrThrow(resultColumn));
			c.close();
			return result;
		} else {
			c.close();
			throw new RowNotFoundException("Could not find " + table + "." + searchColumn
					+ " with value '" + searchValue + "'");
		}
	}

	public static long insertRepository(String url) {
		ContentValues values = new ContentValues();
		values.put(RepositoriesColumns.URL, url);
		return mDb.insertOrThrow(RepositoriesColumns.TABLE_NAME, null, values);
	}

	public static void deleteRepositories() {
		if (mDb != null)
			mDb.delete(RepositoriesColumns.TABLE_NAME, null, null);
	}

	public static Map<Long,Repository> getRepositories() {
		Map<Long,Repository> result = new LinkedHashMap<Long, Repository>(1);

		String[] projection = new String[] {
			RepositoriesColumns._ID,
			RepositoriesColumns.URL,
			RepositoriesColumns.TITLE,
			RepositoriesColumns.PARTIAL_URL,
			RepositoriesColumns.VERSION,
		};

		Cursor c = mDb.query(RepositoriesColumns.TABLE_NAME, projection, null, null, null, null, RepositoriesColumns._ID);
		while (c.moveToNext()) {
			Repository repo = new Repository();
			long id = c.getLong(c.getColumnIndexOrThrow(RepositoriesColumns._ID));
			repo.url = c.getString(c.getColumnIndexOrThrow(RepositoriesColumns.URL));
			repo.name = c.getString(c.getColumnIndexOrThrow(RepositoriesColumns.TITLE));
			repo.partialUrl = c.getString(c.getColumnIndexOrThrow(RepositoriesColumns.PARTIAL_URL));
			repo.version = c.getString(c.getColumnIndexOrThrow(RepositoriesColumns.VERSION));

			result.put(id, repo);
		}
		c.close();

		return result;
	}

	public static void updateRepository(long repoId, Repository repository) {
		ContentValues values = new ContentValues();
		values.put(RepositoriesColumns.TITLE, repository.name);
		values.put(RepositoriesColumns.PARTIAL_URL, repository.partialUrl);
		values.put(RepositoriesColumns.VERSION, repository.version);
		mDb.update(RepositoriesColumns.TABLE_NAME, values,
				RepositoriesColumns._ID + " = ?",
				new String[] { Long.toString(repoId) });
	}

	public static void updateRepositoryVersion(long repoId, String version) {
		ContentValues values = new ContentValues();
		values.put(RepositoriesColumns.VERSION, version);
		mDb.update(RepositoriesColumns.TABLE_NAME, values,
				RepositoriesColumns._ID + " = ?",
				new String[] { Long.toString(repoId) });
	}

	public static long insertModule(long repoId, Module mod) {
		ContentValues values = new ContentValues();
		values.put(ModulesColumns.REPO_ID, repoId);
		values.put(ModulesColumns.PKGNAME, mod.packageName);
		values.put(ModulesColumns.TITLE, mod.name);
		values.put(ModulesColumns.SUMMARY, mod.summary);
		values.put(ModulesColumns.DESCRIPTION, mod.description);
		values.put(ModulesColumns.DESCRIPTION_IS_HTML, mod.descriptionIsHtml);
		values.put(ModulesColumns.AUTHOR, mod.author);
		values.put(ModulesColumns.SUPPORT, mod.support);
		values.put(ModulesColumns.CREATED, mod.created);
		values.put(ModulesColumns.UPDATED, mod.updated);

		ModuleVersion latestVersion = mRepoLoader.getLatestVersion(mod);

		mDb.beginTransaction();
		try {
			long moduleId = mDb.insertOrThrow(ModulesColumns.TABLE_NAME, null, values);

			long latestVersionId = -1;
			for (ModuleVersion version : mod.versions) {
				long versionId = insertModuleVersion(moduleId, version);
				if (latestVersion == version)
					latestVersionId = versionId;
			}

			if (latestVersionId > -1) {
				values = new ContentValues();
				values.put(ModulesColumns.LATEST_VERSION, latestVersionId);
				mDb.update(ModulesColumns.TABLE_NAME, values,
						ModulesColumns._ID + " = ?",
						new String[] { Long.toString(moduleId) });
			}

			for (Pair<String,String> moreInfoEntry : mod.moreInfo) {
				insertMoreInfo(moduleId, moreInfoEntry.first, moreInfoEntry.second);
			}

			// TODO Add mod.screenshots

			mDb.setTransactionSuccessful();
			return moduleId;

		} finally {
			mDb.endTransaction();
		}
	}

	private static long insertModuleVersion(long moduleId, ModuleVersion version) {
		ContentValues values = new ContentValues();
		values.put(ModuleVersionsColumns.MODULE_ID, moduleId);
		values.put(ModuleVersionsColumns.NAME, version.name);
		values.put(ModuleVersionsColumns.CODE, version.code);
		values.put(ModuleVersionsColumns.DOWNLOAD_LINK, version.downloadLink);
		values.put(ModuleVersionsColumns.MD5SUM, version.md5sum);
		values.put(ModuleVersionsColumns.CHANGELOG, version.changelog);
		values.put(ModuleVersionsColumns.CHANGELOG_IS_HTML, version.changelogIsHtml);
		values.put(ModuleVersionsColumns.RELTYPE, version.relType.ordinal());
		values.put(ModuleVersionsColumns.UPLOADED, version.uploaded);
		return mDb.insertOrThrow(ModuleVersionsColumns.TABLE_NAME, null, values);
	}

	private static long insertMoreInfo(long moduleId, String title, String value) {
		ContentValues values = new ContentValues();
		values.put(MoreInfoColumns.MODULE_ID, moduleId);
		values.put(MoreInfoColumns.LABEL, title);
		values.put(MoreInfoColumns.VALUE, value);
		return mDb.insertOrThrow(MoreInfoColumns.TABLE_NAME, null, values);
	}

	public static void deleteAllModules(long repoId) {
		mDb.delete(ModulesColumns.TABLE_NAME,
				ModulesColumns.REPO_ID + " = ?",
				new String[] { Long.toString(repoId) });
	}

	public static void deleteModule(long repoId, String packageName) {
		mDb.delete(ModulesColumns.TABLE_NAME,
				ModulesColumns.REPO_ID + " = ? AND " + ModulesColumns.PKGNAME + " = ?",
				new String[] { Long.toString(repoId), packageName });
	}

	public static Module getModuleByPackageName(String packageName) {
		// The module itself
		String[] projection = new String[] {
			ModulesColumns._ID,
			ModulesColumns.REPO_ID,
			ModulesColumns.PKGNAME,
			ModulesColumns.TITLE,
			ModulesColumns.SUMMARY,
			ModulesColumns.DESCRIPTION,
			ModulesColumns.DESCRIPTION_IS_HTML,
			ModulesColumns.AUTHOR,
			ModulesColumns.SUPPORT,
			ModulesColumns.CREATED,
			ModulesColumns.UPDATED,
		};

		String where = ModulesColumns.PREFERRED + " = 1 AND " + ModulesColumns.PKGNAME + " = ?";
		String[] whereArgs = new String[] { packageName };

		Cursor c = mDb.query(ModulesColumns.TABLE_NAME, projection, where, whereArgs, null, null, null, "1");
		if (!c.moveToFirst()) {
			c.close();
			return null;
		}

		long moduleId = c.getLong(c.getColumnIndexOrThrow(ModulesColumns._ID));
		long repoId = c.getLong(c.getColumnIndexOrThrow(ModulesColumns.REPO_ID));

		Module mod = new Module(mRepoLoader.getRepository(repoId));
		mod.packageName = c.getString(c.getColumnIndexOrThrow(ModulesColumns.PKGNAME));
		mod.name = c.getString(c.getColumnIndexOrThrow(ModulesColumns.TITLE));
		mod.summary = c.getString(c.getColumnIndexOrThrow(ModulesColumns.SUMMARY));
		mod.description = c.getString(c.getColumnIndexOrThrow(ModulesColumns.DESCRIPTION));
		mod.descriptionIsHtml = c.getInt(c.getColumnIndexOrThrow(ModulesColumns.DESCRIPTION_IS_HTML)) > 0;
		mod.author = c.getString(c.getColumnIndexOrThrow(ModulesColumns.AUTHOR));
		mod.support = c.getString(c.getColumnIndexOrThrow(ModulesColumns.SUPPORT));
		mod.created = c.getLong(c.getColumnIndexOrThrow(ModulesColumns.CREATED));
		mod.updated = c.getLong(c.getColumnIndexOrThrow(ModulesColumns.UPDATED));

		c.close();


		// Versions
		projection = new String[] {
			ModuleVersionsColumns.NAME,
			ModuleVersionsColumns.CODE,
			ModuleVersionsColumns.DOWNLOAD_LINK,
			ModuleVersionsColumns.MD5SUM,
			ModuleVersionsColumns.CHANGELOG,
			ModuleVersionsColumns.CHANGELOG_IS_HTML,
			ModuleVersionsColumns.RELTYPE,
			ModuleVersionsColumns.UPLOADED,
		};

		where = ModuleVersionsColumns.MODULE_ID + " = ?";
		whereArgs = new String[] { Long.toString(moduleId) };

		c = mDb.query(ModuleVersionsColumns.TABLE_NAME, projection, where, whereArgs, null, null, null);
		while (c.moveToNext()) {
			ModuleVersion version = new ModuleVersion(mod);
			version.name = c.getString(c.getColumnIndexOrThrow(ModuleVersionsColumns.NAME));
			version.code = c.getInt(c.getColumnIndexOrThrow(ModuleVersionsColumns.CODE));
			version.downloadLink = c.getString(c.getColumnIndexOrThrow(ModuleVersionsColumns.DOWNLOAD_LINK));
			version.md5sum = c.getString(c.getColumnIndexOrThrow(ModuleVersionsColumns.MD5SUM));
			version.changelog = c.getString(c.getColumnIndexOrThrow(ModuleVersionsColumns.CHANGELOG));
			version.changelogIsHtml = c.getInt(c.getColumnIndexOrThrow(ModuleVersionsColumns.CHANGELOG_IS_HTML)) > 0;
			version.relType = ReleaseType.fromOrdinal(c.getInt(c.getColumnIndexOrThrow(ModuleVersionsColumns.RELTYPE)));
			version.uploaded = c.getLong(c.getColumnIndexOrThrow(ModuleVersionsColumns.UPLOADED));
			mod.versions.add(version);
		}
		c.close();


		// MoreInfo
		projection = new String[] {
			MoreInfoColumns.LABEL,
			MoreInfoColumns.VALUE,
		};

		where = MoreInfoColumns.MODULE_ID + " = ?";
		whereArgs = new String[] { Long.toString(moduleId) };

		c = mDb.query(MoreInfoColumns.TABLE_NAME, projection, where, whereArgs, null, null, MoreInfoColumns._ID);
		while (c.moveToNext()) {
			String label = c.getString(c.getColumnIndexOrThrow(MoreInfoColumns.LABEL));
			String value = c.getString(c.getColumnIndexOrThrow(MoreInfoColumns.VALUE));
			mod.moreInfo.add(new Pair<String, String>(label, value));
		}
		c.close();

		return mod;
	}

	public static String getModuleSupport(String packageName) {
		return getString(ModulesColumns.TABLE_NAME, ModulesColumns.PKGNAME, packageName, ModulesColumns.SUPPORT);
	}

	public static void updateModuleLatestVersion(String packageName) {
		int maxShownReleaseType = mRepoLoader.getMaxShownReleaseType(packageName).ordinal();
		mDb.execSQL("UPDATE " + ModulesColumns.TABLE_NAME
			+ " SET " + ModulesColumns.LATEST_VERSION
				+ " = (SELECT " + ModuleVersionsColumns._ID + " FROM " + ModuleVersionsColumns.TABLE_NAME + " AS v"
				+ " WHERE v." + ModuleVersionsColumns.MODULE_ID
				+ " = " + ModulesColumns.TABLE_NAME + "." + ModulesColumns._ID
				+ " AND reltype <= ? LIMIT 1)"
			+ " WHERE " + ModulesColumns.PKGNAME + " = ?",
			new Object[] { maxShownReleaseType, packageName });
	}

	public static void updateAllModulesLatestVersion() {
		mDb.beginTransaction();
		try {
			String[] projection = new String[] { ModulesColumns.PKGNAME };
			Cursor c = mDb.query(true, ModulesColumns.TABLE_NAME, projection, null, null, null, null, null, null);
			while (c.moveToNext()) {
				updateModuleLatestVersion(c.getString(0));
			}
			c.close();
			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction();
		}
	}

	public static long insertInstalledModule(InstalledModule installed) {
		ContentValues values = new ContentValues();
		values.put(InstalledModulesColumns.PKGNAME, installed.packageName);
		values.put(InstalledModulesColumns.VERSION_CODE, installed.versionCode);
		values.put(InstalledModulesColumns.VERSION_NAME, installed.versionName);
		return mDb.insertOrThrow(InstalledModulesColumns.TABLE_NAME, null, values);
	}

	public static void deleteInstalledModule(String packageName) {
		mDb.delete(InstalledModulesColumns.TABLE_NAME,
				InstalledModulesColumns.PKGNAME + " = ?",
				new String[] { packageName });
	}

	public static void deleteAllInstalledModules() {
		mDb.delete(InstalledModulesColumns.TABLE_NAME, null, null);
	}

	public static Cursor queryModuleOverview(int sortingOrder, CharSequence filterText) {
		// Columns
		String[] projection = new String[] {
			"m." + ModulesColumns._ID,
			"m." + ModulesColumns.PKGNAME,
			"m." + ModulesColumns.TITLE,
			"m." + ModulesColumns.SUMMARY,
			"m." + ModulesColumns.CREATED,
			"m." + ModulesColumns.UPDATED,

			"v." + ModuleVersionsColumns.NAME + " AS " + OverviewColumns.LATEST_VERSION,
			"i." + InstalledModulesColumns.VERSION_NAME + " AS " + OverviewColumns.INSTALLED_VERSION,

			"(CASE WHEN m." + ModulesColumns.PKGNAME + " = '" + ModuleUtil.getInstance().getFrameworkPackageName()
				+ "' THEN 1 ELSE 0 END) AS " + OverviewColumns.IS_FRAMEWORK,

			"(CASE WHEN i." + InstalledModulesColumns.VERSION_NAME + " IS NOT NULL"
				+ " THEN 1 ELSE 0 END) AS " + OverviewColumns.IS_INSTALLED,

			"(CASE WHEN v." + ModuleVersionsColumns.CODE + " > " + InstalledModulesColumns.VERSION_CODE
				+ " THEN 1 ELSE 0 END) AS " + OverviewColumns.HAS_UPDATE,
		};

		// Conditions
		String where = ModulesColumns.PREFERRED + " = 1";
		String whereArgs[] = null;
		if (!TextUtils.isEmpty(filterText)) {
			where += " AND (m." + ModulesColumns.TITLE + " LIKE ?"
				+ " OR m." + ModulesColumns.SUMMARY + " LIKE ?"
				+ " OR m." + ModulesColumns.DESCRIPTION + " LIKE ?"
				+ " OR m." + ModulesColumns.AUTHOR + " LIKE ?)";

			String filterTextArg = "%" + filterText + "%";
			whereArgs = new String[] { filterTextArg, filterTextArg, filterTextArg, filterTextArg };
		}

		// Sorting order
		StringBuilder sbOrder = new StringBuilder();
		if (sortingOrder == SORT_CREATED) {
			sbOrder.append(OverviewColumns.CREATED);
			sbOrder.append(" DESC,");
		} else if (sortingOrder == SORT_UPDATED) {
			sbOrder.append(OverviewColumns.UPDATED);
			sbOrder.append(" DESC,");
		}
		sbOrder.append(OverviewColumns.IS_FRAMEWORK);
		sbOrder.append(" DESC, ");
		sbOrder.append(OverviewColumns.HAS_UPDATE);
		sbOrder.append(" DESC, ");
		sbOrder.append(OverviewColumns.IS_INSTALLED);
		sbOrder.append(" DESC, ");
		sbOrder.append("m.");
		sbOrder.append(OverviewColumns.TITLE);
		sbOrder.append(" COLLATE NOCASE, ");
		sbOrder.append("m.");
		sbOrder.append(OverviewColumns.PKGNAME);

		// Query
		Cursor c = mDb.query(
			ModulesColumns.TABLE_NAME + " AS m" +
				" LEFT JOIN " + ModuleVersionsColumns.TABLE_NAME + " AS v" +
					" ON v." + ModuleVersionsColumns._ID + " = m." + ModulesColumns.LATEST_VERSION +
				" LEFT JOIN " + InstalledModulesColumns.TABLE_NAME + " AS i" +
					" ON i." + InstalledModulesColumns.PKGNAME + " = m." + ModulesColumns.PKGNAME,
			projection, where, whereArgs, null, null, sbOrder.toString());

		// Cache column indexes
		OverviewColumnsIndexes.fillFromCursor(c);

		return c;
	}

	public static String getFrameworkUpdateVersion() {
		return getFirstUpdate(true);
	}

	public static boolean hasModuleUpdates() {
		return getFirstUpdate(false) != null;
	}

	private static String getFirstUpdate(boolean framework) {
		String[] projection = new String[] { InstalledModulesUpdatesColumns.LATEST_NAME };
		String where = ModulesColumns.PKGNAME + (framework ? " = ?" : " != ?");
		String[] whereArgs = new String[] { ModuleUtil.getInstance().getFrameworkPackageName() };
		Cursor c = mDb.query(InstalledModulesUpdatesColumns.VIEW_NAME, projection, where, whereArgs, null, null, null, "1");
		String latestVersion = null;
		if (c.moveToFirst())
			latestVersion = c.getString(c.getColumnIndexOrThrow(InstalledModulesUpdatesColumns.LATEST_NAME));
		c.close();
		return latestVersion;
	}


	public static class RowNotFoundException extends RuntimeException {
		private static final long serialVersionUID = -396324186622439535L;
		public RowNotFoundException(String reason) {
			super(reason);
		}
	}
}
