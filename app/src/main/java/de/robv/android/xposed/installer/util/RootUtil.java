package de.robv.android.xposed.installer.util;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.installation.FlashCallback;
import eu.chainfire.libsuperuser.Shell;
import eu.chainfire.libsuperuser.Shell.OnCommandResultListener;

import static de.robv.android.xposed.installer.util.InstallZipUtil.triggerError;

public class RootUtil {
    private Shell.Interactive mShell = null;
    private HandlerThread mCallbackThread = null;

    private boolean mCommandRunning = false;
    private int mLastExitCode = -1;
    private LineCallback mCallback = null;

    private static final String EMULATED_STORAGE_SOURCE;
    private static final String EMULATED_STORAGE_TARGET;

    static {
        EMULATED_STORAGE_SOURCE = getEmulatedStorageVariable("EMULATED_STORAGE_SOURCE");
        EMULATED_STORAGE_TARGET = getEmulatedStorageVariable("EMULATED_STORAGE_TARGET");
    }

    public interface LineCallback {
        void onLine(String line);
        void onErrorLine(String line);
    }

    public static class CollectingLineCallback implements LineCallback {
        protected List<String> mLines = new LinkedList<>();

        @Override
        public void onLine(String line) {
            mLines.add(line);
        }

        @Override
        public void onErrorLine(String line) {
            mLines.add(line);
        }

        @Override
        public String toString() {
            return TextUtils.join("\n", mLines);
        }
    }

    public static class LogLineCallback implements LineCallback {
        @Override
        public void onLine(String line) {
            Log.i(XposedApp.TAG, line);
        }

        @Override
        public void onErrorLine(String line) {
            Log.e(XposedApp.TAG, line);
        }
    }

    private static String getEmulatedStorageVariable(String variable) {
        String result = System.getenv(variable);
        if (result != null) {
            result = getCanonicalPath(new File(result));
            if (!result.endsWith("/")) {
                result += "/";
            }
        }
        return result;
    }


    private final Shell.OnCommandResultListener mOpenListener = new Shell.OnCommandResultListener() {
        @Override
        public void onCommandResult(int commandCode, int exitCode, List<String> output) {
            mStdoutListener.onCommandResult(commandCode, exitCode);
        }
    };

    private final Shell.OnCommandLineListener mStdoutListener = new Shell.OnCommandLineListener() {
        public void onLine(String line) {
            if (mCallback != null) {
                mCallback.onLine(line);
            }
        }

        @Override
        public void onCommandResult(int commandCode, int exitCode) {
            mLastExitCode = exitCode;
            synchronized (mCallbackThread) {
                mCommandRunning = false;
                mCallbackThread.notifyAll();
            }
        }
    };

    private final Shell.OnCommandLineListener mStderrListener = new Shell.OnCommandLineListener() {
        @Override
        public void onLine(String line) {
            if (mCallback != null) {
                mCallback.onErrorLine(line);
            }
        }

        @Override
        public void onCommandResult(int commandCode, int exitCode) {
            // Not called for STDERR listener.
        }
    };

