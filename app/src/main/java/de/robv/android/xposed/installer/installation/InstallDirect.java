package de.robv.android.xposed.installer.installation;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.AssetUtil;
import de.robv.android.xposed.installer.util.InstallZipUtil;
import de.robv.android.xposed.installer.util.RootUtil;

import static de.robv.android.xposed.installer.util.InstallZipUtil.closeSilently;
import static de.robv.android.xposed.installer.util.InstallZipUtil.triggerError;
import static de.robv.android.xposed.installer.util.RootUtil.getShellPath;

public final class InstallDirect {
    public static void install(String zipPath, InstallCallback callback, boolean systemless) {
        // Open the ZIP file.
        ZipFile zip;
        try {
            zip = new ZipFile(zipPath);
        } catch (IOException e) {
            triggerError(callback, InstallCallback.ERROR_INVALID_ZIP, e.getLocalizedMessage());
            return;
        }

        // Do some checks.
        InstallZipUtil.ZipCheckResult zipCheck = InstallZipUtil.checkZip(zip);
        if (!zipCheck.isValidZip()) {
            triggerError(callback, InstallCallback.ERROR_INVALID_ZIP);
            closeSilently(zip);
            return;
        } else if (!zipCheck.isFlashableInApp()) {
            triggerError(callback, InstallCallback.ERROR_NOT_FLASHABLE_IN_APP);
            closeSilently(zip);
            return;
        }

        // Extract update-binary.
        ZipEntry entry = zip.getEntry("META-INF/com/google/android/update-binary");
        File updateBinaryFile = new File(XposedApp.getInstance().getCacheDir(), "update-binary");
        try {
            AssetUtil.writeStreamToFile(zip.getInputStream(entry), updateBinaryFile, 0700);
        } catch (IOException e) {
            Log.e(XposedApp.TAG, "Could not extract update-binary", e);
            triggerError(callback, InstallCallback.ERROR_INVALID_ZIP);
            return;
        } finally {
            closeSilently(zip);
        }

        // Execute the flash commands.
        RootUtil rootUtil = new RootUtil();
        if (!rootUtil.startShell()) {
            triggerError(callback, InstallCallback.ERROR_NO_ROOT_ACCESS);
            return;
        }

        callback.onStarted();

        rootUtil.execute("export NO_UIPRINT=1", callback);
        if (systemless) {
            rootUtil.execute("export SYSTEMLESS=1", callback);
        }

        int result = rootUtil.execute(getShellPath(updateBinaryFile) + " 2 1 " + getShellPath(zipPath), callback);
        if (result != 0) {
            triggerError(callback, result);
            return;
        }

        callback.onDone();
    }

    private InstallDirect() {}
}
