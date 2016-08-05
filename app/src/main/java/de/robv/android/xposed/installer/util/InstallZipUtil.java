package de.robv.android.xposed.installer.util;

import android.os.Build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    public static class XposedProp {
        private String mVersion = null;
        private int mVersionInt = 0;
        private String mArch = null;
        private int mMinSdk = 0;
        private int mMaxSdk = 0;

        private boolean isComplete() {
            return mVersion != null
                    && mVersionInt > 0
                    && mArch != null
                    && mMinSdk > 0
                    && mMaxSdk > 0;
        }

        public String getVersion() {
            return mVersion;
        }

        public boolean isArchCompatible() {
            // FIXME
            return mArch.equals("x86");
        }

        public boolean isSdkCompatible() {
            return mMinSdk <= Build.VERSION.SDK_INT && Build.VERSION.SDK_INT <= mMaxSdk;
        }

        public boolean isCompatible() {
            return isSdkCompatible() && isArchCompatible();
        }
    }

    public static XposedProp parseXposedProp(InputStream is) throws IOException {
        XposedProp prop = new XposedProp();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                continue;
            }

            String key = parts[0].trim();
            if (key.charAt(0) == '#') {
                continue;
            }

            String value = parts[1].trim();

            if (key.equals("version")) {
                prop.mVersion = value;
                prop.mVersionInt = ModuleUtil.extractIntPart(value);
            } else if (key.equals("arch")) {
                prop.mArch = value;
            } else if (key.equals("minsdk")) {
                prop.mMinSdk = Integer.parseInt(value);
            } else if (key.equals("maxsdk")) {
                prop.mMaxSdk = Integer.parseInt(value);
            }
        }
        reader.close();
        return prop.isComplete() ? prop : null;
    }

    public static String messageForError(int code, Object... args) {
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
                if (args.length > 0) {
                    return "Not a flashable ZIP file" + "\n" + args[0];
                } else {
                    return "Not a flashable ZIP file";
                }

            case InstallCallback.ERROR_NOT_FLASHABLE_IN_APP:
                return "This file can only be flashed via recovery";

            default:
                return "Error " + code + " occurred";
        }
    }

    public static void triggerError(InstallCallback callback, int code, Object... args) {
        callback.onError(code, messageForError(code, args));
    }

    public static void closeSilently(ZipFile z) {
        try {
            z.close();
        } catch (IOException ignored) {}
    }

    private InstallZipUtil() {}
}
