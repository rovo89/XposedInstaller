package de.robv.android.xposed.installer.util;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.List;

import de.robv.android.xposed.installer.XposedApp;
import eu.chainfire.libsuperuser.Shell;
import eu.chainfire.libsuperuser.Shell.OnCommandResultListener;

public class RootUtil {
    private Shell.Interactive mShell = null;
    private HandlerThread mCallbackThread = null;

    private boolean mCommandRunning = false;
    private int mLastExitCode = -1;
    private List<String> mLastOutput = null;

    private static final String EMULATED_STORAGE_SOURCE;
    private static final String EMULATED_STORAGE_TARGET;

    static {
        EMULATED_STORAGE_SOURCE = getEmulatedStorageVariable("EMULATED_STORAGE_SOURCE");
        EMULATED_STORAGE_TARGET = getEmulatedStorageVariable("EMULATED_STORAGE_TARGET");
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

    private OnCommandResultListener commandResultListener = new OnCommandResultListener() {
        @Override
        public void onCommandResult(int commandCode, int exitCode, List<String> output) {
            mLastExitCode = exitCode;
            mLastOutput = output;
            synchronized (mCallbackThread) {
                mCommandRunning = false;
                mCallbackThread.notifyAll();
            }
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

        if (mLastExitCode == OnCommandResultListener.WATCHDOG_EXIT || mLastExitCode == OnCommandResultListener.SHELL_DIED)
            dispose();
    }

    /**
     * Starts an interactive shell with root permissions. Does nothing if
     * already running.
     *
     * @return true if root access is available, false otherwise
     */
    public synchronized boolean startShell() {
        if (mShell != null) {
            if (mShell.isRunning())
                return true;
            else
                dispose();
        }

        mCallbackThread = new HandlerThread("su callback listener");
        mCallbackThread.start();

        mCommandRunning = true;
        mShell = new Shell.Builder().useSU()
                .setHandler(new Handler(mCallbackThread.getLooper()))
                .setWantSTDERR(true).setWatchdogTimeout(10)
                .open(commandResultListener);

        waitForCommandFinished();

        if (mLastExitCode != OnCommandResultListener.SHELL_RUNNING) {
            dispose();
            return false;
        }

        return true;
    }

    /**
     * Closes all resources related to the shell.
     */
    public synchronized void dispose() {
        if (mShell == null)
            return;

        try {
            mShell.close();
        } catch (Exception ignored) {
        }
        mShell = null;

        mCallbackThread.quit();
        mCallbackThread = null;
    }

    /**
     * Executes a single command, waits for its termination and returns the
     * result
     */
    public synchronized int execute(String command, List<String> output) {
        if (mShell == null)
            startShell();

        mCommandRunning = true;
        mShell.addCommand(command, 0, commandResultListener);
        waitForCommandFinished();

        if (output != null && mLastOutput.size() != 0 && !mLastOutput.get(0).contains("WARNING"))
            output.addAll(mLastOutput);

        return mLastExitCode;
    }

    /**
     * Executes a single command via the bundled BusyBox executable
     */
    public int executeWithBusybox(String command, List<String> output) {
        AssetUtil.extractBusybox();
        return execute(AssetUtil.BUSYBOX_FILE.getAbsolutePath() + " " + command, output);
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
}
