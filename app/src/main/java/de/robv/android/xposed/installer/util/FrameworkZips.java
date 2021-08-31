package de.robv.android.xposed.installer.util;

import android.content.Context;
import android.os.Build;
import android.support.annotation.StringRes;
import android.support.annotation.WorkerThread;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.DownloadsUtil.SyncDownloadInfo;
import de.robv.android.xposed.installer.util.InstallZipUtil.XposedProp;
import de.robv.android.xposed.installer.util.InstallZipUtil.ZipCheckResult;

public final class FrameworkZips {
    public static final String ARCH = getArch();
    public static final String SDK = Integer.toString(Build.VERSION.SDK_INT);

    private static final File ONLINE_FILE = new File(XposedApp.getInstance().getCacheDir(), "framework.json");
    private static final String ONLINE_URL = "http://dl-xda.xposed.info/framework.json";

    public enum Type {
        INSTALLER(R.string.install_update, R.string.framework_install, R.string.framework_install_recovery),
        UNINSTALLER(R.string.uninstall, R.string.uninstall, R.string.framework_uninstall_recovery);

        public final int title;
        public final int text_flash;
        public final int text_flash_recovery;

        Type(@StringRes int title, @StringRes int text_flash, @StringRes int text_flash_recovery) {
            this.title = title;
            this.text_flash = text_flash;
            this.text_flash_recovery = text_flash_recovery;
        }
    }
    private static final int TYPE_COUNT = Type.values().length;

    @SuppressWarnings("rawtypes")
    private static final Map[] EMPTY_MAP_ARRAY = new Map[TYPE_COUNT];
    static {
        Arrays.fill(EMPTY_MAP_ARRAY, Collections.emptyMap());
    }

    private static Map<String, OnlineFrameworkZip>[] sOnline = emptyMapArray();
    private static Map<String, List<LocalFrameworkZip>>[] sLocal = emptyMapArray();

    @SuppressWarnings("unchecked")
    public static <K,V> Map<K,V>[] emptyMapArray() {
        return (Map<K,V>[]) EMPTY_MAP_ARRAY;
    }

    public static class FrameworkZip {
        public String title;
        public Type type = Type.INSTALLER;

        public boolean isOutdated() {
            return true;
        }
    }

    public static class OnlineFrameworkZip extends FrameworkZip {
        public String url;
        public boolean current = true;

        public boolean isOutdated() {
            return !current;
        }
    }

    public static class LocalFrameworkZip extends FrameworkZip {
        public File path;
    }

    @WorkerThread
    private static void refreshOnline() {
        Map<String, OnlineFrameworkZip>[] zips = getOnline();
        synchronized (FrameworkZips.class) {
            sOnline = zips;
        }
    }

    // TODO provide user feedback in case of errors
    private static Map<String, OnlineFrameworkZip>[] getOnline() {
        String text;
        try {
            text = fileToString(ONLINE_FILE);
        } catch (FileNotFoundException e) {
            return emptyMapArray();
        } catch (IOException e) {
            Log.e(XposedApp.TAG, "Could not read " + ONLINE_FILE, e);
            return emptyMapArray();
        }

        try {
            JSONObject json = new JSONObject(text);

            //noinspection unchecked
            Map<String, OnlineFrameworkZip>[] zipsArray = new Map[TYPE_COUNT];
            for (int i = 0; i < TYPE_COUNT; i++) {
                zipsArray[i] = new LinkedHashMap<>();
            }

            JSONArray jsonZips = json.getJSONArray("zips");
            for (int i = 0; i < jsonZips.length(); i++) {
                parseZipSpec(jsonZips.getJSONObject(i), zipsArray);
            }

            return zipsArray;
        } catch (JSONException e) {
            Log.e(XposedApp.TAG, "Could not parse " + ONLINE_URL, e);
            return emptyMapArray();
        }
    }

