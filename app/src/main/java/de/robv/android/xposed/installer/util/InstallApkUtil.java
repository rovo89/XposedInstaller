package de.robv.android.xposed.installer.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;

import java.io.File;

import de.robv.android.xposed.installer.XposedApp;

public class InstallApkUtil extends AsyncTask<Void, Void, Boolean> {

    private final DownloadsUtil.DownloadInfo info;
    private final Context context;
    private RootUtil mRootUtil;
    private boolean enabled;

    public InstallApkUtil(Context context, DownloadsUtil.DownloadInfo info) {
        this.context = context;
        this.info = info;

        mRootUtil = new RootUtil();
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        SharedPreferences prefs = XposedApp.getPreferences();
        enabled = prefs.getBoolean("install_with_su", false);

        if (enabled)
            mRootUtil.startShell();
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        if (enabled) {
            mRootUtil.execute("pm install -r \"" + info.localFilename + "\"", null);
        }

        return false;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);

        if (!enabled) {
            Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            installIntent.setDataAndType(Uri.fromFile(new File(info.localFilename)), DownloadsUtil.MIME_TYPE_APK);
            installIntent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.getApplicationInfo().packageName);
            context.startActivity(installIntent);
        }
    }
}