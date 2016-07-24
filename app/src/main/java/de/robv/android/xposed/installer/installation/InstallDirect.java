package de.robv.android.xposed.installer.installation;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.AssetUtil;
import de.robv.android.xposed.installer.util.InstallZipUtil;
import eu.chainfire.libsuperuser.Shell;

import static de.robv.android.xposed.installer.util.InstallZipUtil.closeSilently;
import static de.robv.android.xposed.installer.util.InstallZipUtil.triggerError;

public final class InstallDirect {
    public static void install(String zipPath, InstallCallback callback, boolean systemless) {
        // Open the ZIP file.
        ZipFile zip;
        try {
            zip = new ZipFile(zipPath);
        } catch (IOException e) {
            Log.e(XposedApp.TAG, "Could not open ZIP file", e);
            triggerError(callback, InstallCallback.ERROR_INVALID_ZIP);
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
        Shell.Builder builder = new Shell.Builder()
                .useSU()
                .setOnSTDERRLineListener(new StderrListener(callback))
                .addEnvironment("NO_UIPRINT", "1");

        if (systemless) {
            builder.addEnvironment("SYSTEMLESS", "1");
        }

        Shell.Interactive shell = builder.open(new OpenListener(callback));
        shell.addCommand(updateBinaryFile.getAbsolutePath() + " 2 1 " + zipPath, 0, new StdoutListener(callback));
        shell.addCommand("exit");
    }

    private static class OpenListener implements Shell.OnCommandResultListener {
        private InstallCallback callback;

        public OpenListener(InstallCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onCommandResult(int commandCode, int exitCode, List<String> output) {
            if (exitCode == SHELL_RUNNING) {
                callback.onStarted();
            } else {
                triggerError(callback, exitCode);
            }
        }
    }

    private static class StdoutListener implements Shell.OnCommandLineListener {
        private InstallCallback callback;

        public StdoutListener(InstallCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onLine(String line) {
            callback.onLine(line);
        }

        @Override
        public void onCommandResult(int commandCode, int exitCode) {
            if (exitCode == InstallCallback.OK) {
                callback.onDone();
            } else {
                triggerError(callback, exitCode);
            }
        }
    }

    private static class StderrListener implements Shell.OnCommandLineListener {
        private InstallCallback callback;

        public StderrListener(InstallCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onLine(String line) {
            callback.onErrorLine(line);
        }

        @Override
        public void onCommandResult(int commandCode, int exitCode) {
            // Not called for STDERR listener.
        }
    }

    private InstallDirect() {}
}
