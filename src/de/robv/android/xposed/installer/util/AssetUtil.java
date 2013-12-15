package de.robv.android.xposed.installer.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.os.Build;
import android.os.Environment;
import android.os.FileUtils;
import android.util.Log;
import de.robv.android.xposed.installer.XposedApp;

public class AssetUtil {
	public static final File BUSYBOX_FILE = new File(XposedApp.getInstance().getCacheDir(), "busybox-xposed");

	public static String getBinariesFolder() {
		if (Build.CPU_ABI.startsWith("armeabi")) {
			return "arm/";
		} else if (Build.CPU_ABI.startsWith("x86")) {
			return "x86/";
		} else {
			return null;
		}
	}

	public static File writeAssetToCacheFile(String name, int mode) {
		return writeAssetToCacheFile(name, name, mode);
	}

	public static File writeAssetToCacheFile(String assetName, String fileName, int mode) {
		return writeAssetToFile(assetName, new File(XposedApp.getInstance().getCacheDir(), fileName), mode);
	}

	public static File writeAssetToSdcardFile(String name, int mode) {
		return writeAssetToSdcardFile(name, name, mode);
	}

	public static File writeAssetToSdcardFile(String assetName, String fileName, int mode) {
		File dir = Environment.getExternalStorageDirectory();
		dir.mkdirs();
		return writeAssetToFile(assetName, new File(dir, fileName), mode);
	}

	public static File writeAssetToFile(String assetName, File targetFile, int mode) {
		try {
			InputStream in = XposedApp.getInstance().getAssets().open(assetName);
			FileOutputStream out = new FileOutputStream(targetFile);

			byte[] buffer = new byte[1024];
			int len;
			while ((len = in.read(buffer)) > 0){
				out.write(buffer, 0, len);
			}
			in.close();
			out.close();

			FileUtils.setPermissions(targetFile.getAbsolutePath(), mode, -1, -1);

			return targetFile;
		} catch (IOException e) {
			Log.e(XposedApp.TAG, "could not extract asset", e);
			if (targetFile != null)
				targetFile.delete();

			return null;
		}
	}

	public static void extractBusybox() {
		if (BUSYBOX_FILE.exists())
			return;

		AssetUtil.writeAssetToCacheFile(getBinariesFolder() + "busybox-xposed", "busybox-xposed", 00700);
	}

	public static void removeBusybox() {
		BUSYBOX_FILE.delete();
	}
}
