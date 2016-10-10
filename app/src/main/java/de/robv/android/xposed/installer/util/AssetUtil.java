package de.robv.android.xposed.installer.util;

import android.content.res.AssetManager;
import android.os.Build;
import android.os.FileUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import de.robv.android.xposed.installer.XposedApp;

public class AssetUtil {
    public static final File BUSYBOX_FILE = new File(XposedApp.getInstance().getCacheDir(), "busybox-xposed");

    @SuppressWarnings("deprecation")
    public static String getBinariesFolder() {
        if (Build.CPU_ABI.startsWith("arm")) {
            return "arm/";
        } else if (Build.CPU_ABI.startsWith("x86")) {
            return "x86/";
        } else {
            return null;
        }
    }

    public static File writeAssetToFile(AssetManager assets, String assetName, File targetFile, int mode) {
        try {
            if (assets == null)
                assets = XposedApp.getInstance().getAssets();
            InputStream in = assets.open(assetName);
            writeStreamToFile(in, targetFile, mode);;
            return targetFile;
        } catch (IOException e) {
            Log.e(XposedApp.TAG, "could not extract asset", e);
            if (targetFile != null)
                targetFile.delete();

            return null;
        }
    }

    public static void writeStreamToFile(InputStream in, File targetFile, int mode) throws IOException {
        FileOutputStream out = new FileOutputStream(targetFile);

        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
        in.close();
        out.close();

        FileUtils.setPermissions(targetFile.getAbsolutePath(), mode, -1, -1);
    }

    public synchronized static void extractBusybox() {
        if (BUSYBOX_FILE.exists())
            return;

        AssetManager assets = null;
        writeAssetToFile(assets, getBinariesFolder() + "busybox-xposed", BUSYBOX_FILE, 00700);
    }

    public synchronized static void removeBusybox() {
        BUSYBOX_FILE.delete();
    }
}
