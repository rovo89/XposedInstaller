package de.robv.android.xposed.installer.util;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Timer;
import java.util.TimerTask;

import de.robv.android.xposed.installer.XposedApp;

public class UpdateService extends Service {

    private Timer mTimer;
    private TimerTask mTask = new TimerTask() {
        @Override
        public void run() {
            if (!isOnline()) return;

            try {
                String jsonString = JSONUtils.getFileContent(JSONUtils.JSON_LINK).replace("%XPOSED_ZIP%", "");

                String newApkVersion = new JSONObject(jsonString).getJSONObject("apk").getString("version");

                BigInteger a = new BigInteger(XposedApp.THIS_APK_VERSION);
                BigInteger b = new BigInteger(newApkVersion);

                if (a.compareTo(b) == -1) {
                    NotificationUtil.showInstallerUpdateNotification();
                }
            } catch (IOException | JSONException e) {
                Log.d(XposedApp.TAG, e.getMessage());
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences prefs = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE);
        int interval = Integer.parseInt(prefs.getString("update_service_interval", "3"));
        if (interval == -1) {
            stopSelf();
            return;
        }
        mTimer = new Timer();
        mTimer.schedule(mTask, 2000, interval * 60 * 60 * 1000);
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mTimer != null) mTimer.cancel();
        if (mTask != null) mTask.cancel();
    }
}
