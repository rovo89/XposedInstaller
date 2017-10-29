package de.robv.android.xposed.installer.installation;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.DownloadsUtil;
import de.robv.android.xposed.installer.util.DownloadsUtil.DownloadFinishedCallback;
import de.robv.android.xposed.installer.util.DownloadsUtil.DownloadInfo;
import de.robv.android.xposed.installer.util.FrameworkZips;
import de.robv.android.xposed.installer.util.FrameworkZips.FrameworkZip;
import de.robv.android.xposed.installer.util.FrameworkZips.LocalFrameworkZip;
import de.robv.android.xposed.installer.util.FrameworkZips.LocalZipLoader;
import de.robv.android.xposed.installer.util.FrameworkZips.OnlineFrameworkZip;
import de.robv.android.xposed.installer.util.FrameworkZips.OnlineZipLoader;
import de.robv.android.xposed.installer.util.InstallZipUtil;
import de.robv.android.xposed.installer.util.Loader;
import de.robv.android.xposed.installer.util.NavUtil;
import de.robv.android.xposed.installer.util.RootUtil;
import de.robv.android.xposed.installer.util.RunnableWithParam;

public class StatusInstallerFragment extends Fragment {
    public static final File DISABLE_FILE = new File(XposedApp.BASE_DIR + "conf/disabled");
    private boolean mShowOutdated = false;

