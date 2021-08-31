package de.robv.android.xposed.installer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.NotificationUtil;

public class PackageChangeReceiver extends BroadcastReceiver {
    private final static ModuleUtil mModuleUtil = ModuleUtil.getInstance();

    private static String getPackageName(Intent intent) {
        Uri uri = intent.getData();
        return (uri != null) ? uri.getSchemeSpecificPart() : null;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED) && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))
            // Ignore existing packages being removed in order to be updated
            return;

        String packageName = getPackageName(intent);
        if (packageName == null)
            return;

        if (intent.getAction().equals(Intent.ACTION_PACKAGE_CHANGED)) {
            // make sure that the change is for the complete package, not only a
            // component
            String[] components = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST);
            if (components != null) {
                boolean isForPackage = false;
                for (String component : components) {
                    if (packageName.equals(component)) {
                        isForPackage = true;
                        break;
                    }
                }
                if (!isForPackage)
                    return;
            }
        }

        InstalledModule module = ModuleUtil.getInstance().reloadSingleModule(packageName);
        if (module == null
                || intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
            // Package being removed, disable it if it was a previously active
            // Xposed mod
            if (mModuleUtil.isModuleEnabled(packageName)) {
                mModuleUtil.setModuleEnabled(packageName, false);
                mModuleUtil.updateModulesList(false);
            }
            return;
        }

        if (mModuleUtil.isModuleEnabled(packageName)) {
            mModuleUtil.updateModulesList(false);
            NotificationUtil.showModulesUpdatedNotification();
        } else {
            NotificationUtil.showNotActivatedNotification(packageName, module.getAppName());
        }
    }
}
