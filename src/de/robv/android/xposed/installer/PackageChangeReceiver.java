package de.robv.android.xposed.installer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

public class PackageChangeReceiver extends BroadcastReceiver {
	public static String MIN_MODULE_VERSION = "2.0b1";
	
	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)
				&& intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))
			return;
		
		new Thread() {
			@Override
			public void run() {
				String packageName = getPackageName(intent);
				if (packageName == null)
					return;
				
				String appName;
				try {
					PackageManager pm = context.getPackageManager();
					ApplicationInfo app = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
					if (app.metaData == null || !app.metaData.containsKey("xposedmodule"))
						return;
					appName = pm.getApplicationLabel(app).toString();
				} catch (NameNotFoundException e) {
					return;
				}
				
				Set<String> enabledModules = getEnabledModules(context);
				if (enabledModules.contains(packageName))
					updateModulesList(context, enabledModules);
				else
					showNotActivatedNotification(context, packageName, appName);
			}
		}.start();
	}
	
	private static String getPackageName(Intent intent) {
        Uri uri = intent.getData();
        return (uri != null) ? uri.getSchemeSpecificPart() : null;
    }
	
	static Set<String> getEnabledModules(Context context) {
		Set<String> modules = new HashSet<String>();
		try {
			BufferedReader moduleLines = new BufferedReader(new FileReader("/data/xposed/modules.whitelist"));
			String module;
			while ((module = moduleLines.readLine()) != null) {
				modules.add(module);
			}
			moduleLines.close();
		} catch (IOException e) {
			Toast.makeText(context, "cannot read /data/xposed/modules.whitelist", 1000).show();
			Log.e(XposedInstallerActivity.TAG, "cannot read /data/xposed/modules.whitelist", e);
			return modules;
		}
		return modules;
	}
	
	static void setEnabledModules(Context context, Set<String> modules) {
		try {
			PrintWriter pw = new PrintWriter("/data/xposed/modules.whitelist");
			for (String module : modules) {
				pw.println(module);
			}
			pw.close();
		} catch (IOException e) {
			Toast.makeText(context, "cannot write /data/xposed/modules.whitelist", 1000).show();
			Log.e(XposedInstallerActivity.TAG, "cannot write /data/xposed/modules.whitelist", e);
		}
	}
	
	static synchronized void updateModulesList(final Context context, final Set<String> enabledModules) {
		new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void... params) {
				try {
					PackageManager pm = context.getPackageManager();
					Log.i(XposedInstallerActivity.TAG, "updating modules.list");
					String installedXposedVersion = InstallerFragment.getJarInstalledVersion(null); 
					PrintWriter modulesList = new PrintWriter("/data/xposed/modules.list");
					for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
						if (!enabledModules.contains(app.packageName) || app.metaData == null
						 || !app.metaData.containsKey("xposedmodule") || !app.metaData.containsKey("xposedminversion"))
							continue;
						
						String minVersion = app.metaData.getString("xposedminversion");
						if (installedXposedVersion != null &&
							(PackageChangeReceiver.compareVersions(minVersion, installedXposedVersion) > 0
							|| PackageChangeReceiver.compareVersions(minVersion, MIN_MODULE_VERSION) < 0))
							continue;
						
						modulesList.println(app.sourceDir);
					}
					modulesList.close();
					
					return context.getString(R.string.xposed_module_list_updated);
				} catch (IOException e) {
					Log.e(XposedInstallerActivity.TAG, "cannot write /data/xposed/modules.list", e);
					return "cannot write /data/xposed/modules.list";
				}
			}
			
			@Override
			protected void onPostExecute(String result) {
				Toast.makeText(context, result, 1000).show();
			}
		}.execute();
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
	
	
	private static Pattern SEARCH_PATTERN = Pattern.compile("(\\d+)(\\D+)?");
	// removes: spaces at front and back, any dots and following zeros/stars at the end, any stars at the end
	private static Pattern TRIM_VERSION = Pattern.compile("^\\s*(.*?)(?:\\.+[0*]*)*\\**\\s*$");
	public static int compareVersions(String s1, String s2) {
		// easy: both are equal
		if (s1.equalsIgnoreCase(s2))
			return 0;
		
		s1 = trimVersion(s1);
		s2 = trimVersion(s2);
		
		// check again
		if (s1.equalsIgnoreCase(s2))
			return 0;
		
		Matcher m1 = SEARCH_PATTERN.matcher(s1);
		Matcher m2 = SEARCH_PATTERN.matcher(s2);
		boolean bothMatch = false;
		while (m1.find() && m2.find()) {
			bothMatch = true;
			
			// if the whole match is equal, continue with the next match
			if (m1.group().equalsIgnoreCase(m2.group()))
				continue;
			
			// compare numeric part
			int i1 = Integer.parseInt(m1.group(1));
			int i2 = Integer.parseInt(m2.group(1));
			if (i1 != i2)
				return i1 - i2;
			
			// numeric part is equal from here on, now compare the suffix (anything non-numeric after the number)
			String suf1 = m1.group(2);
			String suf2 = m2.group(2);
			
			// both have no suffix, means nothing left in the string => equal
			if (suf1 == null && suf2 == null)
				return 0;
			
			// only one has a suffix => if it is a dot, a number will follow => newer, otherwise older
			if (suf1 == null)
				return suf2.equals(".") ? -1 : 1;
			if (suf2 == null)
				return suf1.equals(".") ? 1 : -1;
			
			// both have a prefix	
			if (suf1 != null && suf2 != null && !suf1.equalsIgnoreCase(suf2))
				return suf1.compareToIgnoreCase(suf2);
		}
		
		// if one of the strings does not start with a number, do a simple string comparison
		if (!bothMatch)
			return s1.compareToIgnoreCase(s2);

		// either whoever has remaining digits is bigger
		return m1.hitEnd() ? -1 : 1;
	}
	
	public static String trimVersion(String version) {
		return TRIM_VERSION.matcher(version).replaceFirst("$1"); 		
	}
}
