package de.robv.android.xposed.installer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

		if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
			// Package being removed, disable it if it was a previously active Xposed mod
			if (mModuleUtil.isModuleEnabled(packageName)) {
				mModuleUtil.setModuleEnabled(packageName, false);
				mModuleUtil.updateModulesList();
			}
			return;
		}

		InstalledModule module = ModuleUtil.getInstance().reloadSingleModule(packageName);
		if (module == null)
			return;

		if (mModuleUtil.isModuleEnabled(packageName)) {
			mModuleUtil.updateModulesList();
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
		nm.notify(packageName, XposedInstallerActivity.NOTIFICATION_MODULE_NOT_ACTIVATED_YET, notification);
	}
	
	
	private static Pattern SEARCH_PATTERN = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)*)(.*?)[.+*]*\\s*$");
	private static Pattern SUFFIX_PATTERN = Pattern.compile("(.*?)(\\d*)$");
	public static int compareVersions(String s1, String s2) {
		// easy: both are equal
		if (s1.equalsIgnoreCase(s2))
			return 0;
		
		Matcher m1 = SEARCH_PATTERN.matcher(s1);
		Matcher m2 = SEARCH_PATTERN.matcher(s2);
		
		// one or both doesn't start with a number => simple string comparison
		if (!m1.find() || !m2.find())
			return s1.compareToIgnoreCase(s2);
		
		// get number blocks, removes trailing .0's
		String[] numeric1 = m1.group(1).split("\\.0*");
		String[] numeric2 = m2.group(1).split("\\.0*");
		int len1 = numeric1.length;
		int len2 = numeric2.length;

		String suffix1 = m1.group(2);
		String suffix2 = m2.group(2);
		
		// compare the number blocks one by one
		for (int i = 0; i < len1; i++) {
			// equal so far, but 1 has more digits so it wins
			if (i >= len2)
				return 1;
			
			int i1 = numeric1[i].isEmpty() ? 0 : Integer.parseInt(numeric1[i]);
			int i2 = numeric2[i].isEmpty() ? 0 : Integer.parseInt(numeric2[i]);

			// different numbers in this block, highest wins
			if (i1 != i2)
				return i1 - i2;
		}
		
		// equal so far, but 2 has more digits so it wins
		if (len1 < len2)
			return -1;
		
		if (suffix1.equalsIgnoreCase(suffix2))
			return 0;
		
		// if one version has no suffix, it wins
		if (suffix1.isEmpty())
			return 1;
		else if (suffix2.isEmpty())
			return -1;
		
		m1 = SUFFIX_PATTERN.matcher(suffix1);
		m2 = SUFFIX_PATTERN.matcher(suffix2);
		m1.find(); m2.find();
		
		// different suffix base => string comparison of the suffix base
		int cmp = m1.group(1).compareToIgnoreCase(m2.group(1));
		if (cmp != 0)
			return cmp;
		
		// if one version has no trailing number in the suffix, it loses 
		if (m1.group(2).isEmpty())
			return -1;
		else if (m2.group(2).isEmpty())
			return 1;
		
		// same suffix base, compare the trailing number of the suffixes
		int suffixNum1 = Integer.parseInt(m1.group(2));
		int suffixNum2 = Integer.parseInt(m2.group(2));
		return suffixNum1 - suffixNum2;
	}
	
	private static Pattern TRIM_VERSION = Pattern.compile("[.+*]+$");
	/** removes: spaces at front and back, any dots, stars and plus signs at the end */
	public static String trimVersion(String version) {
		return TRIM_VERSION.matcher(version.trim()).replaceFirst("");
	}
}
