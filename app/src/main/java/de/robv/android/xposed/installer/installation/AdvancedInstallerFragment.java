package de.robv.android.xposed.installer.installation;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.ParseException;
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
import android.text.format.DateFormat;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.AssetUtil;
import de.robv.android.xposed.installer.util.JSONUtils;
import de.robv.android.xposed.installer.util.RootUtil;
import de.robv.android.xposed.installer.util.XposedZip;

import static android.content.Context.MODE_PRIVATE;

public class AdvancedInstallerFragment extends Fragment {

    private static final List<XposedZip.Installer> listOfficialInstaller = new ArrayList<>();
    private static final List<XposedZip.Installer> listSystemlessInstallers = new ArrayList<>();
    private static final List<XposedZip.Installer> listSamsungInstallers = new ArrayList<>();
    private static final List<XposedZip.Installer> listMiuiInstallers = new ArrayList<>();

    private static final List<XposedZip.Uninstaller> listOfficialUninstaller = new ArrayList<>();
    private static final List<XposedZip.Uninstaller> listSystemlessUninstallers = new ArrayList<>();
    private static final List<XposedZip.Uninstaller> listSamsungUninstallers = new ArrayList<>();
    private static final List<XposedZip.Uninstaller> listMiuiUninstallers = new ArrayList<>();
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

    public static void gotoPage(int page) {mPager.setCurrentItem(page);}

