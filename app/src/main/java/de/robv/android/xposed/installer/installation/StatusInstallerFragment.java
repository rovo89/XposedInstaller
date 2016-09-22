package de.robv.android.xposed.installer.installation;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.MaterialDialog.Builder;
import com.afollestad.materialdialogs.simplelist.MaterialSimpleListAdapter;
import com.afollestad.materialdialogs.simplelist.MaterialSimpleListItem;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.AssetUtil;
import de.robv.android.xposed.installer.util.DownloadsUtil;
import de.robv.android.xposed.installer.util.DownloadsUtil.DownloadFinishedCallback;
import de.robv.android.xposed.installer.util.DownloadsUtil.DownloadInfo;
import de.robv.android.xposed.installer.util.FrameworkZips;
import de.robv.android.xposed.installer.util.FrameworkZips.FrameworkZip;
import de.robv.android.xposed.installer.util.FrameworkZips.LocalFrameworkZip;
import de.robv.android.xposed.installer.util.FrameworkZips.OnlineFrameworkZip;
import de.robv.android.xposed.installer.util.NavUtil;
import de.robv.android.xposed.installer.util.RootUtil;
import de.robv.android.xposed.installer.util.RunnableWithParam;

public class StatusInstallerFragment extends Fragment {
    public static final File DISABLE_FILE = new File(XposedApp.BASE_DIR + "conf/disabled");

    private static ImageView mErrorIcon;
    private static TextView mErrorTv;
    private static TextView txtKnownIssue;
    private static SwitchCompat xposedDisable;
    private boolean mShowOutdated = false;
    private RootUtil mRootUtil = new RootUtil();

