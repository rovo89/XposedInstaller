package de.robv.android.xposed.installer.util;

import android.content.Context;
import android.os.Build;
import android.support.annotation.WorkerThread;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.InstallZipUtil.XposedProp;
import de.robv.android.xposed.installer.util.InstallZipUtil.ZipCheckResult;

public class FrameworkZips {
    public static String ARCH = getArch();

    private static Map<String, OnlineFrameworkZip> sOnline = Collections.emptyMap();
    private static Map<String, List<LocalFrameworkZip>> sLocal = Collections.emptyMap();

    public static class FrameworkZip {
        public String title;
        public boolean uninstaller = false;
    }

    public static class OnlineFrameworkZip extends FrameworkZip {
        public String url;
        public boolean current = true;
    }

    public static class LocalFrameworkZip extends FrameworkZip {
        public File path;
    }

    @WorkerThread
    public static void refreshOnline() {
        // TODO fetch list from server, make sure no duplicates exist
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {}
        Map<String, OnlineFrameworkZip> zips = new LinkedHashMap<>();
        for (int version = 86; version >= 79; version--) {
            String title = "Version " + version;
            if (zips.containsKey(title)) {
                continue;
            }
            OnlineFrameworkZip zip = new OnlineFrameworkZip();
            zip.title = title;
            zip.url = "http://dl-xda.xposed.info/framework/sdk23/x86/xposed-v" + version + "-sdk23-x86.zip";
            zip.current = version >= 84;
            zips.put(zip.title, zip);
        }

        OnlineFrameworkZip zip = new OnlineFrameworkZip();
        zip.title = "Uninstaller (20150831)";
        zip.url = "http://dl-xda.xposed.info/framework/uninstaller/xposed-uninstaller-20150831-x86.zip";
        zip.current = true;
        zip.uninstaller = true;
        zips.put(zip.title, zip);

        synchronized (FrameworkZips.class) {
            sOnline = zips;
        }
    }

    @WorkerThread
    public static void refreshLocal() {
        Map<String, List<LocalFrameworkZip>> zips = new TreeMap<>();
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
            sLocal = zips;
        }
    }

    public static Set<String> getAllTitles() {
        Set<String> result = new LinkedHashSet<>(sOnline.keySet());
        result.addAll(sLocal.keySet());
        return result;
    }

    public static OnlineFrameworkZip getOnline(String title) {
        return sOnline.get(title);
    }

    public static LocalFrameworkZip getLocal(String title) {
        List<LocalFrameworkZip> all = sLocal.get(title);
        return all != null ? all.get(0) : null;
    }

    public static boolean hasLocal(String title) {
        return sLocal.containsKey(title);
    }

    public static List<LocalFrameworkZip> getAllLocal(String title) {
        List<LocalFrameworkZip> all = sLocal.get(title);
        return all != null ? all : Collections.<LocalFrameworkZip>emptyList();
    }

    public static void delete(Context context, String title) {
        OnlineFrameworkZip online = getOnline(title);
        if (online != null) {
            DownloadsUtil.removeAllForUrl(context, online.url);
        }

        List<LocalFrameworkZip> locals = getAllLocal(title);
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
                zip.uninstaller = true;
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
}
