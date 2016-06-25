package de.robv.android.xposed.installer.advanced;

import android.annotation.SuppressLint;
import android.net.ParseException;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.JSONUtils;
import de.robv.android.xposed.installer.util.XposedZip;

public class AdvancedInstallerFragment extends Fragment {

    private static final List<XposedZip.Installer> listOfficialInstaller = new ArrayList<>();
    private static final List<XposedZip.Installer> listSystemlessInstallers = new ArrayList<>();
    private static final List<XposedZip.Installer> listSamsungInstallers = new ArrayList<>();
    private static final List<XposedZip.Installer> listHuaweiInstallers = new ArrayList<>();

    private static final List<XposedZip.Uninstaller> listOfficialUninstaller = new ArrayList<>();
    private static final List<XposedZip.Uninstaller> listSystemlessUninstallers = new ArrayList<>();
    private static final List<XposedZip.Uninstaller> listSamsungUninstallers = new ArrayList<>();
    private static final List<XposedZip.Uninstaller> listHuaweiUninstallers = new ArrayList<>();

    private ViewPager mPager;
    private TabLayout mTabLayout;
    private ProgressBar mProgress;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.tab_advanced_installer, container, false);
        mPager = (ViewPager) view.findViewById(R.id.pager);
        mTabLayout = (TabLayout) view.findViewById(R.id.tab_layout);
        mProgress = (ProgressBar) view.findViewById(R.id.progressBar);

        mTabLayout.setBackgroundColor(XposedApp.getColor(getContext()));

        new JSONParser().execute();

        return view;
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
            return R.string.official_compatibility;
        }

        @Override
        protected int incompatibility() {
            return R.string.official_incompatibility;
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
            return R.string.no_incompatibility;
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

    }

    public static class HuaweiInstaller extends BaseAdvancedInstaller {

        @Override
        protected List<XposedZip.Installer> installers() {
            return listHuaweiInstallers;
        }

        @Override
        protected List<XposedZip.Uninstaller> uninstallers() {
            return listHuaweiUninstallers;
        }

        @Override
        protected int compatibility() {
            return R.string.huawei_compatibility;
        }

        @Override
        protected int incompatibility() {
            return R.string.huawei_incompatibility;
        }

    }

    private class JSONParser extends AsyncTask<Void, Void, Boolean> {

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
                        String name = i.name;
                        if (name.contains("systemless")) {
                            listSystemlessInstallers.add(i);
                        } else if (name.contains("wanam")) {
                            listSamsungInstallers.add(i);
                        } else if (name.contains("MIUI")) {
                            listHuaweiInstallers.add(i);
                        } else {
                            listOfficialInstaller.add(i);
                        }
                    }
                }

                for (XposedZip.Uninstaller u : uninstallers) {
                    String name = u.name;
                    if (Build.VERSION.SDK_INT < 21) {
                        if (name.contains("disabler")) {
                            listOfficialUninstaller.add(u);
                            break;
                        }
                    } else {
                        if (name.contains("wanam")) {
                            listSamsungUninstallers.add(u);
                        } else {
                            listOfficialUninstaller.add(u);
                            listHuaweiUninstallers.add(u);
                        }
                    }
                }
                return true;
            } catch (Exception e) {
                Log.e(XposedApp.TAG, "InstallerFragment -> " + e.getMessage());
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            mProgress.setVisibility(View.GONE);

            mPager.setAdapter(new TabsAdapter(getChildFragmentManager(), !result));
            mTabLayout.setupWithViewPager(mPager);
        }
    }

    class TabsAdapter extends FragmentPagerAdapter {

        String[] tabsTitles = new String[]{getString(R.string.status), getString(R.string.official),
                getString(R.string.systemless), getString(R.string.samsung), getString(R.string.huawei)};

        public TabsAdapter(FragmentManager mgr, boolean lock) {
            super(mgr);
            if (lock) {
                tabsTitles = Build.VERSION.SDK_INT < 21 ? new String[]{tabsTitles[0], tabsTitles[1]} : new String[]{tabsTitles[0]};
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
                    fragment = new HuaweiInstaller();
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
