package de.robv.android.xposed.installer.installation;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.util.FrameworkZips;
import de.robv.android.xposed.installer.util.InstallZipUtil.ZipCheckResult;
import de.robv.android.xposed.installer.util.RootUtil;

import static de.robv.android.xposed.installer.util.InstallZipUtil.closeSilently;

public class FlashRecoveryAuto extends Flashable {
    public FlashRecoveryAuto(File zipPath, FrameworkZips.Type type, String title) {
        super(zipPath, type, title);
    }

    @Override
    public void flash(Context context, FlashCallback callback) {
        ZipCheckResult zipCheck = openAndCheckZip(callback);
        if (zipCheck == null) {
            return;
        } else {
            closeSilently(zipCheck.getZip());
        }

        final String zipName = mZipPath.getName();
        String cmd;

        // Execute the flash commands.
        RootUtil rootUtil = new RootUtil();
        if (!rootUtil.startShell(callback)) {
            return;
        }

        callback.onStarted();

        // Make sure /cache/recovery/ exists.
        if (rootUtil.execute("ls /cache/recovery", null) != 0) {
            callback.onLine(context.getString(R.string.file_creating_directory, "/cache/recovery"));
            if (rootUtil.executeWithBusybox("mkdir /cache/recovery", callback) != 0) {
                callback.onError(FlashCallback.ERROR_GENERIC,
                        context.getString(R.string.file_create_directory_failed, "/cache/recovery"));
                return;
            }
        }

        // Copy the ZIP to /cache/recovery/.
        callback.onLine(context.getString(R.string.file_copying, zipName));
        cmd = "cp -a " + RootUtil.getShellPath(mZipPath) + " /cache/recovery/" + zipName;
        if (rootUtil.executeWithBusybox(cmd, callback) != 0) {
            callback.onError(FlashCallback.ERROR_GENERIC,
                    context.getString(R.string.file_copy_failed, zipName, "/cache/recovery"));
            return;
        }

        // Write the flashing command to /cache/recovery/command.
        callback.onLine(context.getString(R.string.file_writing_recovery_command));
        cmd = "echo --update_package=/cache/recovery/" + zipName + " > /cache/recovery/command";
        if (rootUtil.execute(cmd, callback) != 0) {
            callback.onError(FlashCallback.ERROR_GENERIC,
                    context.getString(R.string.file_writing_recovery_command_failed));
            return;
        }

        callback.onLine(context.getString(R.string.auto_flash_note, zipName));
        callback.onDone();
    }

    @Override
    public RootUtil.RebootMode getRebootMode() {
        return RootUtil.RebootMode.RECOVERY;
    }

    public static final Parcelable.Creator<FlashRecoveryAuto> CREATOR
            = new Parcelable.Creator<FlashRecoveryAuto>() {
        @Override
        public FlashRecoveryAuto createFromParcel(Parcel in) {
            return new FlashRecoveryAuto(in);
        }

        @Override
        public FlashRecoveryAuto[] newArray(int size) {
            return new FlashRecoveryAuto[size];
        }
    };

    protected FlashRecoveryAuto(Parcel in) {
        super(in);
    }
}
