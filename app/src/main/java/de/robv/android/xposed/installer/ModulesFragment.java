package de.robv.android.xposed.installer;

import android.Manifest;
import android.app.ListFragment;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.installer.installation.StatusInstallerFragment;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.ModuleVersion;
import de.robv.android.xposed.installer.repo.ReleaseType;
import de.robv.android.xposed.installer.repo.RepoDb;
import de.robv.android.xposed.installer.repo.RepoDb.RowNotFoundException;
import de.robv.android.xposed.installer.util.DownloadsUtil;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.ModuleUtil.ModuleListener;
import de.robv.android.xposed.installer.util.NavUtil;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.ThemeUtil;

import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
import static de.robv.android.xposed.installer.XposedApp.WRITE_EXTERNAL_PERMISSION;

public class ModulesFragment extends ListFragment implements ModuleListener {
    public static final String SETTINGS_CATEGORY = "de.robv.android.xposed.category.MODULE_SETTINGS";
    public static final String PLAY_STORE_PACKAGE = "com.android.vending";
    public static final String PLAY_STORE_LINK = "https://play.google.com/store/apps/details?id=%s";
    public static final String XPOSED_REPO_LINK = "http://repo.xposed.info/module/%s";
    private static final String NOT_ACTIVE_NOTE_TAG = "NOT_ACTIVE_NOTE";
    private static String PLAY_STORE_LABEL = null;
    private int installedXposedVersion;
    private ModuleUtil mModuleUtil;
    private ModuleAdapter mAdapter = null;
    private PackageManager mPm = null;
    private Runnable reloadModules = new Runnable() {
        public void run() {
            mAdapter.setNotifyOnChange(false);
            mAdapter.clear();
            mAdapter.addAll(mModuleUtil.getModules().values());
            final Collator col = Collator.getInstance(Locale.getDefault());
            mAdapter.sort(new Comparator<InstalledModule>() {
                @Override
                public int compare(InstalledModule lhs, InstalledModule rhs) {
                    return col.compare(lhs.getAppName(), rhs.getAppName());
                }
            });
            mAdapter.notifyDataSetChanged();
        }
    };
    private MenuItem mClickedMenuItem = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModuleUtil = ModuleUtil.getInstance();
        mPm = getActivity().getPackageManager();
        if (PLAY_STORE_LABEL == null) {
            try {
                ApplicationInfo ai = mPm.getApplicationInfo(PLAY_STORE_PACKAGE,
                        0);
                PLAY_STORE_LABEL = mPm.getApplicationLabel(ai).toString();
            } catch (NameNotFoundException ignored) {
            }
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        installedXposedVersion = XposedApp.getInstalledXposedVersion();
        if (installedXposedVersion < 0 || XposedApp.getActiveXposedVersion() < 0 || StatusInstallerFragment.DISABLE_FILE.exists()) {
            View notActiveNote = getActivity().getLayoutInflater().inflate(R.layout.xposed_not_active_note, getListView(), false);
            if (installedXposedVersion < 0) {
                ((TextView) notActiveNote.findViewById(android.R.id.title)).setText(R.string.framework_not_installed);
            }
            notActiveNote.setTag(NOT_ACTIVE_NOTE_TAG);
            getListView().addHeaderView(notActiveNote);
        }

        mAdapter = new ModuleAdapter(getActivity());
        reloadModules.run();
        setListAdapter(mAdapter);
        setEmptyText(getActivity().getString(R.string.no_xposed_modules_found));
        registerForContextMenu(getListView());
        mModuleUtil.addListener(this);

        ActionBar actionBar = ((WelcomeActivity) getActivity()).getSupportActionBar();

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int sixDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, metrics);
        int eightDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, metrics);
        assert actionBar != null;
        int toolBarDp = actionBar.getHeight() == 0 ? 196 : actionBar.getHeight();

        getListView().setDivider(null);
        getListView().setDividerHeight(sixDp);
        getListView().setPadding(eightDp, toolBarDp + eightDp, eightDp, eightDp);
        getListView().setClipToPadding(false);

        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // TODO maybe enable again after checking the implementation
        //inflater.inflate(R.menu.menu_modules, menu);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions,
                grantResults);
        if (requestCode == WRITE_EXTERNAL_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mClickedMenuItem != null) {
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onOptionsItemSelected(mClickedMenuItem);
                        }
                    }, 500);
                }
            } else {
                Toast.makeText(getActivity(), R.string.permissionNotGranted, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.bookmarks) {
            startActivity(new Intent(getActivity(), ModulesBookmark.class));
            return true;
        }

        String backupPath = Environment.getExternalStorageDirectory()
                + "/XposedInstaller";

        File enabledModulesPath = new File(backupPath, "enabled_modules.list");
        File installedModulesPath = new File(backupPath, "installed_modules.list");
        File targetDir = new File(backupPath);
        File listModules = new File(XposedApp.ENABLED_MODULES_LIST_FILE);

        mClickedMenuItem = item;

        if (checkPermissions())
            return false;

        switch (item.getItemId()) {
            case R.id.export_enabled_modules:
                if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                    return false;
                }

                if (ModuleUtil.getInstance().getEnabledModules().isEmpty()) {
                    Toast.makeText(getActivity(), getString(R.string.no_enabled_modules), Toast.LENGTH_SHORT).show();
                    return false;
                }

                try {
                    if (!targetDir.exists())
                        targetDir.mkdir();

                    FileInputStream in = new FileInputStream(listModules);
                    FileOutputStream out = new FileOutputStream(enabledModulesPath);

                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    in.close();
                    out.close();
                } catch (IOException e) {
                    Toast.makeText(getActivity(), getResources().getString(R.string.logs_save_failed) + "\n" + e.getMessage(), Toast.LENGTH_LONG).show();
                    return false;
                }

                Toast.makeText(getActivity(), enabledModulesPath.toString(), Toast.LENGTH_LONG).show();
                return true;
            case R.id.export_installed_modules:
                if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                    Toast.makeText(getActivity(), R.string.sdcard_not_writable, Toast.LENGTH_LONG).show();
                    return false;
                }
                Map<String, InstalledModule> installedModules = ModuleUtil.getInstance().getModules();

                if (installedModules.isEmpty()) {
                    Toast.makeText(getActivity(), getString(R.string.no_installed_modules), Toast.LENGTH_SHORT).show();
                    return false;
                }

                try {
                    if (!targetDir.exists())
                        targetDir.mkdir();

                    FileWriter fw = new FileWriter(installedModulesPath);
                    BufferedWriter bw = new BufferedWriter(fw);
                    PrintWriter fileOut = new PrintWriter(bw);

                    Set keys = installedModules.keySet();
                    for (Object key1 : keys) {
                        String packageName = (String) key1;
                        fileOut.println(packageName);
                    }

                    fileOut.close();
                } catch (IOException e) {
                    Toast.makeText(getActivity(), getResources().getString(R.string.logs_save_failed) + "n" + e.getMessage(), Toast.LENGTH_LONG).show();
                    return false;
                }

                Toast.makeText(getActivity(), installedModulesPath.toString(), Toast.LENGTH_LONG).show();
                return true;
            case R.id.import_installed_modules:
                return importModules(installedModulesPath);
            case R.id.import_enabled_modules:
                return importModules(enabledModulesPath);
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean checkPermissions() {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_PERMISSION);
            return true;
        }
        return false;
    }

    private boolean importModules(File path) {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(getActivity(), R.string.sdcard_not_writable, Toast.LENGTH_LONG).show();
            return false;
        }
        InputStream ips = null;
        RepoLoader repoLoader = RepoLoader.getInstance();
        List<Module> list = new ArrayList<>();
        if (!path.exists()) {
            Toast.makeText(getActivity(), getString(R.string.no_backup_found),
                    Toast.LENGTH_LONG).show();
            return false;
        }
        try {
            ips = new FileInputStream(path);
        } catch (FileNotFoundException e) {
            Log.e(XposedApp.TAG, "Could not open " + path, e);
        }

        if (path.length() == 0) {
            Toast.makeText(getActivity(), R.string.file_is_empty, Toast.LENGTH_LONG).show();
            return false;
        }

        try {
            assert ips != null;
            InputStreamReader ipsr = new InputStreamReader(ips);
            BufferedReader br = new BufferedReader(ipsr);
            String line;
            while ((line = br.readLine()) != null) {
                Module m = repoLoader.getModule(line);

                if (m == null) {
                    Toast.makeText(getActivity(), getString(R.string.download_details_not_found,
                            line), Toast.LENGTH_SHORT).show();
                } else {
                    list.add(m);
                }
            }
            br.close();
        } catch (ActivityNotFoundException | IOException e) {
            Toast.makeText(getActivity(), e.toString(), Toast.LENGTH_SHORT).show();
        }

        for (Module m : list) {
            ModuleVersion mv = null;
            for (int i = 0; i < m.versions.size(); i++) {
                ModuleVersion mvTemp = m.versions.get(i);

                if (mvTemp.relType == ReleaseType.STABLE) {
                    mv = mvTemp;
                    break;
                }
            }

            if (mv != null) {
                DownloadsUtil.addModule(getActivity(), m.name, mv.downloadLink, new DownloadDetailsVersionsFragment.DownloadModuleCallback(mv));
            }
        }

        return true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mModuleUtil.removeListener(this);
        setListAdapter(null);
        mAdapter = null;
    }

    @Override
    public void onSingleInstalledModuleReloaded(ModuleUtil moduleUtil, String packageName, InstalledModule module) {
        getActivity().runOnUiThread(reloadModules);
    }

    @Override
    public void onInstalledModulesReloaded(ModuleUtil moduleUtil) {
        getActivity().runOnUiThread(reloadModules);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        String packageName = (String) v.getTag();
        if (packageName == null)
            return;

        if (packageName.equals(NOT_ACTIVE_NOTE_TAG)) {
            ((WelcomeActivity) getActivity()).switchFragment(0);
            return;
        }

        Intent launchIntent = getSettingsIntent(packageName);
        if (launchIntent != null)
            startActivity(launchIntent);
        else
            Toast.makeText(getActivity(),
                    getActivity().getString(R.string.module_no_ui),
                    Toast.LENGTH_LONG).show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {
        InstalledModule installedModule = getItemFromContextMenuInfo(menuInfo);
        if (installedModule == null)
            return;

        menu.setHeaderTitle(installedModule.getAppName());
        getActivity().getMenuInflater().inflate(R.menu.context_menu_modules, menu);

        if (getSettingsIntent(installedModule.packageName) == null)
            menu.removeItem(R.id.menu_launch);

        try {
            String support = RepoDb
                    .getModuleSupport(installedModule.packageName);
            if (NavUtil.parseURL(support) == null)
                menu.removeItem(R.id.menu_support);
        } catch (RowNotFoundException e) {
            menu.removeItem(R.id.menu_download_updates);
            menu.removeItem(R.id.menu_support);
        }

        String installer = mPm.getInstallerPackageName(installedModule.packageName);
        if (PLAY_STORE_LABEL != null && PLAY_STORE_PACKAGE.equals(installer))
            menu.findItem(R.id.menu_play_store).setTitle(PLAY_STORE_LABEL);
        else
            menu.removeItem(R.id.menu_play_store);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        InstalledModule module = getItemFromContextMenuInfo(item.getMenuInfo());
        if (module == null)
            return false;

        switch (item.getItemId()) {
            case R.id.menu_launch:
                startActivity(getSettingsIntent(module.packageName));
                return true;

            case R.id.menu_download_updates:
                Intent detailsIntent = new Intent(getActivity(), DownloadDetailsActivity.class);
                detailsIntent.setData(Uri.fromParts("package", module.packageName, null));
                startActivity(detailsIntent);
                return true;

            case R.id.menu_support:
                NavUtil.startURL(getActivity(), Uri.parse(RepoDb.getModuleSupport(module.packageName)));
                return true;

            case R.id.menu_play_store:
                Intent i = new Intent(android.content.Intent.ACTION_VIEW);
                i.setData(Uri.parse(String.format(PLAY_STORE_LINK, module.packageName)));
                i.setPackage(PLAY_STORE_PACKAGE);
                try {
                    startActivity(i);
                } catch (ActivityNotFoundException e) {
                    i.setPackage(null);
                    startActivity(i);
                }
                return true;

            case R.id.menu_app_info:
                startActivity(new Intent(ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", module.packageName, null)));
                return true;

            case R.id.menu_uninstall:
                startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", module.packageName, null)));
                return true;
        }

        return false;
    }

    private InstalledModule getItemFromContextMenuInfo(ContextMenuInfo menuInfo) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        int position = info.position - getListView().getHeaderViewsCount();
        return (position >= 0) ? (InstalledModule) getListAdapter().getItem(position) : null;
    }

    private Intent getSettingsIntent(String packageName) {
        // taken from
        // ApplicationPackageManager.getLaunchIntentForPackage(String)
        // first looks for an Xposed-specific category, falls back to
        // getLaunchIntentForPackage
        PackageManager pm = getActivity().getPackageManager();

        Intent intentToResolve = new Intent(Intent.ACTION_MAIN);
        intentToResolve.addCategory(SETTINGS_CATEGORY);
        intentToResolve.setPackage(packageName);
        List<ResolveInfo> ris = pm.queryIntentActivities(intentToResolve, 0);

        if (ris == null || ris.size() <= 0) {
            return pm.getLaunchIntentForPackage(packageName);
        }

        Intent intent = new Intent(intentToResolve);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(ris.get(0).activityInfo.packageName, ris.get(0).activityInfo.name);
        return intent;
    }

    private class ModuleAdapter extends ArrayAdapter<InstalledModule> {
        public ModuleAdapter(Context context) {
            super(context, R.layout.list_item_module, R.id.title);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            if (convertView == null) {
                // The reusable view was created for the first time, set up the
                // listener on the checkbox
                ((CheckBox) view.findViewById(R.id.checkbox)).setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        String packageName = (String) buttonView.getTag();
                        boolean changed = mModuleUtil.isModuleEnabled(packageName) ^ isChecked;
                        if (changed) {
                            mModuleUtil.setModuleEnabled(packageName, isChecked);
                            mModuleUtil.updateModulesList(true);
                        }
                    }
                });
            }

            InstalledModule item = getItem(position);

            TextView version = (TextView) view.findViewById(R.id.version_name);
            version.setText(item.versionName);

            // Store the package name in some views' tag for later access
            view.findViewById(R.id.checkbox).setTag(item.packageName);
            view.setTag(item.packageName);

            ((ImageView) view.findViewById(R.id.icon)).setImageDrawable(item.getIcon());

            TextView descriptionText = (TextView) view.findViewById(R.id.description);
            if (!item.getDescription().isEmpty()) {
                descriptionText.setText(item.getDescription());
                descriptionText.setTextColor(ThemeUtil.getThemeColor(getContext(), android.R.attr.textColorSecondary));
            } else {
                descriptionText.setText(getString(R.string.module_empty_description));
                descriptionText.setTextColor(getResources().getColor(R.color.warning));
            }

            CheckBox checkbox = (CheckBox) view.findViewById(R.id.checkbox);
            checkbox.setChecked(mModuleUtil.isModuleEnabled(item.packageName));
            TextView warningText = (TextView) view.findViewById(R.id.warning);

            if (item.minVersion == 0) {
                checkbox.setEnabled(false);
                warningText.setText(getString(R.string.no_min_version_specified));
                warningText.setVisibility(View.VISIBLE);
            } else if (installedXposedVersion != 0 && item.minVersion > installedXposedVersion) {
                checkbox.setEnabled(false);
                warningText.setText(String.format(getString(R.string.warning_xposed_min_version), item.minVersion));
                warningText.setVisibility(View.VISIBLE);
            } else if (item.minVersion < ModuleUtil.MIN_MODULE_VERSION) {
                checkbox.setEnabled(false);
                warningText.setText(String.format(getString(R.string.warning_min_version_too_low), item.minVersion, ModuleUtil.MIN_MODULE_VERSION));
                warningText.setVisibility(View.VISIBLE);
            } else if (item.isInstalledOnExternalStorage()) {
                checkbox.setEnabled(false);
                warningText.setText(getString(R.string.warning_installed_on_external_storage));
                warningText.setVisibility(View.VISIBLE);
            } else if (installedXposedVersion == 0) {
                checkbox.setEnabled(false);
                warningText.setText(getString(R.string.framework_not_installed));
                warningText.setVisibility(View.VISIBLE);
            } else {
                checkbox.setEnabled(true);
                warningText.setVisibility(View.GONE);
            }
            return view;
        }
    }

}