    private void waitForCommandFinished() {
        synchronized (mCallbackThread) {
            while (mCommandRunning) {
                try {
                    mCallbackThread.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }

        if (mLastExitCode == OnCommandResultListener.WATCHDOG_EXIT || mLastExitCode == OnCommandResultListener.SHELL_DIED) {
            dispose();
        }
    }

    /**
     * Starts an interactive shell with root permissions. Does nothing if
     * already running.
     *
     * @return true if root access is available, false otherwise
     */
    public synchronized boolean startShell() {
        if (mShell != null) {
            if (mShell.isRunning()) {
                return true;
            } else {
                dispose();
            }
        }

        mCallbackThread = new HandlerThread("su callback listener");
        mCallbackThread.start();

        mCommandRunning = true;
        mShell = new Shell.Builder().useSU()
                .setHandler(new Handler(mCallbackThread.getLooper()))
                .setOnSTDERRLineListener(mStderrListener)
                .open(mOpenListener);

        waitForCommandFinished();

        if (mLastExitCode != OnCommandResultListener.SHELL_RUNNING) {
            dispose();
            return false;
        }

        return true;
    }

    public boolean startShell(FlashCallback flashCallback) {
        if (!startShell()) {
            triggerError(flashCallback, FlashCallback.ERROR_NO_ROOT_ACCESS);
            return false;
        }
        return true;
    }

    /**
     * Closes all resources related to the shell.
     */
    public synchronized void dispose() {
        if (mShell == null) {
            return;
        }

        try {
            mShell.close();
        } catch (Exception ignored) {
        }
        mShell = null;

        mCallbackThread.quit();
        mCallbackThread = null;
    }

    public synchronized int execute(String command, LineCallback callback) {
        if (mShell == null) {
            throw new IllegalStateException("shell is not running");
        }

        mCallback = callback;
        mCommandRunning = true;
        mShell.addCommand(command, 0, mStdoutListener);
        waitForCommandFinished();

        return mLastExitCode;
    }

    public int executeWithBusybox(String command, LineCallback callback) {
        AssetUtil.extractBusybox();
        return execute(AssetUtil.BUSYBOX_FILE.getAbsolutePath() + " " + command, callback);
    }

    private static String getCanonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            Log.w(XposedApp.TAG, "Could not get canonical path for " + file);
            return file.getAbsolutePath();
        }
    }

    public static String getShellPath(File file) {
        return getShellPath(getCanonicalPath(file));
    }

    public static String getShellPath(String path) {
        if (EMULATED_STORAGE_SOURCE != null && EMULATED_STORAGE_TARGET != null
                && path.startsWith(EMULATED_STORAGE_TARGET)) {
            path = EMULATED_STORAGE_SOURCE + path.substring(EMULATED_STORAGE_TARGET.length());
        }
        return path;
    }

    @Override
    protected void finalize() throws Throwable {
        dispose();
    }

    public enum RebootMode {
        NORMAL(R.string.reboot),
        SOFT(R.string.soft_reboot),
        RECOVERY(R.string.reboot_recovery);

        public final int titleRes;

        RebootMode(@StringRes int titleRes) {
            this.titleRes = titleRes;
        }

        public static RebootMode fromId(@IdRes int id) {
            switch (id) {
                case R.id.reboot:
                    return NORMAL;
                case R.id.soft_reboot:
                    return SOFT;
                case R.id.reboot_recovery:
                    return RECOVERY;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }

    public static boolean reboot(RebootMode mode, @NonNull Context context) {
        RootUtil rootUtil = new RootUtil();
        if (!rootUtil.startShell()) {
            NavUtil.showMessage(context, context.getString(R.string.root_failed));
            return false;
        }

        LineCallback callback = new CollectingLineCallback();
        if (!rootUtil.reboot(mode, callback)) {
            StringBuilder message = new StringBuilder(callback.toString());
            if (message.length() > 0) {
                message.append("\n\n");
            }
            message.append(context.getString(R.string.reboot_failed));
            NavUtil.showMessage(context, message);
            return false;
        }

        return true;
    }

    public boolean reboot(RebootMode mode, LineCallback callback) {
        switch (mode) {
            case NORMAL:
                return reboot(callback);
            case SOFT:
                return softReboot(callback);
            case RECOVERY:
                return rebootToRecovery(callback);
            default:
                throw new IllegalArgumentException();
        }
    }

    private boolean reboot(LineCallback callback) {
        return executeWithBusybox("reboot", callback) == 0;
    }

    private boolean softReboot(LineCallback callback) {
        return execute("setprop ctl.restart surfaceflinger; setprop ctl.restart zygote", callback) == 0;
    }

    private boolean rebootToRecovery(LineCallback callback) {
        // Create a flag used by some kernels to boot into recovery.
        if (execute("ls /cache/recovery", null) != 0) {
            executeWithBusybox("mkdir /cache/recovery", callback);
        }
        executeWithBusybox("touch /cache/recovery/boot", callback);

        return executeWithBusybox("reboot recovery", callback) == 0;
    }
}