    public void setError(boolean connectionFailed, boolean noSdks) {
        if (!connectionFailed && !noSdks) return;

        mErrorTv.setVisibility(View.VISIBLE);
        mErrorIcon.setVisibility(View.VISIBLE);
        if (noSdks) {
            mErrorIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_warning_grey));
            mErrorTv.setText(String.format(getString(R.string.phone_not_compatible), Build.VERSION.SDK_INT, Build.CPU_ABI));
        }
        if (connectionFailed) {
            mErrorIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_no_connection));
            mErrorTv.setText(getString(R.string.loadingError));
        }
    }

    private static boolean checkClassExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static int extractIntPart(String str) {
        int result = 0, length = str.length();
        for (int offset = 0; offset < length; offset++) {
            char c = str.charAt(offset);
            if ('0' <= c && c <= '9')
                result = result * 10 + (c - '0');
            else
                break;
        }
        return result;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.status_installer, container, false);

        triggerRefresh(true, true);

        mErrorIcon = (ImageView) v.findViewById(R.id.errorIcon);
        mErrorTv = (TextView) v.findViewById(R.id.errorTv);

        txtKnownIssue = (TextView) v.findViewById(R.id.framework_known_issue);

        TextView txtInstallError = (TextView) v.findViewById(R.id.framework_install_errors);
        View txtInstallContainer = v.findViewById(R.id.status_container);
        ImageView txtInstallIcon = (ImageView) v.findViewById(R.id.status_icon);

        String installedXposedVersion = XposedApp.getXposedProp().get("version");
        View disableView = v.findViewById(R.id.disableView);
        xposedDisable = (SwitchCompat) v.findViewById(R.id.disableSwitch);

        TextView androidSdk = (TextView) v.findViewById(R.id.android_version);
        TextView manufacturer = (TextView) v.findViewById(R.id.ic_manufacturer);
        TextView cpu = (TextView) v.findViewById(R.id.cpu);

        if (Build.VERSION.SDK_INT >= 21) {
            if (installedXposedVersion != null) {
                int installedXposedVersionInt = extractIntPart(installedXposedVersion);
                if (installedXposedVersionInt == XposedApp.getXposedVersion()) {
                    txtInstallError.setText(getString(R.string.installed_lollipop, installedXposedVersion));
                    txtInstallError.setTextColor(getResources().getColor(R.color.darker_green));
                    txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.darker_green));
                    txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_check_circle));
                } else {
                    txtInstallError.setText(getString(R.string.installed_lollipop_inactive, installedXposedVersion));
                    txtInstallError.setTextColor(getResources().getColor(R.color.amber_500));
                    txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.amber_500));
                    txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_warning));
                }
            } else {
                txtInstallError.setText(R.string.not_installed_no_lollipop);
                txtInstallError.setTextColor(getResources().getColor(R.color.warning));
                txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.warning));
                txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_error));
                xposedDisable.setVisibility(View.GONE);
                disableView.setVisibility(View.GONE);
            }
        } else {
            int installedXposedVersionInt = XposedApp.getXposedVersion();
            if (installedXposedVersionInt != 0) {
                txtInstallError.setText(getString(R.string.installed_lollipop, installedXposedVersionInt));
                txtInstallError.setTextColor(getResources().getColor(R.color.darker_green));
                txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.darker_green));
                txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_check_circle));
                if (DISABLE_FILE.exists()) {
                    txtInstallError.setText(getString(R.string.installed_lollipop_inactive, installedXposedVersionInt));
                    txtInstallError.setTextColor(getResources().getColor(R.color.amber_500));
                    txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.amber_500));
                    txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_warning));
                }
            } else {
                txtInstallError.setText(getString(R.string.not_installed_no_lollipop));
                txtInstallError.setTextColor(getResources().getColor(R.color.warning));
                txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.warning));
                txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_error));
                xposedDisable.setVisibility(View.GONE);
                disableView.setVisibility(View.GONE);
            }
        }

        xposedDisable.setChecked(!DISABLE_FILE.exists());

        xposedDisable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (DISABLE_FILE.exists()) {
                    DISABLE_FILE.delete();
                    Snackbar.make(xposedDisable, R.string.xposed_on_next_reboot, Snackbar.LENGTH_LONG).show();
                } else {
                    try {
                        DISABLE_FILE.createNewFile();
                        Snackbar.make(xposedDisable, R.string.xposed_off_next_reboot, Snackbar.LENGTH_LONG).show();
                    } catch (IOException e) {
                        Log.e(XposedApp.TAG, "StatusInstallerFragment -> " + e.getMessage());
                    }
                }
            }
        });

        androidSdk.setText(getString(R.string.android_sdk, getAndroidVersion(), Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
        manufacturer.setText(getUIFramework());
        cpu.setText(FrameworkZips.ARCH);

        refreshKnownIssue();

        if (!XposedApp.getPreferences().getBoolean("hide_install_warning", false)) {
            final View dontShowAgainView = inflater.inflate(R.layout.dialog_install_warning, null);

            new MaterialDialog.Builder(getActivity())
                    .title(R.string.install_warning_title)
                    .customView(dontShowAgainView, false)
                    .positiveText(android.R.string.ok)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            super.onPositive(dialog);
                            CheckBox checkBox = (CheckBox) dontShowAgainView.findViewById(android.R.id.checkbox);
                            if (checkBox.isChecked())
                                XposedApp.getPreferences().edit().putBoolean("hide_install_warning", true).apply();
                        }
                    }).cancelable(false).show();
        }

        return v;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mShowOutdated = XposedApp.getPreferences().getBoolean("framework_download_show_outdated", false);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_installer, menu);
        menu.findItem(R.id.show_outdated).setChecked(mShowOutdated);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.reboot:
                areYouSure(R.string.reboot, new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        super.onPositive(dialog);
                        reboot(null);
                    }
                });
                return true;

            case R.id.soft_reboot:
                areYouSure(R.string.soft_reboot, new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        super.onPositive(dialog);
                        softReboot();
                    }
                });
                return true;

            case R.id.reboot_recovery:
                areYouSure(R.string.reboot_recovery, new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        super.onPositive(dialog);
                        reboot("recovery");
                    }
                });
                return true;

            case R.id.show_outdated:
                mShowOutdated = !item.isChecked();
                XposedApp.getPreferences().edit().putBoolean("framework_download_show_outdated", mShowOutdated).apply();
                item.setChecked(mShowOutdated);
                refreshZipViews();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void areYouSure(int contentTextId, MaterialDialog.ButtonCallback yesHandler) {
        new MaterialDialog.Builder(getActivity()).title(R.string.areyousure)
                .content(contentTextId)
                .iconAttr(android.R.attr.alertDialogIcon)
                .positiveText(android.R.string.yes)
                .negativeText(android.R.string.no).callback(yesHandler).show();
    }

    @SuppressLint("StringFormatInvalid")
    private void refreshKnownIssue() {
        String issueName = null;
        String issueLink = null;

        if (new File("/system/framework/core.jar.jex").exists()) {
            issueName = "Aliyun OS";
            issueLink = "http://forum.xda-developers.com/showpost.php?p=52289793&postcount=5";

        } else if (new File("/data/miui/DexspyInstaller.jar").exists() || checkClassExists("miui.dexspy.DexspyInstaller")) {
            issueName = "MIUI/Dexspy";
            issueLink = "http://forum.xda-developers.com/showpost.php?p=52291098&postcount=6";

        } else if (checkClassExists("com.huawei.android.content.res.ResourcesEx")
                || checkClassExists("android.content.res.NubiaResources")) {
            issueName = "Resources subclass";
            issueLink = "http://forum.xda-developers.com/showpost.php?p=52801382&postcount=8";
        }

        if (issueName != null) {
            final String issueLinkFinal = issueLink;
            txtKnownIssue.setText(getString(R.string.install_known_issue, issueName));
            txtKnownIssue.setVisibility(View.VISIBLE);
            txtKnownIssue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NavUtil.startURL(getActivity(), issueLinkFinal);
                }
            });
        } else {
            txtKnownIssue.setVisibility(View.GONE);
        }
    }

    private String getAndroidVersion() {
        switch (Build.VERSION.SDK_INT) {
            case 16:
            case 17:
            case 18:
                return "Jelly Bean";
            case 19:
                return "KitKat";
            case 21:
            case 22:
                return "Lollipop";
            case 23:
                return "Marshmallow";
            case 24:
                return "Nougat";
        }
        return "";
    }

    private String getUIFramework() {
        String manufacturer = Character.toUpperCase(Build.MANUFACTURER.charAt(0)) + Build.MANUFACTURER.substring(1);
        if (!Build.BRAND.equals(Build.MANUFACTURER)) {
            manufacturer += " " + Character.toUpperCase(Build.BRAND.charAt(0)) + Build.BRAND.substring(1);
        }
        manufacturer += " " + Build.MODEL + " ";
        if (manufacturer.contains("Samsung")) {
            manufacturer += new File("/system/framework/twframework.jar").exists() ? "(TouchWiz)" : "(AOSP-based ROM)";
        } else if (manufacturer.contains("Xioami")) {
            manufacturer += new File("/system/framework/framework-miui-res.apk").exists() ? "(MIUI)" : "(AOSP-based ROM)";
        }
        return manufacturer;
    }


    private void triggerRefresh(final boolean online, final boolean local) {
        new Thread("FrameworkZipsRefresh") {
            @Override
            public void run() {
                if (online) {
                    XposedApp.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading();
                        }
                    });
                    FrameworkZips.refreshOnline();
                }
                if (local) {
                    FrameworkZips.refreshLocal();
                }
                XposedApp.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshZipViews();
                    }
                });
            }
        }.start();
    }

    @UiThread
    private void showLoading() {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        LinearLayout zips = (LinearLayout) getView().findViewById(R.id.zips);

        // TODO add a proper layout, spinner or something like that
        TextView tv = new TextView(getActivity());
        tv.setText("loading...");
        tv.setTextSize(20);

        zips.removeAllViews();
        zips.addView(tv);
    }

    @UiThread
    private void refreshZipViews() {
        LinearLayout zips = (LinearLayout) getView().findViewById(R.id.zips);
        zips.removeAllViews();
        synchronized (FrameworkZip.class) {
            // TODO handle "no ZIPs" case
            for (FrameworkZips.Type type : FrameworkZips.Type.values()) {
                addZipViews(getActivity().getLayoutInflater(), zips, type);
            }
        }
    }

    private void addZipViews(LayoutInflater inflater, ViewGroup root, FrameworkZips.Type type) {
        ViewGroup container = null;
        Set<String> allTitles = FrameworkZips.getAllTitles(type);
        for (String title : allTitles) {
            OnlineFrameworkZip online = FrameworkZips.getOnline(title, type);
            LocalFrameworkZip local = FrameworkZips.getLocal(title, type);

            boolean hasOnline = (online != null);
            boolean hasLocal = (local != null);
            FrameworkZip zip = hasOnline ? online : local;
            boolean isOutdated = zip.isOutdated();

            if (isOutdated && !mShowOutdated) {
                continue;
            }

            if (container == null) {
                View card = inflater.inflate(R.layout.framework_zip_group, root, false);
                TextView tv = (TextView) card.findViewById(android.R.id.title);
                tv.setText(type.title);
                tv.setBackgroundResource(type.color);
                container = (ViewGroup) card.findViewById(android.R.id.content);
                root.addView(card);
            }

            addZipView(inflater, container, zip, hasOnline, hasLocal, isOutdated);
        }
    }

    public void addZipView(LayoutInflater inflater, ViewGroup container, final FrameworkZip zip,
                           boolean hasOnline, boolean hasLocal, boolean isOutdated) {
        View view = inflater.inflate(R.layout.framework_zip_item, container, false);

        TextView tvTitle = (TextView) view.findViewById(android.R.id.title);
        tvTitle.setText(zip.title);

        ImageView ivStatus = (ImageView) view.findViewById(R.id.framework_zip_status);
        if (!hasLocal) {
            ivStatus.setImageResource(R.drawable.ic_cloud);
        } else if (hasOnline) {
            ivStatus.setImageResource(R.drawable.ic_cloud_download);
        } else {
            ivStatus.setImageResource(R.drawable.ic_cloud_off);
        }

        if (isOutdated) {
            int gray = Color.parseColor("#A0A0A0");
            tvTitle.setTextColor(gray);
            ivStatus.setColorFilter(gray);
        }

        view.setClickable(true);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showActionDialog(getActivity(), zip.title, zip.type);
            }
        });
        container.addView(view);
    }


    private void showActionDialog(final Context context, final String title, final FrameworkZips.Type type) {
        final long ACTION_INSTALL = 0;
        final long ACTION_INSTALL_RECOVERY = 1;
        final long ACTION_SAVE = 2;
        final long ACTION_DELETE = 3;

        final MaterialSimpleListAdapter adapter = new MaterialSimpleListAdapter(new MaterialSimpleListAdapter.Callback() {
            @Override
            public void onMaterialListItemSelected(MaterialDialog dialog, int index, MaterialSimpleListItem item) {
                dialog.dismiss();
                long action = item.getId();

                // Handle delete simple actions.
                if (action == ACTION_DELETE) {
                    FrameworkZips.delete(context, title, type);
                    triggerRefresh(false, true);
                    return;
                }

                // Handle actions that need a download first.
                RunnableWithParam<File> runAfterDownload = null;
                if (action == ACTION_INSTALL) {
                    runAfterDownload = new RunnableWithParam<File>() {
                        @Override
                        public void run(File file) {
                            flash(context, new FlashDirectly(file, false));
                        }
                    };
                } else if (action == ACTION_INSTALL_RECOVERY) {
                    runAfterDownload = new RunnableWithParam<File>() {
                        @Override
                        public void run(File file) {
                            flash(context, new FlashRecoveryAuto(file));
                        }
                    };
                } else if (action == ACTION_SAVE) {
                    runAfterDownload = new RunnableWithParam<File>() {
                        @Override
                        public void run(File file) {
                            saveTo(context, file);
                        }
                    };
                }

                LocalFrameworkZip local = FrameworkZips.getLocal(title, type);
                if (local != null) {
                    runAfterDownload.run(local.path);
                } else {
                    download(context, title, type, runAfterDownload);
                }
            }
        });

        // TODO Adjust texts for uninstaller (e.g. "execute")
        adapter.add(new MaterialSimpleListItem.Builder(context)
                .content("Install")
                .id(ACTION_INSTALL)
                .icon(R.drawable.ic_check_circle)
                .build());

        adapter.add(new MaterialSimpleListItem.Builder(context)
                .content("Install via recovery")
                .id(ACTION_INSTALL_RECOVERY)
                .icon(R.drawable.ic_check_circle)
                .build());

        adapter.add(new MaterialSimpleListItem.Builder(context)
                .content("Save to...")
                .id(ACTION_SAVE)
                .icon(R.drawable.ic_save)
                .build());

        if (FrameworkZips.hasLocal(title, type)) {
            adapter.add(new MaterialSimpleListItem.Builder(context)
                    .content("Delete downloaded file")
                    .id(ACTION_DELETE)
                    .icon(R.drawable.ic_delete)
                    .build());
        }

        MaterialDialog dialog = new Builder(context)
                .title(title)
                .adapter(adapter, null)
                .build();

        dialog.show();
    }

    private void download(Context context, String title, FrameworkZips.Type type, final RunnableWithParam<File> callback) {
        OnlineFrameworkZip zip = FrameworkZips.getOnline(title, type);
        new DownloadsUtil.Builder(context)
                .setTitle(zip.title)
                .setUrl(zip.url)
                .setDestinationFromUrl(DownloadsUtil.DOWNLOAD_FRAMEWORK)
                .setCallback(new DownloadFinishedCallback() {
                    @Override
                    public void onDownloadFinished(Context context, DownloadInfo info) {
                        triggerRefresh(false, true);
                        callback.run(new File(info.localFilename));
                    }
                })
                .setMimeType(DownloadsUtil.MIME_TYPES.ZIP)
                .setDialog(true)
                .download();
    }

    private static void flash(Context context, Flashable flashable) {
        Intent install = new Intent(context, InstallationActivity.class);
        install.putExtra(Flashable.KEY, flashable);
        context.startActivity(install);
    }

    private static void saveTo(Context context, File file) {
        Toast.makeText(context, "Not implemented yet", Toast.LENGTH_SHORT).show();
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

    private void softReboot() {
        if (!startShell())
            return;

        List<String> messages = new LinkedList<>();
        if (mRootUtil.execute("setprop ctl.restart surfaceflinger; setprop ctl.restart zygote", messages) != 0) {
            messages.add("");
            messages.add(getString(R.string.reboot_failed));
            showAlert(TextUtils.join("\n", messages).trim());
        }
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
}