    private void onNetworkChange(boolean state) {
        if (state) new JSONParser().execute();
        else StatusInstallerFragment.setError(true, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        counter = 0;

        listOfficialInstaller.clear();
        listSystemlessInstallers.clear();
        listSamsungInstallers.clear();
        listMiuiInstallers.clear();

        listOfficialUninstaller.clear();
        listSystemlessUninstallers.clear();
        listSamsungUninstallers.clear();
        listMiuiUninstallers.clear();
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
        new JSONParser().execute();
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
    public void onResume() {
        super.onResume();

        mTabLayout.setBackgroundColor(XposedApp.getColor(getContext()));

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
                if (XposedApp.getPreferences().getBoolean("confirm_reboots", true)) {
                    areYouSure(R.string.reboot, new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            super.onPositive(dialog);
                            reboot(null);
                        }
                    });
                } else {
                    reboot(null);
                }
                break;
            case R.id.soft_reboot:
                if (XposedApp.getPreferences().getBoolean("confirm_reboots", true)) {
                    areYouSure(R.string.soft_reboot, new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            super.onPositive(dialog);
                            softReboot();
                        }
                    });
                } else {
                    softReboot();
                }
                break;
            case R.id.reboot_recovery:
                if (XposedApp.getPreferences().getBoolean("confirm_reboots", true)) {
                    areYouSure(R.string.reboot_recovery, new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            super.onPositive(dialog);
                            reboot("recovery");
                        }
                    });
                } else {
                    reboot("recovery");
                }
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

    public static class OfficialInstaller extends BaseAdvancedInstaller {

        @Override
        protected List<XposedZip.Installer> installers() {
            return listOfficialInstaller;
        }

        @Override
        protected List<XposedZip.Uninstaller> uninstallers() {
            return listOfficialUninstaller;
        }

        @Override
        protected int compatibility() {
            switch (Build.VERSION.SDK_INT) {
                case 21:
                case 22:
                case 23:
                    return R.string.official_compatibility_v21;
                default:
                    return R.string.official_compatibility;
            }
        }

        @Override
        protected int incompatibility() {
            switch (Build.VERSION.SDK_INT) {
                case 21:
                case 22:
                case 23:
                    return R.string.official_incompatibility_v21;
                default:
                    return R.string.official_incompatibility;
            }
        }

        @Override
        protected CharSequence author() {
            return "rovo89";
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

    public static class SystemlessInstaller extends BaseAdvancedInstaller {

        @Override
        protected List<XposedZip.Installer> installers() {
            return listSystemlessInstallers;
        }

        @Override
        protected List<XposedZip.Uninstaller> uninstallers() {
            return listSystemlessUninstallers;
        }

        @Override
        protected int compatibility() {
            return R.string.systemless_compatibility;
        }

        @Override
        protected int incompatibility() {
            return R.string.systemless_incompatibility;
        }

        @Override
        protected CharSequence author() {
            return "topjohnwu";
        }

        @Override
        protected String xdaUrl() {
            return "http://forum.xda-developers.com/xposed/unofficial-systemless-xposed-t3388268";
        }

    }

    public static class SamsungInstaller extends BaseAdvancedInstaller {

        @Override
        protected List<XposedZip.Installer> installers() {
            return listSamsungInstallers;
        }

        @Override
        protected List<XposedZip.Uninstaller> uninstallers() {
            return listSamsungUninstallers;
        }

        @Override
        protected int compatibility() {
            return R.string.samsung_compatibility;
        }

        @Override
        protected int incompatibility() {
            return R.string.samsung_incompatibility;
        }

        @Override
        protected CharSequence author() {
            return "wanam";
        }

        @Override
        protected String xdaUrl() {
            return "http://forum.xda-developers.com/xposed/unofficial-xposed-samsung-lollipop-t3180960";
        }

    }

    public static class MiuiInstaller extends BaseAdvancedInstaller {

        @Override
        protected List<XposedZip.Installer> installers() {
            return listMiuiInstallers;
        }

        @Override
        protected List<XposedZip.Uninstaller> uninstallers() {
            return listMiuiUninstallers;
        }

        @Override
        protected int compatibility() {
            return R.string.miui_compatibility;
        }

        @Override
        protected int incompatibility() {
            return R.string.miui_incompatibility;
        }

        @Override
        protected CharSequence author() {
            return "SolarWarez";
        }

        @Override
        protected String xdaUrl() {
            return "http://forum.xda-developers.com/xposed/unofficial-xposed-miui-t3367634";
        }

    }

    private class JSONParser extends AsyncTask<Void, Void, Boolean> {

        private String newApkVersion = null;
        private String newApkLink = null;
        private String newApkChangelog = null;

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                String originalJson = JSONUtils.getFileContent(JSONUtils.JSON_LINK);
                String newJson = JSONUtils.listZip();

                String jsonString = originalJson.replace("%XPOSED_ZIP%", newJson);

                JSONObject json = new JSONObject(jsonString);
                JSONArray installerArray = json.getJSONArray("installer");
                JSONArray uninstallerArray = json.getJSONArray("uninstaller");

                ArrayList<XposedZip.Installer> installers = new ArrayList<>();
                ArrayList<XposedZip.Uninstaller> uninstallers = new ArrayList<>();

                for (int i = 0; i < installerArray.length(); i++) {
                    JSONObject jsonObject = installerArray.getJSONObject(i);

                    String link = jsonObject.getString("link");
                    String name = jsonObject.getString("name");
                    String architecture = jsonObject.getString("architecture");
                    String version = jsonObject.getString("version");
                    int sdk = jsonObject.getInt("sdk");

                    installers.add(new XposedZip.Installer(link, name, architecture, sdk, version));
                }

                for (int i = 0; i < uninstallerArray.length(); i++) {
                    JSONObject jsonObject = uninstallerArray.getJSONObject(i);

                    String link = jsonObject.getString("link");
                    String name = jsonObject.getString("name");
                    String architecture = jsonObject.getString("architecture");

                    @SuppressLint("SimpleDateFormat")
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                    Date date = null;
                    try {
                        date = sdf.parse(jsonObject.getString("date"));
                    } catch (ParseException ignored) {
                    }
                    java.text.DateFormat dateFormat = DateFormat.getDateFormat(getContext());

                    uninstallers.add(new XposedZip.Uninstaller(link, name, architecture, dateFormat.format(date)));
                }

                for (XposedZip.Installer i : installers) {
                    if (Build.VERSION.SDK_INT == i.sdk) {
                        thisSdkCount++;
                        String name = i.name;
                        if (name.contains("systemless")) {
                            listSystemlessInstallers.add(i);
                        } else if (name.contains("wanam")) {
                            listSamsungInstallers.add(i);
                        } else if (name.contains("MIUI")) {
                            listMiuiInstallers.add(i);
                        } else {
                            listOfficialInstaller.add(i);
                        }
                    }
                }

                for (XposedZip.Uninstaller u : uninstallers) {
                    String name = u.name;
                    if (Build.VERSION.SDK_INT < 21) {
                        if (name.contains("Disabler")) {
                            listOfficialUninstaller.add(u);
                            break;
                        }
                    } else {
                        if (name.contains("wanam")) {
                            listSamsungUninstallers.add(u);
                        } else if (!name.contains("Disabler")) {
                            listOfficialUninstaller.add(u);
                            listMiuiUninstallers.add(u);
                        }
                    }
                }

                newApkVersion = json.getJSONObject("apk").getString("version");
                newApkLink = json.getJSONObject("apk").getString("link");
                newApkChangelog = json.getJSONObject("apk").getString("changelog");

                return true;
            } catch (Exception e) {
                Log.e(XposedApp.TAG, "AdvcancedInstallerFragment -> " + e.getMessage());
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

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

                if (newApkVersion == null)
                    return;

                SharedPreferences prefs;
                try {
                    prefs = getContext().getSharedPreferences(getContext().getPackageName() + "_preferences", MODE_PRIVATE);

                    prefs.edit().putString("changelog_" + newApkVersion, newApkChangelog).apply();
                } catch (NullPointerException ignored) {
                }

                BigInteger a = new BigInteger(XposedApp.THIS_APK_VERSION);
                BigInteger b = new BigInteger(newApkVersion);

                if (a.compareTo(b) == -1) {
                    StatusInstallerFragment.setUpdate(newApkLink, newApkChangelog);
                }

            } catch (IllegalStateException ignored) {
            }

        }
    }

    class TabsAdapter extends FragmentPagerAdapter {

        String[] tabsTitles = new String[]{getString(R.string.status), getString(R.string.official),
                getString(R.string.systemless), getString(R.string.samsung), getString(R.string.miui)};

        public TabsAdapter(FragmentManager mgr, boolean lock) {
            super(mgr);
            if (Build.VERSION.SDK_INT < 21) {
                tabsTitles = new String[]{tabsTitles[0], tabsTitles[1]};
            }
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
                    fragment = new OfficialInstaller();
                    break;
                case 2:
                    fragment = new SystemlessInstaller();
                    break;
                case 3:
                    fragment = new SamsungInstaller();
                    break;
                case 4:
                    fragment = new MiuiInstaller();
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
