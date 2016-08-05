package de.robv.android.xposed.installer.installation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.WelcomeActivity;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.AssetUtil;
import de.robv.android.xposed.installer.util.RootUtil;
import de.robv.android.xposed.installer.util.XposedZip;
import de.robv.android.xposed.installer.util.ZipUtils;

public class AdvancedInstallerFragment extends Fragment {

    private static final List<XposedZip.Installer> listInstaller = new ArrayList<>();
    private static final List<XposedZip.Uninstaller> listUninstaller = new ArrayList<>();

    private static ViewPager mPager;
    private TabLayout mTabLayout;
    private int counter = 0;
    private BroadcastReceiver connectionListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            counter++;

            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();

            if (counter == 1) return;
            onNetworkChange(networkInfo != null && networkInfo.isConnected());
        }
    };
    private RootUtil mRootUtil = new RootUtil();
    private int thisSdkCount = 0;

    private void onNetworkChange(boolean state) {
        if (state) new ZipLoader().execute();
        else StatusInstallerFragment.setError(true, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        counter = 0;

        listInstaller.clear();
        listUninstaller.clear();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.tab_advanced_installer, container, false);
        mPager = (ViewPager) view.findViewById(R.id.pager);
        mTabLayout = (TabLayout) view.findViewById(R.id.tab_layout);

        mPager.setAdapter(new TabsAdapter(getChildFragmentManager(), true));
        mTabLayout.setupWithViewPager(mPager);

        setHasOptionsMenu(true);
        new ZipLoader().execute();
        getActivity().registerReceiver(connectionListener, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

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

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        getActivity().unregisterReceiver(connectionListener);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_installer, menu);
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
                break;
            case R.id.soft_reboot:
                areYouSure(R.string.soft_reboot, new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        super.onPositive(dialog);
                        softReboot();
                    }
                });
                break;
            case R.id.reboot_recovery:
                areYouSure(R.string.reboot_recovery, new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        super.onPositive(dialog);
                        reboot("recovery");
                    }
                });
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean startShell() {
        if (mRootUtil.startShell())
            return true;

        showAlert(getString(R.string.root_failed));
        return false;
    }

    private void areYouSure(int contentTextId, MaterialDialog.ButtonCallback yesHandler) {
        new MaterialDialog.Builder(getActivity()).title(R.string.areyousure)
                .content(contentTextId)
                .iconAttr(android.R.attr.alertDialogIcon)
                .positiveText(android.R.string.yes)
                .negativeText(android.R.string.no).callback(yesHandler).show();
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

    public static class ClassicInstaller extends BaseAdvancedInstaller {

        @Override
        protected List<XposedZip.Installer> installers() {
            return listInstaller;
        }

        @Override
        protected List<XposedZip.Uninstaller> uninstallers() {
            return listUninstaller;
        }

        @Override
        protected int compatibility() {
            switch (Build.VERSION.SDK_INT) {
                case 21:
                case 22:
                case 23:
                    return R.string.classic_compatibility_v21;
                default:
                    return R.string.classic_compatibility;
            }
        }

        @Override
        protected int incompatibility() {
            switch (Build.VERSION.SDK_INT) {
                case 21:
                case 22:
                case 23:
                    return R.string.classic_incompatibility_v21;
                default:
                    return R.string.classic_incompatibility;
            }
        }

        @Override
        protected String xdaUrl() {
            switch (Build.VERSION.SDK_INT) {
                case 21:
                case 22:
                    return "http://forum.xda-developers.com/xposed/official-xposed-lollipop-t3030118";
                case 23:
                    return "http://forum.xda-developers.com/xposed/discussion-xposed-marshmallow-t3249095";
                default:
                    return "http://forum.xda-developers.com/xposed";
            }
        }

    }

    private class ZipLoader extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            WelcomeActivity.mProgress.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                if (Build.VERSION.SDK_INT < 21) {
                    thisSdkCount++;
                    listInstaller.add(new XposedZip.Installer(null, "Xposed-Installer-Recovery", "arm + x86", String.valueOf(Build.VERSION.SDK_INT), "58"));
                    listUninstaller.add(new XposedZip.Uninstaller(getContext(), null, "Xposed-Disabler-Recovery", "arm + x86", "20140101"));
                    return true;
                }

                ZipUtils.init();

                for (XposedZip.Installer i : ZipUtils.getInstallers()) {
                    if (Build.VERSION.SDK_INT == Integer.parseInt(i.sdk)) {
                        thisSdkCount++;
                        listInstaller.add(i);
                    }
                }

                listUninstaller.addAll(ZipUtils.getUninstallers(getContext()));

                return true;
            } catch (Exception e) {
                Log.e(XposedApp.TAG, "AdvancedInstallerFragment -> " + e.getMessage());
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            WelcomeActivity.mProgress.setVisibility(View.GONE);
            try {
                if (thisSdkCount == 0) {
                    mPager.setAdapter(new TabsAdapter(getChildFragmentManager(), true));
                } else {
                    mPager.setAdapter(new TabsAdapter(getChildFragmentManager(), !result));
                }

                mTabLayout.setupWithViewPager(mPager);

                if (!result) {
                    StatusInstallerFragment.setError(true/* connection failed */, true /* so no sdks available*/);
                } else {
                    StatusInstallerFragment.setError(false /*connection ok*/, thisSdkCount == 0 /*if counter is 0 there aren't sdks*/);
                }

            } catch (IllegalStateException ignored) {
            }
        }
    }

    class TabsAdapter extends FragmentPagerAdapter {

        String[] tabsTitles = new String[]{getString(R.string.status), getString(R.string.install), "Download"};

        public TabsAdapter(FragmentManager mgr, boolean lock) {
            super(mgr);

            if (lock) {
                tabsTitles = new String[]{tabsTitles[0]};
            }
        }

        @Override
        public int getCount() { return tabsTitles.length; }

        @Override
        public Fragment getItem(int position) {
            Fragment fragment = null;
            switch (position) {
                case 0:
                    fragment = new StatusInstallerFragment();
                    break;
                case 1:
                    fragment = new ClassicInstaller();
                    break;
                case 2:
                    fragment = new FrameworkDownloader();
                    break;
            }
            return fragment;
        }

        @Override
        public String getPageTitle(int position) {
            return tabsTitles[position];
        }
    }
}
