package de.robv.android.xposed.installer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import de.robv.android.xposed.installer.util.ModuleUtil;

public class InstallerFragment extends Fragment {
	private static Pattern PATTERN_APP_PROCESS_VERSION = Pattern.compile(".*with Xposed support \\(version (.+)\\).*");
	private String APP_PROCESS_NAME = null;
	private String XPOSEDTEST_NAME = null;
	private final String BINARIES_FOLDER = getBinariesFolder();
	private static final String JAR_PATH = XposedApp.BASE_DIR + "bin/XposedBridge.jar";

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();
		if (activity instanceof XposedInstallerActivity)
			((XposedInstallerActivity) activity).setNavItem(XposedInstallerActivity.TAB_INSTALL, null);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.tab_installer, container, false);
		
		final TextView txtAppProcessInstalledVersion = (TextView) v.findViewById(R.id.app_process_installed_version);
		final TextView txtAppProcessLatestVersion = (TextView) v.findViewById(R.id.app_process_latest_version);
		final TextView txtJarInstalledVersion = (TextView) v.findViewById(R.id.jar_installed_version);
		final TextView txtJarLatestVersion = (TextView) v.findViewById(R.id.jar_latest_version);

		final Button btnInstall = (Button) v.findViewById(R.id.btnInstall);
		final Button btnUninstall = (Button) v.findViewById(R.id.btnUninstall);
		final Button btnSoftReboot = (Button) v.findViewById(R.id.btnSoftReboot);
		final Button btnReboot = (Button) v.findViewById(R.id.btnReboot);
		
		boolean isCompatible = false;
		if (BINARIES_FOLDER == null) {
			// incompatible processor architecture
		} else if (Build.VERSION.SDK_INT == 15) {
			APP_PROCESS_NAME = BINARIES_FOLDER + "app_process_xposed_sdk15";
			XPOSEDTEST_NAME = BINARIES_FOLDER + "xposedtest_sdk15";
			isCompatible = checkCompatibility();
			
		} else if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT <= 18) {
			APP_PROCESS_NAME = BINARIES_FOLDER + "app_process_xposed_sdk16";
			XPOSEDTEST_NAME = BINARIES_FOLDER + "xposedtest_sdk16";
			isCompatible = checkCompatibility();
			
		} else if (Build.VERSION.SDK_INT > 18) {
			APP_PROCESS_NAME = BINARIES_FOLDER + "app_process_xposed_sdk16";
			XPOSEDTEST_NAME = BINARIES_FOLDER + "xposedtest_sdk16";
			isCompatible = checkCompatibility();
			if (isCompatible) {
				btnInstall.setText(String.format(getString(R.string.not_tested_but_compatible), Build.VERSION.SDK_INT));
				btnInstall.setTextColor(Color.YELLOW);
			}
		}
		
		final String none = getString(R.string.none);
		final String appProcessInstalledVersion = getInstalledAppProcessVersion(none);
		final String appProcessLatestVersion = getLatestAppProcessVersion(none);
		final String jarInstalledVersion = getJarInstalledVersion(none);
		final String jarLatestVersion = getJarLatestVersion(none);
		
		txtAppProcessInstalledVersion.setText(appProcessInstalledVersion);
		txtAppProcessLatestVersion.setText(appProcessLatestVersion);
		txtJarInstalledVersion.setText(jarInstalledVersion);
		txtJarLatestVersion.setText(jarLatestVersion);

		if (appProcessInstalledVersion.equals(none)
				|| PackageChangeReceiver.compareVersions(appProcessInstalledVersion, appProcessLatestVersion) < 0)
			txtAppProcessInstalledVersion.setTextColor(Color.RED);
		else
			txtAppProcessInstalledVersion.setTextColor(Color.GREEN);
		
		if (jarInstalledVersion.equals(none)
				|| PackageChangeReceiver.compareVersions(jarInstalledVersion, jarLatestVersion) < 0)
			txtJarInstalledVersion.setTextColor(Color.RED);
		else
			txtJarInstalledVersion.setTextColor(Color.GREEN);
		
		
		if (isCompatible) {
			btnInstall.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showAlert(install());
					txtAppProcessInstalledVersion.setText(getInstalledAppProcessVersion(none));
					txtAppProcessInstalledVersion.setTextColor(Color.GREEN);
					txtJarInstalledVersion.setText(getJarInstalledVersion(none));
					txtJarInstalledVersion.setTextColor(Color.GREEN);

					ModuleUtil.getInstance().updateModulesList();
				}
			});
		} else {
			btnInstall.setText(String.format(getString(R.string.phone_not_compatible), Build.VERSION.SDK_INT, Build.CPU_ABI));
			btnInstall.setTextColor(Color.RED);
			btnInstall.setEnabled(false);
		}
		
		btnUninstall.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showAlert(uninstall());
				txtAppProcessInstalledVersion.setText(getInstalledAppProcessVersion(none));
				txtAppProcessInstalledVersion.setTextColor(Color.RED);
				txtJarInstalledVersion.setText(getJarInstalledVersion(none));
				txtJarInstalledVersion.setTextColor(Color.RED);
			}
		});
		btnReboot.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				areYouSure(R.string.reboot, new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						showAlert(reboot());
					}
				});
			}
		});
		
		btnSoftReboot.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				areYouSure(R.string.soft_reboot, new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						showAlert(softReboot());
					}
				});
			}
		});
		
		return v;
	}
	
	private void showAlert(String result) {
		new AlertDialog.Builder(getActivity())
        .setMessage(result)
        .setPositiveButton(android.R.string.ok, null)
        .create()
        .show();
	}
	
	private void areYouSure(int messageTextId, OnClickListener yesHandler) {
		new AlertDialog.Builder(getActivity())
		.setTitle(R.string.areyousure)
        .setMessage(messageTextId)
        .setIconAttribute(android.R.attr.alertDialogIcon)
        .setPositiveButton(android.R.string.yes, yesHandler)
        .setNegativeButton(android.R.string.no, null)
        .create()
        .show();
	}
	
	private static String getBinariesFolder() {
		if (Build.CPU_ABI.startsWith("armeabi")) {
			return "arm/";
		} else if (Build.CPU_ABI.startsWith("x86")) {
				return "x86/";
		} else {
			return null;
		}
	}
	
	private boolean checkCompatibility() {
		return checkXposedTestCompatibility() && checkAppProcessCompatibility();
	}

	private boolean checkXposedTestCompatibility() {
		try {
			if (XPOSEDTEST_NAME == null)
				return false;
			
			File testFile = writeAssetToCacheFile(XPOSEDTEST_NAME, "xposedtest", 00700);
			if (testFile == null)
				return false;
			
			Process p = Runtime.getRuntime().exec(testFile.getAbsolutePath());
			
			BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String result = stdout.readLine();
			stdout.close();
			p.destroy();
			
			testFile.delete();
			return result != null && result.equals("OK");
		} catch (IOException e) {
			return false;
		}
	}

	private boolean checkAppProcessCompatibility() {
		try {
			if (APP_PROCESS_NAME == null)
				return false;
			
			File testFile = writeAssetToCacheFile(APP_PROCESS_NAME, "app_process", 00700);
			if (testFile == null)
				return false;

			Process p = Runtime.getRuntime().exec(new String[] { testFile.getAbsolutePath(), "--xposedversion" });

			BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String result = stdout.readLine();
			stdout.close();
			p.destroy();

			testFile.delete();
			return result != null && result.startsWith("Xposed version: ");
		} catch (IOException e) {
			return false;
		}
	}

	private String getInstalledAppProcessVersion(String defaultValue) {
		try {
			return getAppProcessVersion(
					new FileInputStream("/system/bin/app_process"),
					defaultValue);
		} catch (IOException e) {
			return getString(R.string.none);
		}
	}
	
	private String getLatestAppProcessVersion(String defaultValue) {
		if (APP_PROCESS_NAME == null)
			return defaultValue;
		
		try {
			return getAppProcessVersion(
					getActivity().getAssets().open(APP_PROCESS_NAME),
					defaultValue);
		} catch (Exception e) {
			return defaultValue;
		}
	}
	
	private String getAppProcessVersion(InputStream is, String defaultValue) throws IOException {
		
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line;
		while ((line = br.readLine()) != null) {
			if (!line.contains("Xposed"))
				continue;
			Matcher m = PATTERN_APP_PROCESS_VERSION.matcher(line);
			if (m.find()) {
				is.close();
				return m.group(1);
			}
		}
		is.close();
		return defaultValue;
	}
	
	public static String getJarInstalledVersion(String defaultValue) {
		try {
			if (new File(JAR_PATH + ".newversion").exists())
				return getJarVersion(new FileInputStream(JAR_PATH + ".newversion"), defaultValue);
			else
				return getJarVersion(new FileInputStream(JAR_PATH), defaultValue);
		} catch (IOException e) {
			return defaultValue;
		}
	}
	
	private String getJarLatestVersion(String defaultValue) {
		try {
			return getJarVersion(getActivity().getAssets().open("XposedBridge.jar"), defaultValue);
		} catch (IOException e) {
			return defaultValue;
		}
	}
	
	public static String getJarVersion(InputStream is, String defaultValue) throws IOException {
		JarInputStream jis = new JarInputStream(is);
		JarEntry entry;
		try {
			while ((entry = jis.getNextJarEntry()) != null) {
				if (!entry.getName().equals("assets/VERSION"))
					continue;
				
				BufferedReader br = new BufferedReader(new InputStreamReader(jis));
				String version = br.readLine();
				is.close();
				br.close();
				return version;
			}
		} finally {
			try {
				jis.close();
			} catch (Exception e) { }
		}
		return defaultValue;
	}
	
	private String install() {
		File appProcessFile = writeAssetToCacheFile(APP_PROCESS_NAME, "app_process", 00700);
		if (appProcessFile == null)
			return "Could not find asset \"app_process\"";
		
		File jarFile = writeAssetToFile("XposedBridge.jar", new File(JAR_PATH + ".newversion"), 00644);
		if (jarFile == null)
			return "Could not find asset \"XposedBridge.jar\"";

		writeAssetToFile(APP_PROCESS_NAME, new File(XposedApp.BASE_DIR + "bin/app_process"), 00600);
		writeAssetToSdcardFile("Xposed-Disabler-Recovery.zip", 00644);
		
		String result = executeScript("install.sh");
		
		appProcessFile.delete();
		
		return result;
	}
	
	private String uninstall() {
		new File(JAR_PATH).delete();
		new File(JAR_PATH + ".newversion").delete();
		new File(XposedApp.BASE_DIR + "bin/app_process").delete();
		return executeScript("uninstall.sh");
	}
	
	private String softReboot() {
		return executeScript("soft_reboot.sh");
	}
	
	private String reboot() {
		return executeScript("reboot.sh");
	}
	
	private String executeScript(String name) {
		File scriptFile = writeAssetToCacheFile(name, 00700);
		if (scriptFile == null)
			return "Could not find asset \"" + name + "\"";
		
		File busybox = writeAssetToCacheFile(BINARIES_FOLDER + "busybox-xposed", "busybox-xposed", 00700);
		if (busybox == null) {
			scriptFile.delete();
			return "Could not find asset \"busybox-xposed\"";
		}
		
		try {
			Process p = Runtime.getRuntime().exec(
					new String[] {
						"su",
						"-c",
						scriptFile.getAbsolutePath() + " 2>&1"
					});
			BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
			BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = stdout.readLine()) != null) {
				sb.append(line);
				sb.append('\n');
			}
			while ((line = stderr.readLine()) != null) {
				sb.append(line);
				sb.append('\n');
			}
			stdout.close();
			return sb.toString();
			
		} catch (IOException e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			return sw.toString();
		} finally {
			scriptFile.delete();
			busybox.delete();
		}
	}

	private File writeAssetToCacheFile(String name, int mode) {
		return writeAssetToCacheFile(name, name, mode);
	}

	private File writeAssetToCacheFile(String assetName, String fileName, int mode) {
		return writeAssetToFile(assetName, new File(getActivity().getCacheDir(), fileName), mode);
	}

	private File writeAssetToSdcardFile(String name, int mode) {
		return writeAssetToSdcardFile(name, name, mode);
	}

	private File writeAssetToSdcardFile(String assetName, String fileName, int mode) {
		File dir = Environment.getExternalStorageDirectory();
		dir.mkdirs();
		return writeAssetToFile(assetName, new File(dir, fileName), mode);
	}

	private File writeAssetToFile(String assetName, File targetFile, int mode) {
		try {
			InputStream in = getActivity().getAssets().open(assetName);
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
			e.printStackTrace();
			if (targetFile != null)
				targetFile.delete();

			return null;
		}
	}
}
