package de.robv.android.xposed.installer.util;

import java.util.LinkedList;
import java.util.List;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.XposedInstallerActivity;

public final class NotificationUtil {
	private static Context sContext = null;
	private static NotificationManager sNotificationManager;

	public static final int NOTIFICATION_MODULE_NOT_ACTIVATED_YET = 0;
	public static final int NOTIFICATION_MODULES_UPDATED = 1;

	private static final int PENDING_INTENT_OPEN_MODULES = 0;
	private static final int PENDING_INTENT_OPEN_INSTALL = 1;
	private static final int PENDING_INTENT_SOFT_REBOOT = 2;
	private static final int PENDING_INTENT_REBOOT = 3;
	private static final int PENDING_INTENT_ACTIVATE_MODULE = 4;
	private static final int PENDING_INTENT_ACTIVATE_MODULE_AND_REBOOT = 5;
	

	public static void init() {
		if (sContext != null)
			throw new IllegalStateException("NotificationUtil has already been initialized");

		sContext = XposedApp.getInstance();
		sNotificationManager = (NotificationManager) sContext.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	public static void cancel(int id) {
		sNotificationManager.cancel(id);
	}

	public static void cancelAll() {
		sNotificationManager.cancelAll();
	}

	public static void showNotActivatedNotification(String packageName, String appName) {
		Intent iModulesTab = new Intent(sContext, XposedInstallerActivity.class);
		iModulesTab.putExtra(XposedInstallerActivity.EXTRA_OPEN_TAB, XposedInstallerActivity.TAB_MODULES);
		iModulesTab.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		PendingIntent pModulesTab = PendingIntent.getActivity(sContext, PENDING_INTENT_OPEN_MODULES,
				iModulesTab, PendingIntent.FLAG_UPDATE_CURRENT);

		String title = sContext.getString(R.string.module_is_not_activated_yet);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(sContext)
			.setContentTitle(title)
			.setContentText(appName)
			.setTicker(title)
			.setContentIntent(pModulesTab)
			.setAutoCancel(true)
			.setSmallIcon(android.R.drawable.ic_dialog_info);
		
		if (Build.VERSION.SDK_INT >= 16) {
			Intent iActivateAndReboot = new Intent(sContext, RebootReceiver.class);
			iActivateAndReboot.putExtra(RebootReceiver.EXTRA_ACTIVATE_AND_REBOOT, true);
			iActivateAndReboot.putExtra(RebootReceiver.EXTRA_PACKAGENAME, packageName);
			PendingIntent pActivateAndReboot = PendingIntent.getBroadcast(sContext, PENDING_INTENT_ACTIVATE_MODULE_AND_REBOOT,
					iActivateAndReboot, PendingIntent.FLAG_UPDATE_CURRENT);
			
			Intent iActivate = new Intent(sContext, RebootReceiver.class);
			iActivate.putExtra(RebootReceiver.EXTRA_ACTIVATE, true);
			iActivate.putExtra(RebootReceiver.EXTRA_PACKAGENAME, packageName);
			PendingIntent pActivate = PendingIntent.getBroadcast(sContext, PENDING_INTENT_ACTIVATE_MODULE,
					iActivate, PendingIntent.FLAG_UPDATE_CURRENT);

			builder.addAction(0, "Activate and reboot", pActivateAndReboot);
			builder.addAction(0, "Activate", pActivate);
		}

		sNotificationManager.notify(packageName, NOTIFICATION_MODULE_NOT_ACTIVATED_YET, builder.build());
	}

	public static void showModulesUpdatedNotification() {
		Intent iInstallTab = new Intent(sContext, XposedInstallerActivity.class);
		iInstallTab.putExtra(XposedInstallerActivity.EXTRA_OPEN_TAB, XposedInstallerActivity.TAB_INSTALL);
		iInstallTab.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent pInstallTab = PendingIntent.getActivity(sContext, PENDING_INTENT_OPEN_INSTALL,
				iInstallTab, PendingIntent.FLAG_UPDATE_CURRENT);

		String title = sContext.getString(R.string.xposed_module_updated_notification_title);
		String message = sContext.getString(R.string.xposed_module_updated_notification);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(sContext)
			.setContentTitle(title)
			.setContentText(message)
			.setTicker(title)
			.setContentIntent(pInstallTab)
			.setAutoCancel(true)
			.setSmallIcon(android.R.drawable.ic_dialog_info);

		if (Build.VERSION.SDK_INT >= 16) {
			Intent iSoftReboot = new Intent(sContext, RebootReceiver.class);
			iSoftReboot.putExtra(RebootReceiver.EXTRA_SOFT_REBOOT, true);
			PendingIntent pSoftReboot = PendingIntent.getBroadcast(sContext, PENDING_INTENT_SOFT_REBOOT,
					iSoftReboot, PendingIntent.FLAG_UPDATE_CURRENT);

			Intent iReboot = new Intent(sContext, RebootReceiver.class);
			iReboot.putExtra(RebootReceiver.EXTRA_REBOOT, true);
			PendingIntent pReboot = PendingIntent.getBroadcast(sContext, PENDING_INTENT_REBOOT,
					iReboot, PendingIntent.FLAG_UPDATE_CURRENT);

			builder.addAction(0, sContext.getString(R.string.soft_reboot), pSoftReboot);
			builder.addAction(0, sContext.getString(R.string.reboot), pReboot);
		}

		sNotificationManager.notify(null, NOTIFICATION_MODULES_UPDATED, builder.build());
	}

	public static class RebootReceiver extends BroadcastReceiver {
		public static String EXTRA_REBOOT = "reboot";
		public static String EXTRA_SOFT_REBOOT = "soft";
		public static String EXTRA_ACTIVATE_AND_REBOOT = "activateandreboot";
		public static String EXTRA_ACTIVATE = "activate";
		public static String EXTRA_PACKAGENAME = "packagename";
		
		@Override
		public void onReceive(Context context, Intent intent) {	
			RootUtil rootUtil = null;
			List<String> messages = new LinkedList<String>();
			int returnCode = 0;
			
			/*
			 *  Close the notification bar in order to see the toast
			 *  if module was/wasn't successfully enabled.
			 *  Furthermore if SU permissions haven't been granted yet
			 *  the SU dialog will be prompted behind the expanded notification
			 *  bar and therefore not visible for the user 
			 */			
			sContext.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
			cancelAll();			
			
			if (intent.getBooleanExtra(EXTRA_ACTIVATE, false)) {			
				ModuleUtil moduleUtil = ModuleUtil.getInstance();
				String packageName = intent.getStringExtra(EXTRA_PACKAGENAME);
				moduleUtil.setModuleEnabled(packageName, true);				
				
				if (moduleUtil.isModuleEnabled(packageName)) {
					Toast.makeText(sContext, R.string.module_activated, Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(sContext, R.string.module_activated_failed, Toast.LENGTH_SHORT).show();
					
					Intent iModulesTab = new Intent(sContext, XposedInstallerActivity.class);
					iModulesTab.putExtra(XposedInstallerActivity.EXTRA_OPEN_TAB, XposedInstallerActivity.TAB_MODULES);
					iModulesTab.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					sContext.sendBroadcast(iModulesTab);
				}
			} else {
				rootUtil = new RootUtil();				
				if (!rootUtil.startShell()) {
					Log.e(XposedApp.TAG, "Could not start root shell");
					return;
				}
				
				if (intent.getBooleanExtra(EXTRA_REBOOT, false)) {				
					returnCode = rootUtil.executeWithBusybox("reboot", messages);
				} else if (intent.getBooleanExtra(EXTRA_SOFT_REBOOT, false)) {
					returnCode = rootUtil.execute("setprop ctl.restart surfaceflinger; setprop ctl.restart zygote", messages);
				} else if (intent.getBooleanExtra(EXTRA_ACTIVATE_AND_REBOOT, false)) {
					ModuleUtil moduleUtil = ModuleUtil.getInstance();
					moduleUtil.setModuleEnabled(intent.getStringExtra(EXTRA_PACKAGENAME), true);
					
					if (moduleUtil.isModuleEnabled(intent.getStringExtra(EXTRA_PACKAGENAME))) {
						returnCode = rootUtil.executeWithBusybox("reboot", messages);
					} else {
						Toast.makeText(sContext, R.string.module_activated_failed, Toast.LENGTH_SHORT).show();
						
						Intent iModulesTab = new Intent(sContext, XposedInstallerActivity.class);
						iModulesTab.putExtra(XposedInstallerActivity.EXTRA_OPEN_TAB, XposedInstallerActivity.TAB_MODULES);
						iModulesTab.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						sContext.startActivity(iModulesTab);
					}
				}
			}

			if (returnCode != 0) {
				Log.e(XposedApp.TAG, "Could not reboot:");
				for (String line : messages) {
					Log.e(XposedApp.TAG, line);
				}
			}
			
			if (rootUtil != null) {
				rootUtil.dispose();
				AssetUtil.removeBusybox();
			}
		}
	}
}
