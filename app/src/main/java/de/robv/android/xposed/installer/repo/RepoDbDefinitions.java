package de.robv.android.xposed.installer.repo;

import android.database.Cursor;
import android.provider.BaseColumns;

public class RepoDbDefinitions {
    public static final int DATABASE_VERSION = 4;
    public static final String DATABASE_NAME = "repo_cache.db";


    //////////////////////////////////////////////////////////////////////////
    public static interface RepositoriesColumns extends BaseColumns {
        public static final String TABLE_NAME = "repositories";

        public static final String URL = "url";
        public static final String TITLE = "title";
        public static final String PARTIAL_URL = "partial_url";
        public static final String VERSION = "version";
    }
    static final String SQL_CREATE_TABLE_REPOSITORIES =
            "CREATE TABLE " + RepositoriesColumns.TABLE_NAME + " (" +
                    RepositoriesColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    RepositoriesColumns.URL + " TEXT NOT NULL, " +
                    RepositoriesColumns.TITLE + " TEXT, " +
                    RepositoriesColumns.PARTIAL_URL + " TEXT, " +
                    RepositoriesColumns.VERSION + " TEXT, " +
                    "UNIQUE (" + RepositoriesColumns.URL + ") ON CONFLICT REPLACE)";


    //////////////////////////////////////////////////////////////////////////
    public static interface ModulesColumns extends BaseColumns {
        public static final String TABLE_NAME = "modules";

        public static final String REPO_ID = "repo_id";
        public static final String PKGNAME = "pkgname";
        public static final String TITLE = "title";
        public static final String SUMMARY = "summary";
        public static final String DESCRIPTION = "description";
        public static final String DESCRIPTION_IS_HTML = "description_is_html";
        public static final String AUTHOR = "author";
        public static final String SUPPORT = "support";
        public static final String CREATED = "created";
        public static final String UPDATED = "updated";

        public static final String PREFERRED = "preferred";
        public static final String LATEST_VERSION = "latest_version_id";
    }
    static final String SQL_CREATE_TABLE_MODULES =
            "CREATE TABLE " + ModulesColumns.TABLE_NAME + " (" +
                    ModulesColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +

                    ModulesColumns.REPO_ID + " INTEGER NOT NULL REFERENCES "
                    + RepositoriesColumns.TABLE_NAME + " ON DELETE CASCADE, " +
                    ModulesColumns.PKGNAME + " TEXT NOT NULL, " +
                    ModulesColumns.TITLE + " TEXT NOT NULL, " +
                    ModulesColumns.SUMMARY + " TEXT, " +
                    ModulesColumns.DESCRIPTION + " TEXT, " +
                    ModulesColumns.DESCRIPTION_IS_HTML + " INTEGER DEFAULT 0, " +
                    ModulesColumns.AUTHOR + " TEXT, " +
                    ModulesColumns.SUPPORT + " TEXT, " +
                    ModulesColumns.CREATED + " INTEGER DEFAULT -1, " +
                    ModulesColumns.UPDATED + " INTEGER DEFAULT -1, " +
                    ModulesColumns.PREFERRED + " INTEGER DEFAULT 1, " +
                    ModulesColumns.LATEST_VERSION + " INTEGER REFERENCES " + ModuleVersionsColumns.TABLE_NAME + ", " +
                    "UNIQUE (" + ModulesColumns.PKGNAME + ", " + ModulesColumns.REPO_ID + ") ON CONFLICT REPLACE)";


    //////////////////////////////////////////////////////////////////////////
    public static interface ModuleVersionsColumns extends BaseColumns {
        public static final String TABLE_NAME = "module_versions";
        public static final String IDX_MODULE_ID = "module_versions_module_id_idx";

