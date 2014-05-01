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
			PendingIntent pReboot = PendingIntent.getBroadcast(sContext, PENDING_INTENT_REBOOT,
					iReboot, PendingIntent.FLAG_UPDATE_CURRENT);

			builder.addAction(0, sContext.getString(R.string.soft_reboot), pSoftReboot);
			builder.addAction(0, sContext.getString(R.string.reboot), pReboot);
		}

		sNotificationManager.notify(null, NOTIFICATION_MODULES_UPDATED, builder.build());
	}

	public static class RebootReceiver extends BroadcastReceiver {
		public static String EXTRA_SOFT_REBOOT = "soft";

		@Override
		public void onReceive(Context context, Intent intent) {
			RootUtil rootUtil = new RootUtil();
			if (!rootUtil.startShell()) {
				Log.e(XposedApp.TAG, "Could not start root shell");
				return;
			}

			List<String> messages = new LinkedList<String>();
			boolean isSoftReboot = intent.getBooleanExtra(EXTRA_SOFT_REBOOT, false);
			int returnCode = isSoftReboot ?
				  rootUtil.execute("setprop ctl.restart surfaceflinger; setprop ctl.restart zygote", messages)
				: rootUtil.executeWithBusybox("reboot", messages);

			if (returnCode != 0) {
				Log.e(XposedApp.TAG, "Could not reboot:");
				for (String line : messages) {
					Log.e(XposedApp.TAG, line);
				}
			}

			rootUtil.dispose();
			AssetUtil.removeBusybox();
		}
	}
}