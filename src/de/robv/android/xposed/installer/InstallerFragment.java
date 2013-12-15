package de.robv.android.xposed.installer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import de.robv.android.xposed.installer.util.AssetUtil;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.RootUtil;

public class InstallerFragment extends Fragment {
	private static Pattern PATTERN_APP_PROCESS_VERSION = Pattern.compile(".*with Xposed support \\(version (.+)\\).*");
	private String APP_PROCESS_NAME = null;
	private String XPOSEDTEST_NAME = null;
	private final String BINARIES_FOLDER = AssetUtil.getBinariesFolder();
	private static final String JAR_PATH = XposedApp.BASE_DIR + "bin/XposedBridge.jar";
	private static final String JAR_PATH_NEWVERSION = JAR_PATH + ".newversion";
	private static int JAR_LATEST_VERSION = -1;
	private final LinkedList<String> mCompatibilityErrors = new LinkedList<String>();
	private RootUtil mRootUtil = new RootUtil();

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();
		if (activity instanceof XposedDropdownNavActivity)
			((XposedDropdownNavActivity) activity).setNavItem(XposedDropdownNavActivity.TAB_INSTALL);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.tab_installer, container, false);

		final TextView txtAppProcessInstalledVersion = (TextView) v.findViewById(R.id.app_process_installed_version);
		final TextView txtAppProcessLatestVersion = (TextView) v.findViewById(R.id.app_process_latest_version);
		final TextView txtJarInstalledVersion = (TextView) v.findViewById(R.id.jar_installed_version);
		final TextView txtJarLatestVersion = (TextView) v.findViewById(R.id.jar_latest_version);

		final TextView txtInstallError = (TextView) v.findViewById(R.id.framework_install_errors);

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

		} else if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT <= 19) {
			APP_PROCESS_NAME = BINARIES_FOLDER + "app_process_xposed_sdk16";
			XPOSEDTEST_NAME = BINARIES_FOLDER + "xposedtest_sdk16";
			isCompatible = checkCompatibility();

		} else if (Build.VERSION.SDK_INT > 19) {
			APP_PROCESS_NAME = BINARIES_FOLDER + "app_process_xposed_sdk16";
			XPOSEDTEST_NAME = BINARIES_FOLDER + "xposedtest_sdk16";
			isCompatible = checkCompatibility();
			if (isCompatible) {
				txtInstallError.setText(String.format(getString(R.string.not_tested_but_compatible), Build.VERSION.SDK_INT));
				txtInstallError.setTextColor(Color.YELLOW);
				txtInstallError.setVisibility(View.VISIBLE);
			}
		}

		final int appProcessInstalledVersion = getInstalledAppProcessVersion();
		final int appProcessLatestVersion = getLatestAppProcessVersion();
		final int jarInstalledVersion = getJarInstalledVersion();
		final int jarLatestVersion = getJarLatestVersion();

		txtAppProcessInstalledVersion.setText(versionToText(appProcessInstalledVersion));
		txtAppProcessLatestVersion.setText(versionToText(appProcessLatestVersion));
		txtJarInstalledVersion.setText(versionToText(jarInstalledVersion));
		txtJarLatestVersion.setText(versionToText(jarLatestVersion));

		if (appProcessInstalledVersion < appProcessLatestVersion)
			txtAppProcessInstalledVersion.setTextColor(Color.RED);
		else
			txtAppProcessInstalledVersion.setTextColor(Color.GREEN);

		if (jarInstalledVersion < jarLatestVersion)
			txtJarInstalledVersion.setTextColor(Color.RED);
		else
			txtJarInstalledVersion.setTextColor(Color.GREEN);


		if (isCompatible) {
			btnInstall.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (!install())
						return;

					txtAppProcessInstalledVersion.setText(versionToText(getInstalledAppProcessVersion()));
					txtAppProcessInstalledVersion.setTextColor(Color.GREEN);
					txtJarInstalledVersion.setText(versionToText(getJarInstalledVersion()));
					txtJarInstalledVersion.setTextColor(Color.GREEN);

					ModuleUtil.getInstance().updateModulesList();
				}
			});
		} else {
			String errorText = String.format(getString(R.string.phone_not_compatible), Build.VERSION.SDK_INT, Build.CPU_ABI);
			if (!mCompatibilityErrors.isEmpty())
				errorText += "\n\n" + TextUtils.join("\n", mCompatibilityErrors);
			txtInstallError.setText(errorText);
			txtInstallError.setTextColor(Color.RED);
			txtInstallError.setVisibility(View.VISIBLE);
			btnInstall.setEnabled(false);
		}

		btnUninstall.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!uninstall())
					return;

				txtAppProcessInstalledVersion.setText(versionToText(getInstalledAppProcessVersion()));
				txtAppProcessInstalledVersion.setTextColor(Color.RED);
				txtJarInstalledVersion.setText(versionToText(getJarInstalledVersion()));
				txtJarInstalledVersion.setTextColor(Color.RED);
			}
		});
		btnReboot.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				areYouSure(R.string.reboot, new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						reboot();
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
						softReboot();
					}
				});
			}
		});

		return v;
	}

	private String versionToText(int version) {
		return (version == 0) ? getString(R.string.none) : Integer.toString(version);
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
		.setTitle(messageTextId)
		.setMessage(R.string.areyousure)
		.setIconAttribute(android.R.attr.alertDialogIcon)
		.setPositiveButton(android.R.string.yes, yesHandler)
		.setNegativeButton(android.R.string.no, null)
		.create()
		.show();
	}

	private boolean checkCompatibility() {
		mCompatibilityErrors.clear();
		return checkXposedTestCompatibility() && checkAppProcessCompatibility();
	}

	private boolean checkXposedTestCompatibility() {
		try {
			if (XPOSEDTEST_NAME == null)
				return false;

			File testFile = AssetUtil.writeAssetToCacheFile(XPOSEDTEST_NAME, "xposedtest", 00700);
			if (testFile == null) {
				mCompatibilityErrors.add("could not write xposedtest to cache");
				return false;
			}

			Process p = Runtime.getRuntime().exec(testFile.getAbsolutePath());

			BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String result = stdout.readLine();
			stdout.close();

			BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			String errorLine;
			while ((errorLine = stderr.readLine()) != null) {
				mCompatibilityErrors.add(errorLine);
			}
			stderr.close();

			p.destroy();

			testFile.delete();
			return result != null && result.equals("OK");
		} catch (IOException e) {
			mCompatibilityErrors.add(e.getMessage());
			return false;
		}
	}

	private boolean checkAppProcessCompatibility() {
		try {
			if (APP_PROCESS_NAME == null)
				return false;

			File testFile = AssetUtil.writeAssetToCacheFile(APP_PROCESS_NAME, "app_process", 00700);
			if (testFile == null) {
				mCompatibilityErrors.add("could not write app_process to cache");
				return false;
			}

			Process p = Runtime.getRuntime().exec(new String[] { testFile.getAbsolutePath(), "--xposedversion" });

			BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String result = stdout.readLine();
			stdout.close();

			BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			String errorLine;
			while ((errorLine = stderr.readLine()) != null) {
				mCompatibilityErrors.add(errorLine);
			}
			stderr.close();

			p.destroy();

			testFile.delete();
			return result != null && result.startsWith("Xposed version: ");
		} catch (IOException e) {
			mCompatibilityErrors.add(e.getMessage());
			return false;
		}
	}

	private int getInstalledAppProcessVersion() {
		try {
			return getAppProcessVersion(new FileInputStream("/system/bin/app_process"));
		} catch (IOException e) {
			return 0;
		}
	}

	private int getLatestAppProcessVersion() {
		if (APP_PROCESS_NAME == null)
			return 0;

		try {
			return getAppProcessVersion(getActivity().getAssets().open(APP_PROCESS_NAME));
		} catch (Exception e) {
			return 0;
		}
	}

	private int getAppProcessVersion(InputStream is) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line;
		while ((line = br.readLine()) != null) {
			if (!line.contains("Xposed"))
				continue;
			Matcher m = PATTERN_APP_PROCESS_VERSION.matcher(line);
			if (m.find()) {
				is.close();
				return ModuleUtil.extractIntPart(m.group(1));
			}
		}
		is.close();
		return 0;
	}

	public static int getJarInstalledVersion() {
		try {
			if (new File(JAR_PATH_NEWVERSION).exists())
				return getJarVersion(new FileInputStream(JAR_PATH_NEWVERSION));
			else
				return getJarVersion(new FileInputStream(JAR_PATH));
		} catch (IOException e) {
			return 0;
		}
	}

	public static int getJarLatestVersion() {
		if (JAR_LATEST_VERSION == -1) {
			try {
				JAR_LATEST_VERSION = getJarVersion(XposedApp.getInstance().getAssets().open("XposedBridge.jar"));
			} catch (IOException e) {
				JAR_LATEST_VERSION = 0;
			}
		}
		return JAR_LATEST_VERSION;
	}

	public static int getJarVersion(InputStream is) throws IOException {
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
				return ModuleUtil.extractIntPart(version);
			}
		} finally {
			try {
				jis.close();
			} catch (Exception e) { }
		}
		return 0;
	}

	private boolean startShell() {
		if (mRootUtil.startShell())
			return true;

		showAlert(getString(R.string.root_failed));
		return false;
	}

	private boolean install() {
		if (!startShell())
			return false;

		List<String> messages = new LinkedList<String>();

		File appProcessFile = AssetUtil.writeAssetToCacheFile(APP_PROCESS_NAME, "app_process", 00700);
		if (appProcessFile == null) {
			showAlert(getString(R.string.file_extract_failed, "app_process"));
			return false;
		}

		try {
			messages.add(getString(R.string.file_mounting_writable, "/system"));
			if (mRootUtil.executeWithBusybox("mount -o remount,rw /system", messages) != 0) {
				messages.add(getString(R.string.file_mount_writable_failed, "/system"));
				messages.add(getString(R.string.file_trying_to_continue));
			}

			if (new File("/system/bin/app_process.orig").exists()) {
				messages.add(getString(R.string.file_backup_already_exists, "/system/bin/app_process.orig"));
			} else {
				if (mRootUtil.executeWithBusybox("cp -a /system/bin/app_process /system/bin/app_process.orig", messages) != 0) {
					messages.add("");
					messages.add(getString(R.string.file_backup_failed, "/system/bin/app_process"));
					return false;
				} else {
					messages.add(getString(R.string.file_backup_successful, "/system/bin/app_process.orig"));
				}
			}

			messages.add(getString(R.string.file_copying, "app_process"));
			if (mRootUtil.executeWithBusybox("cp -a " + appProcessFile.getAbsolutePath() + " /system/bin/app_process", messages) != 0) {
				messages.add("");
				messages.add(getString(R.string.file_copy_failed, "app_process", "/system/bin"));
				return false;
			}
			if (mRootUtil.executeWithBusybox("chmod 755 /system/bin/app_process", messages) != 0) {
				messages.add("");
				messages.add(getString(R.string.file_set_perms_failed, "/system/bin/app_process"));
				return false;
			}
			if (mRootUtil.executeWithBusybox("chown root:shell /system/bin/app_process", messages) != 0) {
				messages.add("");
				messages.add(getString(R.string.file_set_owner_failed, "/system/bin/app_process"));
				return false;
			}

			File blocker = new File(XposedApp.BASE_DIR + "conf/disabled");
			if (blocker.exists()) {
				messages.add(getString(R.string.file_removing, blocker.getAbsolutePath()));
				if (mRootUtil.executeWithBusybox("rm " + blocker.getAbsolutePath(), messages) != 0) {
					messages.add("");
					messages.add(getString(R.string.file_remove_failed, blocker.getAbsolutePath()));
					return false;
				}
			}

			if (new File("/data/xposed").exists()) {
				messages.add(getString(R.string.file_removing, "/data/xposed"));
				mRootUtil.executeWithBusybox("rm -r /data/xposed", messages);
				// ignoring the result as it's only cleanup
			}

			messages.add(getString(R.string.file_copying, "XposedBridge.jar"));
			File jarFile = AssetUtil.writeAssetToFile("XposedBridge.jar", new File(JAR_PATH_NEWVERSION), 00644);
			if (jarFile == null) {
				messages.add("");
				messages.add(getString(R.string.file_extract_failed, "XposedBridge.jar"));
				return false;
			}

			AssetUtil.writeAssetToFile(APP_PROCESS_NAME, new File(XposedApp.BASE_DIR + "bin/app_process"), 00600);
			AssetUtil.writeAssetToSdcardFile("Xposed-Disabler-Recovery.zip", 00644);

			messages.add("");
			messages.add(getString(R.string.file_done));
			return true;

		} finally {
			mRootUtil.dispose();
			AssetUtil.removeBusybox();
			appProcessFile.delete();

			showAlert(TextUtils.join("\n", messages).trim());
		}
	}

	private boolean uninstall() {
		new File(JAR_PATH_NEWVERSION).delete();
		new File(JAR_PATH).delete();
		new File(XposedApp.BASE_DIR + "bin/app_process").delete();

		if (!startShell())
			return false;

		List<String> messages = new LinkedList<String>();
		try {
			messages.add(getString(R.string.file_mounting_writable, "/system"));
			if (mRootUtil.executeWithBusybox("mount -o remount,rw /system", messages) != 0) {
				messages.add(getString(R.string.file_mount_writable_failed, "/system"));
				messages.add(getString(R.string.file_trying_to_continue));
			}

			messages.add(getString(R.string.file_backup_restoring, "/system/bin/app_process.orig"));
			if (!new File("/system/bin/app_process.orig").exists()) {
				messages.add("");
				messages.add(getString(R.string.file_backup_not_found, "/system/bin/app_process.orig"));
				return false;
			}

			if (mRootUtil.executeWithBusybox("mv /system/bin/app_process.orig /system/bin/app_process", messages) != 0) {
				messages.add("");
				messages.add(getString(R.string.file_move_failed, "/system/bin/app_process.orig", "/system/bin/app_process"));
				return false;
			}
			if (mRootUtil.executeWithBusybox("chmod 755 /system/bin/app_process", messages) != 0) {
				messages.add("");
				messages.add(getString(R.string.file_set_perms_failed, "/system/bin/app_process"));
				return false;
			}
			if (mRootUtil.executeWithBusybox("chown root:shell /system/bin/app_process", messages) != 0) {
				messages.add("");
				messages.add(getString(R.string.file_set_owner_failed, "/system/bin/app_process"));
				return false;
			}

			if (new File("/data/xposed").exists()) {
				messages.add(getString(R.string.file_removing, "/data/xposed"));
				mRootUtil.executeWithBusybox("rm -r /data/xposed", messages);
				// ignoring the result as it's only cleanup
			}

			messages.add("");
			messages.add(getString(R.string.file_done));
			return true;

		} finally {
			mRootUtil.dispose();
			AssetUtil.removeBusybox();

			showAlert(TextUtils.join("\n", messages).trim());
		}
	}

	private void softReboot() {
		if (!startShell())
			return;

		List<String> messages = new LinkedList<String>();
		if (mRootUtil.execute("setprop ctl.restart surfaceflinger; setprop ctl.restart zygote", messages) != 0) {
			messages.add("");
			messages.add(getString(R.string.reboot_failed));
			showAlert(TextUtils.join("\n", messages).trim());
		}

		mRootUtil.dispose();
	}

	private void reboot() {
		if (!startShell())
			return;

		List<String> messages = new LinkedList<String>();
		if (mRootUtil.execute("reboot", messages) != 0) {
			messages.add("");
			messages.add(getString(R.string.reboot_failed));
			showAlert(TextUtils.join("\n", messages).trim());
		}

		mRootUtil.dispose();
	}
}
