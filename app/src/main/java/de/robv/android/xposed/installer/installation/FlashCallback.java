package de.robv.android.xposed.installer.installation;

import de.robv.android.xposed.installer.util.RootUtil;
import eu.chainfire.libsuperuser.Shell;

public interface FlashCallback extends RootUtil.LineCallback {
    void onStarted();
    void onDone();
    void onError(int exitCode, String error);

    int OK = 0;
    int ERROR_GENERIC = 1;

    // SU errors
    int ERROR_TIMEOUT = Shell.OnCommandResultListener.WATCHDOG_EXIT;
    int ERROR_SHELL_DIED = Shell.OnCommandResultListener.SHELL_DIED;
    int ERROR_NO_ROOT_ACCESS = Shell.OnCommandResultListener.SHELL_EXEC_FAILED;

    // ZIP errors
    int ERROR_INVALID_ZIP = -100;
    int ERROR_NOT_FLASHABLE_IN_APP = -101;
    int ERROR_INSTALLER_NEEDS_UPDATE = -102;
}
