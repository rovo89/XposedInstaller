package de.robv.android.xposed.installer.installation;

import eu.chainfire.libsuperuser.Shell;

public interface InstallCallback {
    void onStarted();
    void onLine(String line);
    void onErrorLine(String line);
    void onDone();
    void onError(int exitCode, String error);

    int OK = 0;

    // SU errors
    int ERROR_TIMEOUT = Shell.OnCommandResultListener.WATCHDOG_EXIT;
    int ERROR_SHELL_DIED = Shell.OnCommandResultListener.SHELL_DIED;
    int ERROR_EXEC_FAILED = Shell.OnCommandResultListener.SHELL_EXEC_FAILED;
    int ERROR_WRONG_UID = Shell.OnCommandResultListener.SHELL_WRONG_UID;

    // ZIP errors
    int ERROR_INVALID_ZIP = -100;
    int ERROR_NOT_FLASHABLE_IN_APP = -101;
}
