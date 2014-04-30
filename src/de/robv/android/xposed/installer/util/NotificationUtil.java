package de.robv.android.xposed.installer.util;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.XposedInstallerActivity;

public final class NotificationUtil {
	private static Context sContext = null;
	private static NotificationManager sNotificationManager;

	private static final int NOTIFICATION_MODULE_NOT_ACTIVATED_YET = 0;

	private static final int PENDING_INTENT_OPEN_MODULES = 0;

	public static void init() {
		if (sContext != null)
			throw new IllegalStateException("NotificationUtil has already been initialized");

		sContext = XposedApp.getInstance();
		sNotificationManager = (NotificationManager) sContext.getSystemService(Context.NOTIFICATION_SERVICE);
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
}