    private static boolean checkClassExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.status_installer, container, false);

        // Available ZIPs
        final SwipeRefreshLayout refreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swiperefreshlayout);
        refreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimary));

        ONLINE_ZIP_LOADER.setSwipeRefreshLayout(refreshLayout);
        ONLINE_ZIP_LOADER.addListener(mOnlineZipListener);
        ONLINE_ZIP_LOADER.triggerFirstLoadIfNecessary();

        LOCAL_ZIP_LOADER.addListener(mLocalZipListener);
        LOCAL_ZIP_LOADER.triggerFirstLoadIfNecessary();

        refreshZipViews(v);

        // Disable switch
        final SwitchCompat disableSwitch = (SwitchCompat) v.findViewById(R.id.disableSwitch);
        disableSwitch.setChecked(!DISABLE_FILE.exists());
        disableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (DISABLE_FILE.exists()) {
                    DISABLE_FILE.delete();
                    Snackbar.make(disableSwitch, R.string.xposed_on_next_reboot, Snackbar.LENGTH_LONG).show();
                } else {
                    try {
                        DISABLE_FILE.createNewFile();
                        Snackbar.make(disableSwitch, R.string.xposed_off_next_reboot, Snackbar.LENGTH_LONG).show();
                    } catch (IOException e) {
                        Log.e(XposedApp.TAG, "Could not create " + DISABLE_FILE, e);
                    }
                }
            }
        });

        // Device info
        TextView androidSdk = (TextView) v.findViewById(R.id.android_version);
        TextView manufacturer = (TextView) v.findViewById(R.id.ic_manufacturer);
        TextView cpu = (TextView) v.findViewById(R.id.cpu);

        androidSdk.setText(getString(R.string.android_sdk, Build.VERSION.RELEASE, getAndroidVersion(), Build.VERSION.SDK_INT));
        manufacturer.setText(getUIFramework());
        cpu.setText(FrameworkZips.ARCH);

        // Known issues
        refreshKnownIssue(v);

        // Display warning dialog to new users
        if (!XposedApp.getPreferences().getBoolean("hide_install_warning", false)) {
            new MaterialDialog.Builder(getActivity())
                    .title(R.string.install_warning_title)
                    .content(R.string.install_warning)
                    .positiveText(android.R.string.ok)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            if (dialog.isPromptCheckBoxChecked()) {
                                XposedApp.getPreferences().edit().putBoolean("hide_install_warning", true).apply();
                            }
                        }
                    })
                    .checkBoxPromptRes(R.string.dont_show_again, false, null)
                    .cancelable(false)
                    .show();
        }

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshInstallStatus();
    }

    private void refreshInstallStatus() {
        View v = getView();
        TextView txtInstallError = (TextView) v.findViewById(R.id.framework_install_errors);
        View txtInstallContainer = v.findViewById(R.id.status_container);
        ImageView txtInstallIcon = (ImageView) v.findViewById(R.id.status_icon);
        View disableWrapper = v.findViewById(R.id.disableView);

        // TODO This should probably compare the full version string, not just the number part.
        int active = XposedApp.getActiveXposedVersion();
        int installed = XposedApp.getInstalledXposedVersion();
        if (installed < 0) {
            txtInstallError.setText(R.string.framework_not_installed);
            txtInstallError.setTextColor(getResources().getColor(R.color.warning));
            txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.warning));
            txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_error));
            disableWrapper.setVisibility(View.GONE);
        } else if (installed != active) {
            txtInstallError.setText(getString(R.string.framework_not_active, XposedApp.getXposedProp().getVersion()));
            txtInstallError.setTextColor(getResources().getColor(R.color.amber_500));
            txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.amber_500));
            txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_warning));
        } else {
            txtInstallError.setText(getString(R.string.framework_active, XposedApp.getXposedProp().getVersion()));
            txtInstallError.setTextColor(getResources().getColor(R.color.darker_green));
            txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.darker_green));
            txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_check_circle));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ONLINE_ZIP_LOADER.removeListener(mOnlineZipListener);
        ONLINE_ZIP_LOADER.setSwipeRefreshLayout(null);
        LOCAL_ZIP_LOADER.removeListener(mLocalZipListener);
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
            case R.id.soft_reboot:
            case R.id.reboot_recovery:
                final RootUtil.RebootMode mode = RootUtil.RebootMode.fromId(item.getItemId());
                confirmReboot(mode.titleRes, new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        RootUtil.reboot(mode, getActivity());
                    }
                });
                return true;

            case R.id.show_outdated:
                mShowOutdated = !item.isChecked();
                XposedApp.getPreferences().edit().putBoolean("framework_download_show_outdated", mShowOutdated).apply();
                item.setChecked(mShowOutdated);
                refreshZipViews(getView());
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void confirmReboot(int contentTextId, MaterialDialog.SingleButtonCallback yesHandler) {
        new MaterialDialog.Builder(getActivity())
                .content(R.string.reboot_confirmation)
                .positiveText(contentTextId)
                .negativeText(android.R.string.no)
                .onPositive(yesHandler)
                .show();
    }

    @SuppressLint("StringFormatInvalid")
    private void refreshKnownIssue(View v) {
        final String issueName;
        final String issueLink;
        final File baseDir = new File(XposedApp.BASE_DIR);
        final ApplicationInfo appInfo = getActivity().getApplicationInfo();
        final Set<String> missingFeatures = XposedApp.getXposedProp().getMissingInstallerFeatures();

        if (!missingFeatures.isEmpty()) {
            InstallZipUtil.reportMissingFeatures(missingFeatures);
            issueName = getString(R.string.installer_needs_update, getString(R.string.app_name));
            issueLink = getString(R.string.about_support);
        } else if (new File("/system/framework/core.jar.jex").exists()) {
            issueName = "Aliyun OS";
            issueLink = "https://forum.xda-developers.com/showpost.php?p=52289793&postcount=5";
        } else if (Build.VERSION.SDK_INT < 24 && (new File("/data/miui/DexspyInstaller.jar").exists() || checkClassExists("miui.dexspy.DexspyInstaller"))) {
            issueName = "MIUI/Dexspy";
            issueLink = "https://forum.xda-developers.com/showpost.php?p=52291098&postcount=6";
        } else if (Build.VERSION.SDK_INT < 24 && new File("/system/framework/twframework.jar").exists()) {
            issueName = "Samsung TouchWiz ROM";
            issueLink = "https://forum.xda-developers.com/showthread.php?t=3034811";
        } else if (Build.VERSION.SDK_INT < 24 && !baseDir.equals(new File(appInfo.dataDir))) {
            Log.e(XposedApp.TAG, "Base directory: " + appInfo.dataDir);
            Log.e(XposedApp.TAG, "Expected: " + XposedApp.BASE_DIR);
            issueName = getString(R.string.known_issue_wrong_base_directory);
            issueLink = "https://github.com/rovo89/XposedInstaller/issues/395";
        } else if (Build.VERSION.SDK_INT >= 24 && !baseDir.equals(new File(appInfo.deviceProtectedDataDir))) {
            Log.e(XposedApp.TAG, "Base directory: " + appInfo.deviceProtectedDataDir);
            Log.e(XposedApp.TAG, "Expected: " + XposedApp.BASE_DIR);
            issueName = getString(R.string.known_issue_wrong_base_directory);
            issueLink = "https://github.com/rovo89/XposedInstaller/issues/395";
        } else if (!baseDir.exists()) {
            issueName = getString(R.string.known_issue_missing_base_directory);
            issueLink = "https://github.com/rovo89/XposedInstaller/issues/393";
        } else {
            issueName = null;
            issueLink = null;
        }

        TextView txtKnownIssue = (TextView) v.findViewById(R.id.framework_known_issue);
        if (issueName != null) {
            txtKnownIssue.setText(getString(R.string.install_known_issue, issueName));
            txtKnownIssue.setVisibility(View.VISIBLE);
            txtKnownIssue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NavUtil.startURL(getActivity(), issueLink);
                }
            });
        } else {
            txtKnownIssue.setVisibility(View.GONE);
        }
    }

    private String getAndroidVersion() {
        switch (Build.VERSION.SDK_INT) {
            case 15:
                return "Ice Cream Sandwich";
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
            case 25:
                return "Nougat";
            case 26:
            case 27:
                return "Oreo";
            default:
                return "unknown";
        }
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

    @UiThread
    private void refreshZipViews(View view) {
        LinearLayout zips = (LinearLayout) view.findViewById(R.id.zips);
        zips.removeAllViews();
        TextView tvError = (TextView) view.findViewById(R.id.zips_load_error);
        synchronized (FrameworkZips.class) {
            boolean hasZips = false;
            for (FrameworkZips.Type type : FrameworkZips.Type.values()) {
                hasZips |= addZipViews(getActivity().getLayoutInflater(), zips, type);
            }

            if (!FrameworkZips.hasLoadedOnlineZips()) {
                tvError.setText(R.string.framework_zip_load_failed);
                tvError.setVisibility(View.VISIBLE);
            } else if (!hasZips) {
                tvError.setText(R.string.framework_no_zips);
                tvError.setVisibility(View.VISIBLE);
            } else {
                tvError.setVisibility(View.GONE);
            }
        }
    }

    private boolean addZipViews(LayoutInflater inflater, ViewGroup root, FrameworkZips.Type type) {
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
                container = (ViewGroup) card.findViewById(android.R.id.content);
                root.addView(card);
            }

            addZipView(inflater, container, zip, hasOnline, hasLocal, isOutdated);
        }

        return !allTitles.isEmpty();
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
        final int ACTION_FLASH = 0;
        final int ACTION_FLASH_RECOVERY = 1;
        final int ACTION_SAVE = 2;
        final int ACTION_DELETE = 3;

        boolean isDownloaded = FrameworkZips.hasLocal(title, type);
        int itemCount = isDownloaded ? 3 : 2;
        String[] texts = new String[itemCount];
        int[] ids = new int[itemCount];
        int i = 0;

        texts[i] = context.getString(type.text_flash);
        ids[i++] = ACTION_FLASH;

        texts[i] = context.getString(type.text_flash_recovery);
        ids[i++] = ACTION_FLASH_RECOVERY;

        /*
        texts[i] = "Save to...";
        ids[i++] = ACTION_SAVE;
        */

        if (FrameworkZips.hasLocal(title, type)) {
            texts[i] = context.getString(R.string.framework_delete);
            ids[i++] = ACTION_DELETE;
        }

        new MaterialDialog.Builder(context)
                .title(title)
                .items(texts)
                .itemsIds(ids)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View itemView, int position, CharSequence text) {
                        final int action = itemView.getId();

                        // Handle delete simple actions.
                        if (action == ACTION_DELETE) {
                            FrameworkZips.delete(context, title, type);
                            LOCAL_ZIP_LOADER.triggerReload(true);
                            return;
                        }

                        // Handle actions that need a download first.
                        RunnableWithParam<File> runAfterDownload = null;
                        if (action == ACTION_FLASH) {
                            runAfterDownload = new RunnableWithParam<File>() {
                                @Override
                                public void run(File file) {
                                    flash(context, new FlashDirectly(file, type, title, false));
                                }
                            };
                        } else if (action == ACTION_FLASH_RECOVERY) {
                            runAfterDownload = new RunnableWithParam<File>() {
                                @Override
                                public void run(File file) {
                                    flash(context, new FlashRecoveryAuto(file, type, title));
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
                })
                .show();
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
                        LOCAL_ZIP_LOADER.triggerReload(true);
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

    private void refreshZipViewsOnUiThread() {
        XposedApp.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshZipViews(getView());
            }
        });
    }

    private static final OnlineZipLoader ONLINE_ZIP_LOADER = OnlineZipLoader.getInstance();
    private final Loader.Listener<OnlineZipLoader> mOnlineZipListener = new Loader.Listener<OnlineZipLoader>() {
        @Override
        public void onReloadDone(OnlineZipLoader loader) {
            refreshZipViewsOnUiThread();
        }
    };

    private static final LocalZipLoader LOCAL_ZIP_LOADER = LocalZipLoader.getInstance();
    private final Loader.Listener<LocalZipLoader> mLocalZipListener = new Loader.Listener<LocalZipLoader>() {
        @Override
        public void onReloadDone(LocalZipLoader loader) {
            refreshZipViewsOnUiThread();
        }
    };
}
