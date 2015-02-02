package de.robv.android.xposed.installer;

import java.io.File;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.FileUtils;

public class ExportLogReceiver extends BroadcastReceiver {
	private final static String EXPORT_LOG = "de.robv.android.xposed.installer.EXPORT_LOG";

	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (!EXPORT_LOG.equals(intent.getAction())) {
			return;
		}
		Bundle extras = intent.getExtras();
		String path = context.getExternalFilesDir(null) + "/Xposed.log";
		if (extras != null && extras.getString("path") != null) {
			path = extras.getString("path");
		}
		exportLog(path);
	}

	private static void exportLog(String path) {
		File src = new File(XposedApp.BASE_DIR + "log/error.log");
		File dst = new File(path);
		if (dst.exists()) {
			dst.delete();
		}
		FileUtils.copyFile(src, dst);
	}
}
