package de.robv.android.xposed.installer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import de.robv.android.xposed.installer.util.AssetUtil;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;

public class PackageChangeReceiver extends BroadcastReceiver {
	private final static ModuleUtil mModuleUtil = ModuleUtil.getInstance();
	
	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)
				&& intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))
			// Ignore existing packages being removed in order to be updated
			return;
		
		String packageName = getPackageName(intent);
		if (packageName == null)
			return;

		if (intent.getAction().equals(Intent.ACTION_PACKAGE_CHANGED)) {
			// make sure that the change is for the complete package, not only a component
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

		if (packageName.equals(AssetUtil.STATIC_BUSYBOX_PACKAGE)) {
			AssetUtil.checkStaticBusyboxAvailability();
			AssetUtil.removeBusybox();
			return;
		}

		InstalledModule module = ModuleUtil.getInstance().reloadSingleModule(packageName);
		if (module == null || intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
			// Package being removed, disable it if it was a previously active Xposed mod
			if (mModuleUtil.isModuleEnabled(packageName)) {
				mModuleUtil.setModuleEnabled(packageName, false);
				mModuleUtil.updateModulesList(false);
			}
			return;
		}

		if (mModuleUtil.isModuleEnabled(packageName)) {
			mModuleUtil.updateModulesList(true);
		} else {
			showNotActivatedNotification(context, packageName, module.getAppName());
		}
	}
	
	private static String getPackageName(Intent intent) {
		Uri uri = intent.getData();
		return (uri != null) ? uri.getSchemeSpecificPart() : null;
	}
	


	private static void showNotActivatedNotification(Context context, String packageName, String appName) {
		Intent startXposedInstaller = new Intent(context, XposedInstallerActivity.class);
		startXposedInstaller.putExtra(XposedInstallerActivity.EXTRA_OPEN_TAB, XposedInstallerActivity.TAB_MODULES);
		startXposedInstaller.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		
		Notification notification =
			new Notification.Builder(context)
			.setContentTitle(context.getString(R.string.module_is_not_activated_yet))
			.setContentText(appName)
			.setTicker(context.getString(R.string.module_is_not_activated_yet))
			.setContentIntent(PendingIntent.getActivity(context, 0, startXposedInstaller, PendingIntent.FLAG_UPDATE_CURRENT))
			.setAutoCancel(true)
			.setSmallIcon(android.R.drawable.ic_dialog_info)
			.getNotification();

		NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(packageName, XposedApp.NOTIFICATION_MODULE_NOT_ACTIVATED_YET, notification);
	}
}
