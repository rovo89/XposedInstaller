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
	public static String MIN_MODULE_VERSION = "2.0";
	
	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)
				&& intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))
			return;
		
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
		if (enabledModules.contains(packageName)) {
			updateModulesList(context, enabledModules);
		} else {
			showNotActivatedNotification(context, packageName, appName);
		}
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
			Toast.makeText(context, "cannot read /data/xposed/modules.whitelist", Toast.LENGTH_LONG).show();
			Log.e(XposedInstallerActivity.TAG, "cannot read /data/xposed/modules.whitelist", e);
			return modules;
		}
		return modules;
	}
	
	static void setEnabledModules(Context context, Set<String> modules) {
		try {
			PrintWriter pw = new PrintWriter("/data/xposed/modules.whitelist");
			synchronized (modules) {
				for (String module : modules) {
					pw.println(module);
				}
			}
			pw.close();
		} catch (IOException e) {
			Toast.makeText(context, "cannot write /data/xposed/modules.whitelist", Toast.LENGTH_LONG).show();
			Log.e(XposedInstallerActivity.TAG, "cannot write /data/xposed/modules.whitelist", e);
		}
	}
	
	static synchronized void updateModulesList(final Context context, final Set<String> enabledModules) {
		new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void... params) {
				try {
					Log.i(XposedInstallerActivity.TAG, "updating modules.list");
					String installedXposedVersion = InstallerFragment.getJarInstalledVersion(null);
					if (installedXposedVersion == null)
						return "The xposed framework is not installed";
					
					PackageManager pm = context.getPackageManager();
					PrintWriter modulesList = new PrintWriter("/data/xposed/modules.list");

					HashSet<String> enabledModulesClone;
					synchronized (enabledModules) {
						enabledModulesClone = new HashSet<String>(enabledModules);
					}
					for (String packageName : enabledModulesClone) {
						ApplicationInfo app;
						try {
							app = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
						} catch (NameNotFoundException e) {
							continue;
						}
						
						if (app.metaData == null
						|| !app.metaData.containsKey("xposedmodule")
						|| !app.metaData.containsKey("xposedminversion"))
							continue;
						
						String minVersion = app.metaData.getString("xposedminversion");
						if (PackageChangeReceiver.compareVersions(minVersion, installedXposedVersion) > 0
								|| PackageChangeReceiver.compareVersions(minVersion, MIN_MODULE_VERSION) < 0)
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
				Toast.makeText(context, result, Toast.LENGTH_SHORT).show();
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
	
	
	private static Pattern SEARCH_PATTERN = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)*)(.*?)[.*]*\\s*$");
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
	
	private static Pattern TRIM_VERSION = Pattern.compile("^\\s*(.*?)(?:\\.+[0*]*)*\\**\\s*$");
	/** removes: spaces at front and back, any dots and following zeros/stars at the end, any stars at the end */
	public static String trimVersion(String version) {
		return TRIM_VERSION.matcher(version).replaceFirst("$1");
	}
}
