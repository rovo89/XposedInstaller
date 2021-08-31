package de.robv.android.xposed.installer.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.CallSuper;

import de.robv.android.xposed.installer.XposedApp;

public abstract class OnlineLoader<T> extends Loader<T> {
    protected SharedPreferences mPref = XposedApp.getPreferences();
    protected String mPrefKeyLastUpdateCheck = CLASS_NAME + "_last_update_check";
    protected int mUpdateFrequency = 24 * 60 * 60 * 1000;

    private static final ConnectivityManager sConMgr
            = (ConnectivityManager) XposedApp.getInstance().getSystemService(Context.CONNECTIVITY_SERVICE);

    protected boolean shouldUpdate() {
        long now = System.currentTimeMillis();
        long lastUpdateCheck = mPref.getLong(mPrefKeyLastUpdateCheck, 0);
        if (now < lastUpdateCheck + mUpdateFrequency) {
            return false;
        }

        NetworkInfo netInfo = sConMgr.getActiveNetworkInfo();
        if (netInfo == null || !netInfo.isConnected()) {
            return false;
        }

        mPref.edit().putLong(mPrefKeyLastUpdateCheck, now).apply();
        return true;
    }

    @CallSuper
    @Override
    protected void onClear() {
        resetLastUpdateCheck();
    }

    public void resetLastUpdateCheck() {
        mPref.edit().remove(mPrefKeyLastUpdateCheck).apply();
    }

}