        public static final String MODULE_ID = "module_id";
        public static final String NAME = "name";
        public static final String CODE = "code";
        public static final String DOWNLOAD_LINK = "download_link";
        public static final String MD5SUM = "md5sum";
        public static final String CHANGELOG = "changelog";
        public static final String CHANGELOG_IS_HTML = "changelog_is_html";
        public static final String RELTYPE = "reltype";
        public static final String UPLOADED = "uploaded";
    }
    static final String SQL_CREATE_TABLE_MODULE_VERSIONS =
            "CREATE TABLE " + ModuleVersionsColumns.TABLE_NAME + " (" +
                    ModuleVersionsColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    ModuleVersionsColumns.MODULE_ID + " INTEGER NOT NULL REFERENCES "
                    + ModulesColumns.TABLE_NAME + " ON DELETE CASCADE, " +
                    ModuleVersionsColumns.NAME + " TEXT NOT NULL, " +
                    ModuleVersionsColumns.CODE + " INTEGER NOT NULL, " +
                    ModuleVersionsColumns.DOWNLOAD_LINK + " TEXT, " +
                    ModuleVersionsColumns.MD5SUM + " TEXT, " +
                    ModuleVersionsColumns.CHANGELOG + " TEXT, " +
                    ModuleVersionsColumns.CHANGELOG_IS_HTML + " INTEGER DEFAULT 0, " +
                    ModuleVersionsColumns.RELTYPE + " INTEGER DEFAULT 0, " +
                    ModuleVersionsColumns.UPLOADED + " INTEGER DEFAULT -1)";
    static final String SQL_CREATE_INDEX_MODULE_VERSIONS_MODULE_ID =
            "CREATE INDEX " + ModuleVersionsColumns.IDX_MODULE_ID + " ON " +
                    ModuleVersionsColumns.TABLE_NAME + " (" +
                    ModuleVersionsColumns.MODULE_ID + ")";


    //////////////////////////////////////////////////////////////////////////
    public static interface MoreInfoColumns extends BaseColumns {
        public static final String TABLE_NAME = "more_info";

        public static final String MODULE_ID = "module_id";
        public static final String LABEL = "label";
        public static final String VALUE = "value";
    }
    static final String SQL_CREATE_TABLE_MORE_INFO =
            "CREATE TABLE " + MoreInfoColumns.TABLE_NAME + " (" +
                    MoreInfoColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    MoreInfoColumns.MODULE_ID + " INTEGER NOT NULL REFERENCES "
                    + ModulesColumns.TABLE_NAME + " ON DELETE CASCADE, " +
                    MoreInfoColumns.LABEL + " TEXT NOT NULL, " +
                    MoreInfoColumns.VALUE + " TEXT)";


    //////////////////////////////////////////////////////////////////////////
    public static interface InstalledModulesColumns {
        public static final String TABLE_NAME = "installed_modules";

        public static final String PKGNAME = "pkgname";
        public static final String VERSION_CODE = "version_code";
        public static final String VERSION_NAME = "version_name";
    }
    static final String SQL_CREATE_TEMP_TABLE_INSTALLED_MODULES =
            "CREATE TEMP TABLE " + InstalledModulesColumns.TABLE_NAME + " (" +
                    InstalledModulesColumns.PKGNAME + " TEXT PRIMARY KEY ON CONFLICT REPLACE, " +
                    InstalledModulesColumns.VERSION_CODE + " INTEGER NOT NULL, " +
                    InstalledModulesColumns.VERSION_NAME + " TEXT)";


    //////////////////////////////////////////////////////////////////////////
    public static interface InstalledModulesUpdatesColumns {
        public static final String VIEW_NAME = InstalledModulesColumns.TABLE_NAME + "_updates";

