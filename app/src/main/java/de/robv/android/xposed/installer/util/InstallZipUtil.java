package de.robv.android.xposed.installer.util;

import java.io.IOException;
import java.util.zip.ZipFile;

import de.robv.android.xposed.installer.installation.InstallCallback;

public final class InstallZipUtil {
    public static class ZipCheckResult {
        private boolean mValidZip = false;
        private boolean mFlashableInApp = false;

        public boolean isValidZip() {
            return mValidZip;
        }

        public boolean isFlashableInApp() {
            return mFlashableInApp;
        }
    }

    public static ZipCheckResult checkZip(String zipPath) {
        ZipFile zip;
        try {
            zip = new ZipFile(zipPath);
        } catch (IOException e) {
            return new ZipCheckResult();
        }

        ZipCheckResult result = checkZip(zip);
        closeSilently(zip);
        return result;
    }

    public static ZipCheckResult checkZip(ZipFile zip) {
        ZipCheckResult result = new ZipCheckResult();

        // Check for update-binary.
        if (zip.getEntry("META-INF/com/google/android/update-binary") == null) {
            return result;
        }

        result.mValidZip = true;

        // Check whether the file can be flashed directly in the app.
        if (zip.getEntry("META-INF/com/google/android/flash-script.sh") != null) {
            result.mFlashableInApp = true;
        }

        return result;
    }

    public static String messageForError(int code) {
        // TODO make translatable
        switch (code) {
            case InstallCallback.ERROR_TIMEOUT:
                return "Timeout occured";

            case InstallCallback.ERROR_SHELL_DIED:
                return "Execution aborted unexpectedly";

            case InstallCallback.ERROR_EXEC_FAILED:
            case InstallCallback.ERROR_WRONG_UID:
                return "Could not gain root access";

            case InstallCallback.ERROR_INVALID_ZIP:
                return "Not a flashable ZIP file";

            case InstallCallback.ERROR_NOT_FLASHABLE_IN_APP:
                return "This file can only be flashed via recovery";

            default:
                return "Error " + code + " occurred";
        }
    }

    public static void triggerError(InstallCallback callback, int code) {
        callback.onError(code, messageForError(code));
    }

    public static void closeSilently(ZipFile z) {
        try {
            z.close();
        } catch (IOException ignored) {}
    }

    private InstallZipUtil() {}
}
