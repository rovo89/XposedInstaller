package de.robv.android.xposed.installer;

import java.io.File;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import de.robv.android.xposed.installer.util.DownloadsUtil;

public class DownloadReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(final Context context, final Intent intent) {
		String action = intent.getAction();
		if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
			DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
			long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
			Query query = new Query();
			query.setFilterById(downloadId);
			Cursor c = dm.query(query);
			if (c.moveToFirst()) {
				int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
				if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
					// TODO move this to a more central place so it could be accessed from other parts of the app
					String filename = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME));
					Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
					installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					installIntent.setDataAndType(Uri.fromFile(new File(filename)), DownloadsUtil.MIME_TYPE_APK);
					installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
					//installIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
					installIntent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.getApplicationInfo().packageName);
		            context.startActivity(installIntent);
				}
			}
		}
	}
}