    private static String fileToString(File file) throws IOException {
        Reader reader = null;
        try {
            reader = new FileReader(file);
            StringBuilder sb = new StringBuilder((int) file.length());
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static void parseZipSpec(JSONObject jsonZip, Map<String, OnlineFrameworkZip>[] zipsArray) throws JSONException {
        if (!contains(jsonZip, "archs", ARCH) || !contains(jsonZip, "sdks", SDK)) {
            return;
        }

        String titleTemplate = jsonZip.getString("title");
        String urlTemplate = jsonZip.getString("url");
        boolean current = jsonZip.optBoolean("current", false);
        String typeString = jsonZip.optString("type", null);
        Type type;
        if (typeString == null) {
            type = Type.INSTALLER;
        } else if (typeString.equals("uninstaller")) {
            type = Type.UNINSTALLER;
        } else {
            Log.w(XposedApp.TAG, "Unsupported framework zip type: " + typeString);
            return;
        }
        Map<String, OnlineFrameworkZip> zips = zipsArray[type.ordinal()];

        Map<String, String> attributes = new HashMap<>(3);

        JSONArray jsonVersions = jsonZip.optJSONArray("versions");
        if (jsonVersions != null) {
            Set<String> excludes = Collections.emptySet();
            JSONArray jsonExcludes = jsonZip.optJSONArray("exclude");
            if (jsonExcludes != null) {
                excludes = new HashSet<>();
                for (int i = 0; i < jsonExcludes.length(); i++) {
                    JSONObject jsonExclude = jsonExcludes.getJSONObject(i);
                    if (contains(jsonExclude, "archs", ARCH) && contains(jsonExclude, "sdks", SDK)) {
                        JSONArray jsonExcludeVersions = jsonExclude.getJSONArray("versions");
                        for (int j = 0; j < jsonExcludeVersions.length(); j++) {
                            excludes.add(jsonExcludeVersions.getString(j));
                        }
                    }
                }
            }

            for (int i = 0; i < jsonVersions.length(); i++) {
                JSONObject versionData = jsonVersions.getJSONObject(i);
                String version = versionData.getString("version");
                if (excludes.contains(version)) {
                    continue;
                }

                attributes.clear();
                attributes.put("arch", ARCH);
                attributes.put("sdk", SDK);
                parseAttributes(versionData, attributes);

                addZip(zips, titleTemplate, urlTemplate, attributes,
                        versionData.optBoolean("current", current), type);
            }
        } else {
            attributes.put("arch", ARCH);
            attributes.put("sdk", SDK);
            addZip(zips, titleTemplate, urlTemplate, attributes, current, type);
        }
    }

    private static boolean contains(JSONObject obj, String key, String value) throws JSONException {
        JSONArray array = obj.optJSONArray(key);
        if (array == null) {
            return true;
        }
        for (int i = 0; i < array.length(); i++) {
            if (array.getString(i).equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static void parseAttributes(JSONObject obj, Map<String, String> attributes) throws JSONException {
        if (obj != null) {
            Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                String key = it.next();
                Object value = obj.get(key);
                if (value instanceof String) {
                    attributes.put(key, (String) value);
                }
            }
        }
    }

    private static void addZip(Map<String, OnlineFrameworkZip> zips, String titleTemplate, String urlTemplate,
                               Map<String, String> attributes, boolean current, Type type) {
        String title = replacePlaceholders(titleTemplate, attributes);
        if (!zips.containsKey(title)) {
            OnlineFrameworkZip zip = new OnlineFrameworkZip();
            zip.title = title;
            zip.url = replacePlaceholders(urlTemplate, attributes);
            zip.current = current;
            zip.type = type;
            zips.put(zip.title, zip);
        }
    }

    private static String replacePlaceholders(String template, Map<String, String> values) {
        if (!template.contains("$(")) {
            return template;
        }

        StringBuilder sb = new StringBuilder(template);
        for (Entry<String, String> entry : values.entrySet()) {
            String search = "$(" + entry.getKey() + ")";
            int length = search.length();
            int index;
            while ((index = sb.indexOf(search)) != -1) {
                sb.replace(index, index + length, entry.getValue());
            }
        }
        return sb.toString();
    }

    @WorkerThread
    private static void refreshLocal() {
        //noinspection unchecked
        Map<String, List<LocalFrameworkZip>>[] zipsArray = new Map[TYPE_COUNT];
        for (int i = 0; i < TYPE_COUNT; i++) {
            zipsArray[i] = new TreeMap<>();
        }

        for (File dir : DownloadsUtil.getDownloadDirs(DownloadsUtil.DOWNLOAD_FRAMEWORK)) {
            if (!dir.isDirectory()) {
                continue;
            }
            for (String filename : dir.list()) {
                if (!filename.endsWith(".zip")) {
                    continue;
                }
                LocalFrameworkZip zip = analyze(new File(dir, filename));
                if (zip != null) {
                    Map<String, List<LocalFrameworkZip>> zips = zipsArray[zip.type.ordinal()];
                    List<LocalFrameworkZip> list = zips.get(zip.title);
                    if (list == null) {
                        list = new ArrayList<>(1);
                        zips.put(zip.title, list);
                    }
                    list.add(zip);
                }
            }
        }
        synchronized (FrameworkZips.class) {
            sLocal = zipsArray;
        }
    }

    // TODO Replace this with a proper way to report loading failures to the users.
    public static boolean hasLoadedOnlineZips() {
        return sOnline != EMPTY_MAP_ARRAY;
    }

    public static Set<String> getAllTitles(Type type) {
        Set<String> result = new LinkedHashSet<>(sOnline[type.ordinal()].keySet());
        result.addAll(sLocal[type.ordinal()].keySet());
        return result;
    }

    public static OnlineFrameworkZip getOnline(String title, Type type) {
        return sOnline[type.ordinal()].get(title);
    }

    public static LocalFrameworkZip getLocal(String title, Type type) {
        List<LocalFrameworkZip> all = sLocal[type.ordinal()].get(title);
        return all != null ? all.get(0) : null;
    }

    public static boolean hasLocal(String title, Type type) {
        return sLocal[type.ordinal()].containsKey(title);
    }

    public static List<LocalFrameworkZip> getAllLocal(String title, Type type) {
        List<LocalFrameworkZip> all = sLocal[type.ordinal()].get(title);
        return all != null ? all : Collections.<LocalFrameworkZip>emptyList();
    }

    public static void delete(Context context, String title, Type type) {
        OnlineFrameworkZip online = getOnline(title, type);
        if (online != null) {
            DownloadsUtil.removeAllForUrl(context, online.url);
        }

        List<LocalFrameworkZip> locals = getAllLocal(title, type);
        for (LocalFrameworkZip local : locals) {
            DownloadsUtil.removeAllForLocalFile(context, local.path);
        }
    }

    @WorkerThread
    private static LocalFrameworkZip analyze(File file) {
        String filename = file.getName();
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(file);
            ZipCheckResult zcr = InstallZipUtil.checkZip(zipFile);
            if (!zcr.isValidZip()) {
                return null;
            }

            LocalFrameworkZip zip = new LocalFrameworkZip();
            ZipEntry entry;
            if ((entry = zipFile.getEntry("system/xposed.prop")) != null) {
                XposedProp prop = InstallZipUtil.parseXposedProp(zipFile.getInputStream(entry));
                if (prop == null || !prop.isCompatible()) {
                    Log.w(XposedApp.TAG, "ZIP file is not compatible: " + file);
                    return null;
                }
                zip.title = "Version " + prop.getVersion();
            } else if (filename.startsWith("xposed-uninstaller-")) {
                // TODO provide more information inside uninstaller ZIPs
                zip.type = Type.UNINSTALLER;
                zip.title = "Uninstaller";
                int start = "xposed-uninstaller-".length();
                int end = filename.lastIndexOf('-');
                if (start < end) {
                    zip.title += " (" + filename.substring(start, end) + ")";
                }
            } else {
                return null;
            }

            zip.path = file;
            return zip;
        } catch (IOException e) {
            Log.e(XposedApp.TAG, "Errors while checking " + file, e);
            return null;
        } finally {
            if (zipFile != null) {
                InstallZipUtil.closeSilently(zipFile);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static String getArch() {
        if (Build.CPU_ABI.equals("arm64-v8a")) {
            return "arm64";
        } else if (Build.CPU_ABI.equals("x86_64")) {
            return "x86_64";
        } else if (Build.CPU_ABI.equals("mips64")) {
            return "mips64";
        } else if (Build.CPU_ABI.startsWith("x86") || Build.CPU_ABI2.startsWith("x86")) {
            return "x86";
        } else if (Build.CPU_ABI.startsWith("mips")) {
            return "mips";
        } else if (Build.CPU_ABI.startsWith("armeabi-v5") || Build.CPU_ABI.startsWith("armeabi-v6")) {
            return "armv5";
        } else {
            return "arm";
        }
    }

    private FrameworkZips() {
    }

    public static class OnlineZipLoader extends OnlineLoader<OnlineZipLoader> {
        private static OnlineZipLoader sInstance = new OnlineZipLoader();

        public static OnlineZipLoader getInstance() {
            return sInstance;
        }

        @Override
        protected synchronized void onFirstLoad() {
            new Thread("OnlineZipInit") {
                @Override
                public void run() {
                    refreshOnline();
                    notifyListeners();
                }
            }.start();
        }

        @Override
        protected boolean onReload() {
            SyncDownloadInfo info = DownloadsUtil.downloadSynchronously(ONLINE_URL, ONLINE_FILE);
            switch (info.status) {
                case SyncDownloadInfo.STATUS_NOT_MODIFIED:
                    return false;

                case SyncDownloadInfo.STATUS_FAILED:
                    onClear();
                    return true;

                case SyncDownloadInfo.STATUS_SUCCESS:
                default:
                    refreshOnline();
                    return true;
            }
        }

        @Override
        protected void onClear() {
            super.onClear();
            synchronized (this) {
                ONLINE_FILE.delete();
            }
            synchronized (FrameworkZips.class) {
                sOnline = emptyMapArray();
            }
        }
    }

    public static class LocalZipLoader extends Loader<LocalZipLoader> {
        private static LocalZipLoader sInstance = new LocalZipLoader();

        public static LocalZipLoader getInstance() {
            return sInstance;
        }

        @Override
        protected boolean onReload() {
            refreshLocal();
            return true;
        }

        @Override
        protected void onClear() {
            synchronized (FrameworkZips.class) {
                sLocal = emptyMapArray();
            }
        }
    }
}
