package de.robv.android.xposed.installer.advanced;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.AssetUtil;
import de.robv.android.xposed.installer.util.DownloadsUtil;
import de.robv.android.xposed.installer.util.RootUtil;
import de.robv.android.xposed.installer.util.XposedZip;

import static de.robv.android.xposed.installer.XposedApp.WRITE_EXTERNAL_PERMISSION;

public abstract class BaseAdvancedInstaller extends Fragment implements DownloadsUtil.DownloadFinishedCallback {

    public static final String JAR_PATH = XposedApp.BASE_DIR + "bin/XposedBridge.jar";
    private static final int INSTALL_MODE_NORMAL = 0;
    private static final int INSTALL_MODE_RECOVERY_AUTO = 1;
    private static final int INSTALL_MODE_RECOVERY_MANUAL = 2;
    private RootUtil mRootUtil = new RootUtil();
    private Button mClickedButton;
    private List<String> messages;
    public static String APP_PROCESS_NAME = null;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.single_installer_view, container, false);

        final Spinner chooserInstallers = (Spinner) view.findViewById(R.id.chooserInstallers);
        final Spinner chooserUninstallers = (Spinner) view.findViewById(R.id.chooserUninstallers);
        final Button btnInstall = (Button) view.findViewById(R.id.btnInstall);
        final Button btnUninstall = (Button) view.findViewById(R.id.btnUninstall);
        ImageView infoInstaller = (ImageView) view.findViewById(R.id.infoInstaller);
        ImageView infoUninstaller = (ImageView) view.findViewById(R.id.infoUninstaller);
        TextView compatibleTv = (TextView) view.findViewById(R.id.compatibilityTv);
        TextView incompatibleTv = (TextView) view.findViewById(R.id.incompatibilityTv);
        TextView author = (TextView) view.findViewById(R.id.author);

        chooserInstallers.setAdapter(new XposedZip.MyAdapter<>(getContext(), installers()));
        chooserUninstallers.setAdapter(new XposedZip.MyAdapter<>(getContext(), uninstallers()));

        infoInstaller.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                XposedZip.Installer selectedInstaller = (XposedZip.Installer) chooserInstallers.getSelectedItem();
                String s = getString(R.string.infoInstaller,
                        selectedInstaller.name, selectedInstaller.sdk,
                        selectedInstaller.architecture,
                        selectedInstaller.version);

                new MaterialDialog.Builder(getContext()).title(R.string.info)
                        .content(s).positiveText(android.R.string.ok).show();
            }
        });
        infoUninstaller.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                XposedZip.Uninstaller selectedUninstaller = (XposedZip.Uninstaller) chooserUninstallers.getSelectedItem();
                String s = getString(R.string.infoUninstaller,
                        selectedUninstaller.name,
                        selectedUninstaller.architecture,
                        selectedUninstaller.date);

                new MaterialDialog.Builder(getContext()).title(R.string.info)
                        .content(s).positiveText(android.R.string.ok).show();
            }
        });

        btnInstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mClickedButton = btnInstall;
                if (checkPermissions())
                    return;

                areYouSure(R.string.warningArchitecture,
                        new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                super.onPositive(dialog);

                                XposedZip.Installer selectedInstaller = (XposedZip.Installer) chooserInstallers.getSelectedItem();

                                DownloadsUtil.add(getContext(), selectedInstaller.name, selectedInstaller.link, BaseAdvancedInstaller.this,
                                        DownloadsUtil.MIME_TYPES.ZIP, true);
                            }
                        });
            }
        });

        btnUninstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mClickedButton = btnUninstall;
                if (checkPermissions())
                    return;

                areYouSure(R.string.warningArchitecture,
                        new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                super.onPositive(dialog);

                                XposedZip.Uninstaller selectedInstaller = (XposedZip.Uninstaller) chooserUninstallers.getSelectedItem();

                                DownloadsUtil.add(getContext(), selectedInstaller.name, selectedInstaller.link, BaseAdvancedInstaller.this,
                                        DownloadsUtil.MIME_TYPES.ZIP, true);
                            }
                        });
            }
        });


        compatibleTv.setText(compatibility());
        incompatibleTv.setText(incompatibility());
        author.setText(getString(R.string.author, author()));

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRootUtil.dispose();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_EXTERNAL_PERMISSION) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mClickedButton != null) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mClickedButton.performClick();
                        }
                    }, 500);
                }
            } else {
                Toast.makeText(getActivity(), R.string.permissionNotGranted, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onDownloadFinished(final Context context, final DownloadsUtil.DownloadInfo info) {
        messages.clear();
        Toast.makeText(context, getString(R.string.downloadZipOk, info.localFilename), Toast.LENGTH_LONG).show();

        if (getInstallMode() == INSTALL_MODE_RECOVERY_MANUAL)
            return;

        areYouSure(R.string.install_warning, new MaterialDialog.ButtonCallback() {
            @Override
            public void onPositive(MaterialDialog dialog) {
                super.onPositive(dialog);

                if (!startShell()) return;

                prepareAutoFlash(messages, new File(info.localFilename));
                offerRebootToRecovery(messages, info.title, INSTALL_MODE_RECOVERY_AUTO);
            }
        });
    }

    private void areYouSure(int contentTextId, MaterialDialog.ButtonCallback yesHandler) {
        new MaterialDialog.Builder(getActivity()).title(R.string.areyousure)
                .content(contentTextId)
                .iconAttr(android.R.attr.alertDialogIcon)
                .positiveText(android.R.string.yes)
                .negativeText(android.R.string.no).callback(yesHandler).show();
    }

    private boolean startShell() {
        if (mRootUtil.startShell())
            return true;

        showAlert(getString(R.string.root_failed));
        return false;
    }

    private void showAlert(final String result) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showAlert(result);
                }
            });
            return;
        }

        MaterialDialog dialog = new MaterialDialog.Builder(getActivity()).content(result).positiveText(android.R.string.ok).build();
        dialog.show();

        TextView txtMessage = (TextView) dialog
                .findViewById(android.R.id.message);
        try {
            txtMessage.setTextSize(14);
        } catch (NullPointerException ignored) {
        }
    }

    private int getInstallMode() {
        int mode = XposedApp.getPreferences().getInt("install_mode", INSTALL_MODE_NORMAL);
        if (mode < INSTALL_MODE_NORMAL || mode > INSTALL_MODE_RECOVERY_MANUAL)
            mode = INSTALL_MODE_NORMAL;
        return mode;
    }

    private void showConfirmDialog(final String message, final MaterialDialog.ButtonCallback callback) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showConfirmDialog(message, callback);
                }
            });
            return;
        }

        new MaterialDialog.Builder(getActivity())
                .content(message).positiveText(android.R.string.yes)
                .negativeText(android.R.string.no).callback(callback).show();
    }


    private boolean prepareAutoFlash(List<String> messages, File file) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            File appProcessFile = AssetUtil.writeAssetToFile(APP_PROCESS_NAME, new File(XposedApp.BASE_DIR + "bin/app_process"), 00700);
            if (appProcessFile == null) {
                showAlert(getString(R.string.file_extract_failed, "app_process"));
                return false;
            }

            messages.add(getString(R.string.file_copying, "XposedBridge.jar"));
            File jarFile = AssetUtil.writeAssetToFile("XposedBridge.jar", new File(JAR_PATH), 00644);
            if (jarFile == null) {
                messages.add("");
                messages.add(getString(R.string.file_extract_failed, "XposedBridge.jar"));
                return false;
            }

            mRootUtil.executeWithBusybox("sync", messages);
        }

        if (mRootUtil.execute("ls /cache/recovery", null) != 0) {
            messages.add(getString(R.string.file_creating_directory, "/cache/recovery"));
            if (mRootUtil.executeWithBusybox("mkdir /cache/recovery",
                    messages) != 0) {
                messages.add("");
                messages.add(getString(R.string.file_create_directory_failed, "/cache/recovery"));
                return false;
            }
        }

        messages.add(getString(R.string.file_copying, file));

        if (mRootUtil.executeWithBusybox("cp -a " + file.getAbsolutePath() + " /cache/recovery/", messages) != 0) {
            messages.add("");
            messages.add(getString(R.string.file_copy_failed, file, "/cache"));
            return false;
        }

        messages.add(getString(R.string.file_writing_recovery_command));
        if (mRootUtil.execute("echo --update_package=/cache/recovery/" + file.getName() + " > /cache/recovery/command", messages) != 0) {
            messages.add("");
            messages.add(getString(R.string.file_writing_recovery_command_failed));
            return false;
        }

        return true;
    }

    private void offerRebootToRecovery(List<String> messages, final String file, final int installMode) {
        if (installMode == INSTALL_MODE_RECOVERY_AUTO)
            messages.add(getString(R.string.auto_flash_note, file));
        else
            messages.add(getString(R.string.manual_flash_note, file));

        messages.add("");
        messages.add(getString(R.string.reboot_recovery_confirmation));
        showConfirmDialog(TextUtils.join("\n", messages).trim(),
                new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        super.onPositive(dialog);
                        reboot("recovery");
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        super.onNegative(dialog);
                        if (installMode == INSTALL_MODE_RECOVERY_AUTO) {
                            // clean up to avoid unwanted flashing
                            mRootUtil.executeWithBusybox("rm /cache/recovery/command", null);
                            mRootUtil.executeWithBusybox("rm /cache/recovery/" + file, null);
                            AssetUtil.removeBusybox();
                        }
                    }
                }

        );
    }

    private void reboot(String mode) {
        if (!startShell())
            return;

        List<String> messages = new LinkedList<>();

        String command = "reboot";
        if (mode != null) {
            command += " " + mode;
            if (mode.equals("recovery"))
                // create a flag used by some kernels to boot into recovery
                mRootUtil.executeWithBusybox("touch /cache/recovery/boot", messages);
        }

        if (mRootUtil.executeWithBusybox(command, messages) != 0) {
            messages.add("");
            messages.add(getString(R.string.reboot_failed));
            showAlert(TextUtils.join("\n", messages).trim());
        }
        AssetUtil.removeBusybox();
    }

    private boolean checkPermissions() {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_PERMISSION);
            return true;
        }
        return false;
    }

    protected abstract List<XposedZip.Installer> installers();

    protected abstract List<XposedZip.Uninstaller> uninstallers();

    @StringRes
    protected abstract int compatibility();

    @StringRes
    protected abstract int incompatibility();

    protected abstract CharSequence author();
}