package de.robv.android.xposed.installer.util;

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
import de.robv.android.xposed.installer.WelcomeActivity;
import de.robv.android.xposed.installer.XposedApp;

public final class NotificationUtil {
    public static final int NOTIFICATION_MODULE_NOT_ACTIVATED_YET = 0;
    public static final int NOTIFICATION_MODULES_UPDATED = 1;
    private static final int PENDING_INTENT_OPEN_MODULES = 0;
    private static final int PENDING_INTENT_OPEN_INSTALL = 1;
    private static final int PENDING_INTENT_SOFT_REBOOT = 2;
    private static final int PENDING_INTENT_REBOOT = 3;
    private static final int PENDING_INTENT_ACTIVATE_MODULE_AND_REBOOT = 4;
    private static final int PENDING_INTENT_ACTIVATE_MODULE = 5;
    private static Context sContext = null;
    private static NotificationManager sNotificationManager;

    public static void init() {
        if (sContext != null)
            throw new IllegalStateException(
                    "NotificationUtil has already been initialized");

        sContext = XposedApp.getInstance();
        sNotificationManager = (NotificationManager) sContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public static void cancel(int id) {
        sNotificationManager.cancel(id);
    }

    public static void cancelAll() {
        sNotificationManager.cancelAll();
    }

    public static void showNotActivatedNotification(String packageName,
                                                    String appName) {
        Intent intent = new Intent(sContext, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("fragment", 1);

        PendingIntent pModulesTab = PendingIntent.getActivity(sContext, PENDING_INTENT_OPEN_MODULES, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        String title = sContext.getString(R.string.module_is_not_activated_yet);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(sContext).setContentTitle(title).setContentText(appName)
                .setTicker(title).setContentIntent(pModulesTab)
                .setVibrate(new long[]{0}).setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(sContext.getResources().getColor(R.color.colorPrimary));

        if (Build.VERSION.SDK_INT >= 21)
            builder.setPriority(2);

        Intent iActivateAndReboot = new Intent(sContext, RebootReceiver.class);
        iActivateAndReboot.putExtra(RebootReceiver.EXTRA_ACTIVATE_MODULE, packageName);
        PendingIntent pActivateAndReboot = PendingIntent.getBroadcast(sContext,
                PENDING_INTENT_ACTIVATE_MODULE_AND_REBOOT, iActivateAndReboot,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Intent iActivate = new Intent(sContext, RebootReceiver.class);
        iActivate.putExtra(RebootReceiver.EXTRA_ACTIVATE_MODULE, packageName);
        iActivate.putExtra(RebootReceiver.EXTRA_ACTIVATE_MODULE_AND_RETURN, true);
        PendingIntent pActivate = PendingIntent.getBroadcast(sContext,
                PENDING_INTENT_ACTIVATE_MODULE, iActivate,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.BigTextStyle notiStyle = new NotificationCompat.BigTextStyle();
        notiStyle.setBigContentTitle(title);
        notiStyle.bigText(sContext.getString(
                R.string.module_is_not_activated_yet_detailed, appName));
        builder.setStyle(notiStyle);

        // Only show the quick activation button if any module has been
        // enabled before,
        // to ensure that the user know the way to disable the module later.
        if (!ModuleUtil.getInstance().getEnabledModules().isEmpty()) {
            builder.addAction(R.drawable.ic_menu_refresh, sContext.getString(R.string.activate_and_reboot), pActivateAndReboot);
            builder.addAction(R.drawable.ic_save, sContext.getString(R.string.activate_only), pActivate);
        }

        sNotificationManager.notify(packageName,
                NOTIFICATION_MODULE_NOT_ACTIVATED_YET, builder.build());
    }

    @SuppressWarnings("deprecation")
    public static void showModulesUpdatedNotification() {
        Intent intent = new Intent(sContext, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("fragment", 0);

        PendingIntent pInstallTab = PendingIntent.getActivity(sContext,
                PENDING_INTENT_OPEN_INSTALL, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        String title = sContext
                .getString(R.string.xposed_module_updated_notification_title);
        String message = sContext
                .getString(R.string.xposed_module_updated_notification);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                sContext).setContentTitle(title).setContentText(message)
                .setTicker(title).setContentIntent(pInstallTab)
                .setVibrate(new long[]{0}).setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(sContext.getResources().getColor(R.color.colorPrimary));

        if (Build.VERSION.SDK_INT >= 21)
            builder.setPriority(2);

        Intent iSoftReboot = new Intent(sContext, RebootReceiver.class);
        iSoftReboot.putExtra(RebootReceiver.EXTRA_SOFT_REBOOT, true);
        PendingIntent pSoftReboot = PendingIntent.getBroadcast(sContext,
                PENDING_INTENT_SOFT_REBOOT, iSoftReboot,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Intent iReboot = new Intent(sContext, RebootReceiver.class);
        PendingIntent pReboot = PendingIntent.getBroadcast(sContext,
                PENDING_INTENT_REBOOT, iReboot,
                PendingIntent.FLAG_UPDATE_CURRENT);

        builder.addAction(0, sContext.getString(R.string.reboot), pReboot);
        builder.addAction(0, sContext.getString(R.string.soft_reboot),
                pSoftReboot);

        sNotificationManager.notify(null, NOTIFICATION_MODULES_UPDATED, builder.build());
    }

    public static class RebootReceiver extends BroadcastReceiver {
        public static String EXTRA_SOFT_REBOOT = "soft";
        public static String EXTRA_ACTIVATE_MODULE = "activate_module";
        public static String EXTRA_ACTIVATE_MODULE_AND_RETURN = "activate_module_and_return";

        @Override
        public void onReceive(Context context, Intent intent) {
            /*
            * Close the notification bar in order to see the toast that module
            * was enabled successfully. Furthermore, if SU permissions haven't
            * been granted yet, the SU dialog will be prompted behind the
            * expanded notification panel and is therefore not visible to the
            * user.
            */
            sContext.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            cancelAll();

            if (intent.hasExtra(EXTRA_ACTIVATE_MODULE)) {
                String packageName = intent.getStringExtra(EXTRA_ACTIVATE_MODULE);
                ModuleUtil moduleUtil = ModuleUtil.getInstance();
                moduleUtil.setModuleEnabled(packageName, true);
                moduleUtil.updateModulesList(false);
                Toast.makeText(sContext, R.string.module_activated, Toast.LENGTH_SHORT).show();

                if (intent.hasExtra(EXTRA_ACTIVATE_MODULE_AND_RETURN)) return;
            }

            RootUtil rootUtil = new RootUtil();
            if (!rootUtil.startShell()) {
                Log.e(XposedApp.TAG, "Could not start root shell");
                return;
            }

            boolean isSoftReboot = intent.getBooleanExtra(EXTRA_SOFT_REBOOT, false);
            rootUtil.reboot(isSoftReboot ? RootUtil.RebootMode.SOFT : RootUtil.RebootMode.NORMAL,
                    new RootUtil.LogLineCallback());

            AssetUtil.removeBusybox();
        }
    }
}
