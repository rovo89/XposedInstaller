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
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class InstallerFragment extends Fragment {
	private static Pattern PATTERN_APP_PROCESS_VERSION = Pattern.compile(".*with Xposed support \\(version (.+)\\).*");
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.tab_installer, container, false);
		
		// make link in description clickable
		final TextView txtDescription = (TextView) v.findViewById(R.id.installerDescription);
		txtDescription.setMovementMethod(LinkMovementMethod.getInstance());
		
		final TextView txtAppProcessInstalledVersion = (TextView) v.findViewById(R.id.app_process_installed_version);
		final TextView txtAppProcessLatestVersion = (TextView) v.findViewById(R.id.app_process_latest_version);
		final TextView txtJarInstalledVersion = (TextView) v.findViewById(R.id.jar_installed_version);
		final TextView txtJarLatestVersion = (TextView) v.findViewById(R.id.jar_latest_version);

		final String none = getString(R.string.none);
		txtAppProcessInstalledVersion.setText(getInstalledAppProcessVersion(none));
		txtAppProcessLatestVersion.setText(getLatestAppProcessVersion(none));
		txtJarInstalledVersion.setText(getJarInstalledVersion(none));
		txtJarLatestVersion.setText(getJarLatestVersion(none));
		
		final Button btnInstall = (Button) v.findViewById(R.id.btnInstall);
		final Button btnUninstall = (Button) v.findViewById(R.id.btnUninstall);
		final Button btnCleanup = (Button) v.findViewById(R.id.btnCleanup);
		final Button btnSoftRebootTestmode = (Button) v.findViewById(R.id.btnSoftRebootTestmode);
		final Button btnReboot = (Button) v.findViewById(R.id.btnReboot);
		
		if (checkCompatibility()) {
			btnInstall.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showAlert(install());
					txtAppProcessInstalledVersion.setText(getInstalledAppProcessVersion(none));
					txtJarInstalledVersion.setText(getJarInstalledVersion(none));
					Context context = InstallerFragment.this.getActivity();
					Set<String> enabledModules = PackageChangeReceiver.getEnabledModules(context);
					PackageChangeReceiver.updateModulesList(context, enabledModules);
					PackageChangeReceiver.updateNativeLibs(context, enabledModules);
				}
			});
		} else {
			btnInstall.setText(R.string.phone_not_compatible);
			btnInstall.setTextColor(Color.RED);
			btnInstall.setEnabled(false);
		}
		
		btnUninstall.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showAlert(uninstall());
				txtAppProcessInstalledVersion.setText(getInstalledAppProcessVersion(none));
				txtJarInstalledVersion.setText(getJarInstalledVersion(none));
			}
		});
		btnCleanup.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				areYouSure(R.string.cleanup, new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						showAlert(cleanup());
						txtAppProcessInstalledVersion.setText(getInstalledAppProcessVersion(none));
						txtJarInstalledVersion.setText(getJarInstalledVersion(none));
					}
				});
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
		
		btnSoftRebootTestmode.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				areYouSure(R.string.reboot, new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						showAlert(softRebootTestmode());
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
	
	private boolean checkCompatibility() {
		return checkBinaryCompatibility() && checkRomCompatibility();
	}

	private boolean checkBinaryCompatibility() {
		try {
			File testFile = writeAssetToCacheFile("xposedtest");
			if (testFile == null)
				return false;
			
			testFile.setExecutable(true);			
			Process p = Runtime.getRuntime().exec(testFile.getAbsolutePath());
			
			BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String result = stdout.readLine();
			stdout.close();
			
			testFile.delete();
			return result.equals("OK");
		} catch (IOException e) {
			return false;
		}
	}

	private boolean checkRomCompatibility() {
		try {
			String assetName = getAppProcessAssetName();
			if (assetName == null) {
				return false;
			}
			File testFile = writeAssetToCacheFile(assetName, "app_process");
			if (testFile == null)
				return false;

			testFile.setExecutable(true);
			Process p = Runtime.getRuntime().exec(testFile.getAbsolutePath());

			BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			stderr.readLine();
			stderr.readLine();
			String line3 = stderr.readLine();
			stderr.close();
			p.destroy();

			testFile.delete();
			return line3.matches(".*with Xposed support.*");
		} catch (IOException e) {
			return false;
		}
	}

	private String getAppProcessAssetName() {
		if (Build.VERSION.RELEASE.startsWith("4.0.")) {
			return "app_process_40";
		} else if (Build.VERSION.RELEASE.startsWith("4.1.")) {
			return "app_process_41";
		} else {
			return null;
		}
	}

	private String getInstalledAppProcessVersion(String defaultValue) {
		try {
			Process p = Runtime.getRuntime().exec(new String[] { "strings", "/system/bin/app_process" });
			return getAppProcessVersion(p.getInputStream(), defaultValue);
		} catch (IOException e) {
			return getString(R.string.none);
		}
	}
	
	private String getLatestAppProcessVersion(String defaultValue) {
		try {
			return getAppProcessVersion(
					getActivity().getAssets().open(getAppProcessAssetName()),
					defaultValue);
		} catch (Exception e) {
			return defaultValue;
		}
	}
	
	private String getAppProcessVersion(InputStream is, String defaultValue) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line;
		while ((line = br.readLine()) != null) {
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
			if (new File("/data/xposed/XposedBridge.jar.newversion").exists())
				return getJarVersion(new FileInputStream("/data/xposed/XposedBridge.jar.newversion"), defaultValue);
			else
				return getJarVersion(new FileInputStream("/data/xposed/XposedBridge.jar"), defaultValue);
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
		while ((entry = jis.getNextJarEntry()) != null) {
			if (!entry.getName().equals("assets/VERSION"))
				continue;
			
			BufferedReader br = new BufferedReader(new InputStreamReader(jis));
			String version = br.readLine();
			is.close();
			return version;
		}
		return defaultValue;
	}
	
	private String install() {
		File appProcessFile = writeAssetToCacheFile(getAppProcessAssetName(), "app_process");
		writeAssetToSdcardFile("Xposed-Disabler-CWM.zip");
		if (appProcessFile == null)
			return "Could not find asset \"app_process\"";
		
		File jarFile = writeAssetToCacheFile("XposedBridge.jar");
		if (jarFile == null)
			return "Could not find asset \"XposedBridge.jar\"";

		writeAssetToSdcardFile("Xposed-Disabler-CWM.zip");
		
		String result = executeScript("install.sh");
		
		appProcessFile.delete();
		jarFile.delete();
		
		return result;
	}
	
	private String uninstall() {
		return executeScript("uninstall.sh");
	}
	
	private String cleanup() {
		return executeScript("cleanup.sh");
	}
	
	private String softRebootTestmode() {
		return executeScript("soft_reboot.sh");
	}
	
	private String reboot() {
		return executeScript("reboot.sh");
	}
	
	private String executeScript(String name) {
		File scriptFile = writeAssetToCacheFile(name);
		if (scriptFile == null)
			return "Could not find asset \"" + name + "\"";
		
		File busybox = writeAssetToCacheFile("busybox-xposed");
		if (busybox == null) {
			scriptFile.delete();
			return "Could not find asset \"busybox-xposed\"";
		}
		
		scriptFile.setReadable(true, false);
		scriptFile.setExecutable(true, false);
		
		busybox.setReadable(true, false);
		busybox.setExecutable(true, false);
		
		try {
			Process p = Runtime.getRuntime().exec(
					new String[] { "su", "-c", scriptFile.getAbsolutePath() + " 2>&1" },
					null,
					getActivity().getCacheDir());
			BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = stdout.readLine()) != null) {
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
	
	private File writeAssetToCacheFile(String name) {
		return writeAssetToCacheFile(name, name);
	}

	private File writeAssetToCacheFile(String assetName, String fileName) {
		File file = null;
		try {
			InputStream in = getActivity().getAssets().open(assetName);
			file = new File(getActivity().getCacheDir(), fileName);
			FileOutputStream out = new FileOutputStream(file);
			
			byte[] buffer = new byte[1024];
			int len;
			while ((len = in.read(buffer)) > 0){
				out.write(buffer, 0, len);
			}
			in.close();
			out.close();
			
			return file;
		} catch (IOException e) {
			e.printStackTrace();
			if (file != null)
				file.delete();
			
			return null;
		}
	}

	private boolean writeAssetToSdcardFile(String name) {
		return writeAssetToSdcardFile(name, name);
	}

	private boolean writeAssetToSdcardFile(String assetName, String fileName) {
		File file = null;
		try {
			InputStream in = getActivity().getAssets().open(assetName);
			File dir = Environment.getExternalStorageDirectory();
			dir.mkdirs();
			file = new File(dir, fileName);
			FileOutputStream out = new FileOutputStream(file);

			byte[] buffer = new byte[1024];
			int len;
			while ((len = in.read(buffer)) > 0){
				out.write(buffer, 0, len);
			}
			in.close();
			out.close();

			return true;
		} catch (IOException e) {
			e.printStackTrace();
			if (file != null)
				file.delete();

			return false;
		}
	}

}
