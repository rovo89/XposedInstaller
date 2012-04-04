package de.robv.android.xposed.installer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.util.Log;

public class PackageChangeReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)
				&& intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))
			return;
		
		String packageName = getPackageName(intent);
		if (packageName == null)
			return;
		
		String appName;
		PackageManager pm = context.getPackageManager();
		try {
			ApplicationInfo app = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
			if (app.metaData == null || !app.metaData.containsKey("xposedmodule"))
				return;
			appName = pm.getApplicationLabel(app).toString();
		} catch (NameNotFoundException e) {
			return;
		}
		
		Set<String> enabledModules = getEnabledModules();
		updateModulesList(pm, enabledModules);
		
		if (!enabledModules.contains(packageName))
			showNotActivatedNotification(context, packageName, appName);
	}
	
	private static String getPackageName(Intent intent) {
        Uri uri = intent.getData();
        return (uri != null) ? uri.getSchemeSpecificPart() : null;
    }
	
	static Set<String> getEnabledModules() {
		Set<String> modules = new HashSet<String>();
		try {
			BufferedReader moduleLines = new BufferedReader(new FileReader("/data/xposed/modules.whitelist"));
			String module;
			while ((module = moduleLines.readLine()) != null) {
				modules.add(module);
			}
		} catch (IOException e) {
			Log.e(XposedInstallerActivity.TAG, "cannot read modules.whitelist", e);
			return modules;
		}
		return modules;
	}
	
	static void setEnabledModules(Set<String> modules) {
		try {
			PrintWriter pw = new PrintWriter("/data/xposed/modules.whitelist");
			for (String module : modules) {
				pw.println(module);
			}
			pw.close();
		} catch (IOException e) {
			Log.e(XposedInstallerActivity.TAG, "cannot read modules.whitelist", e);
		}
	}
	
	static synchronized void updateModulesList(PackageManager pm, Set<String> enabledModules) {
		try {
			Log.i(XposedInstallerActivity.TAG, "updating modules.list");
			PrintWriter modulesList = new PrintWriter("/data/xposed/modules.list");
			for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
				if (!enabledModules.contains(app.packageName) || app.metaData == null || !app.metaData.containsKey("xposedmodule"))
					continue;
				modulesList.println(app.sourceDir);
			}
			modulesList.close();
		} catch (IOException e) {
			Log.e(XposedInstallerActivity.TAG, "cannot write modules.list", e);
		}
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
		nm.notify(packageName, XposedInstallerActivity.NOTIFICATION_MODULE_NOT_ACTIVATED_YET, notification);
	}
}