        public static final String MODULE_ID = "module_id";
        public static final String PKGNAME = "pkgname";
        public static final String INSTALLED_CODE = "installed_code";
        public static final String INSTALLED_NAME = "installed_name";
        public static final String LATEST_ID = "latest_id";
        public static final String LATEST_CODE = "latest_code";
        public static final String LATEST_NAME = "latest_name";
    }
    static final String SQL_CREATE_TEMP_VIEW_INSTALLED_MODULES_UPDATES =
            "CREATE TEMP VIEW " + InstalledModulesUpdatesColumns.VIEW_NAME + " AS SELECT " +
                    "m." + ModulesColumns._ID + " AS " + InstalledModulesUpdatesColumns.MODULE_ID + ", " +
                    "i." + InstalledModulesColumns.PKGNAME + " AS " + InstalledModulesUpdatesColumns.PKGNAME + ", " +
                    "i." + InstalledModulesColumns.VERSION_CODE + " AS " + InstalledModulesUpdatesColumns.INSTALLED_CODE + ", " +
                    "i." + InstalledModulesColumns.VERSION_NAME + " AS " + InstalledModulesUpdatesColumns.INSTALLED_NAME + ", " +
                    "v." + ModuleVersionsColumns._ID + " AS " + InstalledModulesUpdatesColumns.LATEST_ID + ", " +
                    "v." + ModuleVersionsColumns.CODE + " AS " + InstalledModulesUpdatesColumns.LATEST_CODE + ", " +
                    "v." + ModuleVersionsColumns.NAME + " AS " + InstalledModulesUpdatesColumns.LATEST_NAME +
                    " FROM " + InstalledModulesColumns.TABLE_NAME + " AS i" +
                    " INNER JOIN " + ModulesColumns.TABLE_NAME + " AS m" +
                    " ON m." + ModulesColumns.PKGNAME + " = i." + InstalledModulesColumns.PKGNAME +
                    " INNER JOIN " + ModuleVersionsColumns.TABLE_NAME + " AS v" +
                    " ON v." + ModuleVersionsColumns._ID + " = m." + ModulesColumns.LATEST_VERSION +
                    " WHERE " + InstalledModulesUpdatesColumns.LATEST_CODE
                    + " > " + InstalledModulesUpdatesColumns.INSTALLED_CODE
                    + " AND " + ModulesColumns.PREFERRED + " = 1";


    //////////////////////////////////////////////////////////////////////////
    public interface OverviewColumns extends BaseColumns {
        public static final String PKGNAME = ModulesColumns.PKGNAME;
        public static final String TITLE = ModulesColumns.TITLE;
        public static final String SUMMARY = ModulesColumns.SUMMARY;
        public static final String CREATED = ModulesColumns.CREATED;
        public static final String UPDATED = ModulesColumns.UPDATED;

        public static final String INSTALLED_VERSION = "installed_version";
        public static final String LATEST_VERSION = "latest_version";

        public static final String IS_FRAMEWORK = "is_framework";
        public static final String IS_INSTALLED = "is_installed";
        public static final String HAS_UPDATE = "has_update";
    }

    public static class OverviewColumnsIndexes {
        private OverviewColumnsIndexes() {}

        public static int PKGNAME = -1;
        public static int TITLE = -1;
        public static int SUMMARY = -1;
        public static int CREATED = -1;
        public static int UPDATED = -1;

        public static int INSTALLED_VERSION = -1;
        public static int LATEST_VERSION = -1;

        public static int IS_FRAMEWORK = -1;
        public static int IS_INSTALLED = -1;
        public static int HAS_UPDATE = -1;

        private static boolean isFilled = false;

        public static void fillFromCursor(Cursor cursor) {
            if (isFilled || cursor == null)
                return;

            PKGNAME = cursor.getColumnIndexOrThrow(OverviewColumns.PKGNAME);
            TITLE = cursor.getColumnIndexOrThrow(OverviewColumns.TITLE);
            SUMMARY = cursor.getColumnIndexOrThrow(OverviewColumns.SUMMARY);
            CREATED = cursor.getColumnIndexOrThrow(OverviewColumns.CREATED);
            UPDATED = cursor.getColumnIndexOrThrow(OverviewColumns.UPDATED);
            INSTALLED_VERSION = cursor.getColumnIndexOrThrow(OverviewColumns.INSTALLED_VERSION);
            LATEST_VERSION = cursor.getColumnIndexOrThrow(OverviewColumns.LATEST_VERSION);
            INSTALLED_VERSION = cursor.getColumnIndexOrThrow(OverviewColumns.INSTALLED_VERSION);
            IS_FRAMEWORK = cursor.getColumnIndexOrThrow(OverviewColumns.IS_FRAMEWORK);
            IS_INSTALLED = cursor.getColumnIndexOrThrow(OverviewColumns.IS_INSTALLED);
            HAS_UPDATE = cursor.getColumnIndexOrThrow(OverviewColumns.HAS_UPDATE);

            isFilled = true;
        }
    }
}
