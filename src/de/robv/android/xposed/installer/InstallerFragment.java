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
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
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

	private ProgressDialog dlgProgress;
	private TextView txtAppProcessInstalledVersion, txtAppProcessLatestVersion;
	private TextView txtJarInstalledVersion, txtJarLatestVersion;
	private TextView txtInstallError, txtInstallMode;
	private Button btnInstall, btnUninstall, btnSoftReboot, btnReboot;

	private static final int INSTALL_MODE_NORMAL = 0;
	private static final int INSTALL_MODE_RECOVERY_AUTO = 1;
	private static final int INSTALL_MODE_RECOVERY_MANUAL = 2;

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();
		if (activity instanceof XposedDropdownNavActivity)
			((XposedDropdownNavActivity) activity).setNavItem(XposedDropdownNavActivity.TAB_INSTALL);

		dlgProgress = new ProgressDialog(activity);
		dlgProgress.setIndeterminate(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.tab_installer, container, false);

		txtAppProcessInstalledVersion = (TextView) v.findViewById(R.id.app_process_installed_version);
		txtAppProcessLatestVersion = (TextView) v.findViewById(R.id.app_process_latest_version);
		txtJarInstalledVersion = (TextView) v.findViewById(R.id.jar_installed_version);
		txtJarLatestVersion = (TextView) v.findViewById(R.id.jar_latest_version);

		txtInstallMode = (TextView) v.findViewById(R.id.framework_install_mode);
		txtInstallError = (TextView) v.findViewById(R.id.framework_install_errors);

		btnInstall = (Button) v.findViewById(R.id.btnInstall);
		btnUninstall = (Button) v.findViewById(R.id.btnUninstall);
		btnSoftReboot = (Button) v.findViewById(R.id.btnSoftReboot);
		btnReboot = (Button) v.findViewById(R.id.btnReboot);

		txtInstallMode.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity(), XposedInstallerActivity.class);
				intent.putExtra(XposedInstallerActivity.EXTRA_OPEN_TAB, XposedDropdownNavActivity.TAB_SETTINGS);
				startActivity(intent);
			}
		});

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

		refreshVersions();

		if (isCompatible) {
			btnInstall.setOnClickListener(new AsyncClickListener(btnInstall.getText()) {
				@Override
				public void onAsyncClick(View v) {
					final boolean success = install();
					getActivity().runOnUiThread(new Runnable() {
						@Override
						public void run() {
							refreshVersions();
							if (success)
								ModuleUtil.getInstance().updateModulesList(false);
						}
					});
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

		btnUninstall.setOnClickListener(new AsyncClickListener(btnUninstall.getText()) {
			@Override
			public void onAsyncClick(View v) {
				uninstall();
				getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						refreshVersions();
					}
				});
			}
		});
		btnReboot.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				areYouSure(R.string.reboot, new AsyncDialogClickListener(btnReboot.getText()) {
					@Override
					public void onAsyncClick(DialogInterface dialog, int which) {
						reboot(null);
					}
				});
			}
		});

		btnSoftReboot.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				areYouSure(R.string.soft_reboot, new AsyncDialogClickListener(btnSoftReboot.getText()) {
					@Override
					public void onAsyncClick(DialogInterface dialog, int which) {
						softReboot();
					}
				});
			}
		});

		if (!XposedApp.getPreferences().getBoolean("hide_install_warning", false)) {
			final View dontShowAgainView = inflater.inflate(R.layout.dialog_install_warning, null);
			new AlertDialog.Builder(getActivity())
			.setTitle(R.string.install_warning_title)
			.setView(dontShowAgainView)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					CheckBox checkBox = (CheckBox) dontShowAgainView.findViewById(android.R.id.checkbox);
					if (checkBox.isChecked())
						XposedApp.getPreferences().edit().putBoolean("hide_install_warning", true).commit();
				}
			})
			.setCancelable(false)
			.show();
		}

		return v;
	}

	@Override
	public void onResume() {
		super.onResume();

		SpannableStringBuilder installModeText = new SpannableStringBuilder(getString(R.string.settings_install_mode));
		installModeText.append(":\n");
		installModeText.setSpan(new StyleSpan(Typeface.BOLD), 0, installModeText.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
		installModeText.append(getInstallModeText());
		txtInstallMode.setText(installModeText);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mRootUtil.dispose();
	}

	private abstract class AsyncClickListener implements View.OnClickListener {
		private final CharSequence mProgressDlgText;

		public AsyncClickListener(CharSequence progressDlgText) {
			mProgressDlgText = progressDlgText;
		}

		@Override
		public final void onClick(final View v) {
			if (mProgressDlgText != null) {
				dlgProgress.setMessage(mProgressDlgText);
				dlgProgress.show();
			}
			new Thread() {
				public void run() {
					onAsyncClick(v);
					dlgProgress.dismiss();
				}
			}.start();
		}

		protected abstract void onAsyncClick(View v);
	}

	private abstract class AsyncDialogClickListener implements DialogInterface.OnClickListener {
		private final CharSequence mProgressDlgText;

		public AsyncDialogClickListener(CharSequence progressDlgText) {
			mProgressDlgText = progressDlgText;
		}

		@Override
		public void onClick(final DialogInterface dialog, final int which) {
			if (mProgressDlgText != null) {
				dlgProgress.setMessage(mProgressDlgText);
				dlgProgress.show();
			}
			new Thread() {
				public void run() {
					onAsyncClick(dialog, which);
					dlgProgress.dismiss();
				}
			}.start();
		}

		protected abstract void onAsyncClick(DialogInterface dialog, int which);
	}

	private void refreshVersions() {
		int appProcessInstalledVersion = getInstalledAppProcessVersion();
		int appProcessLatestVersion = getLatestAppProcessVersion();
		int jarInstalledVersion = getJarInstalledVersion();
		int jarLatestVersion = getJarLatestVersion();

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
	}

	private String versionToText(int version) {
		return (version == 0) ? getString(R.string.none) : Integer.toString(version);
	}

	private void showAlert(final String result) {
		if (Looper.myLooper() != Looper.getMainLooper()) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					showAlert(result);
				}
			});
			return;
		}

		AlertDialog dialog = new AlertDialog.Builder(getActivity())
		.setMessage(result)
		.setPositiveButton(android.R.string.ok, null)
		.create();
		dialog.show();
		TextView txtMessage = (TextView) dialog.findViewById(android.R.id.message);
		txtMessage.setTextSize(14);
	}

	private void areYouSure(int messageTextId, DialogInterface.OnClickListener yesHandler) {
		new AlertDialog.Builder(getActivity())
		.setTitle(messageTextId)
		.setMessage(R.string.areyousure)
		.setIconAttribute(android.R.attr.alertDialogIcon)
		.setPositiveButton(android.R.string.yes, yesHandler)
		.setNegativeButton(android.R.string.no, null)
		.create()
		.show();
	}

	private void showConfirmDialog(final String message, final DialogInterface.OnClickListener yesHandler,
			final DialogInterface.OnClickListener noHandler) {
		if (Looper.myLooper() != Looper.getMainLooper()) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					showConfirmDialog(message, yesHandler, noHandler);
				}
			});
			return;
		}

		AlertDialog dialog = new AlertDialog.Builder(getActivity())
		.setMessage(message)
		.setPositiveButton(android.R.string.yes, yesHandler)
		.setNegativeButton(android.R.string.no, noHandler)
		.create();
		dialog.show();
		TextView txtMessage = (TextView) dialog.findViewById(android.R.id.message);
		txtMessage.setTextSize(14);
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

	private static int getJarVersion(InputStream is) throws IOException {
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

	private int getInstallMode() {
		int mode = XposedApp.getPreferences().getInt("install_mode", INSTALL_MODE_NORMAL);
		if (mode < INSTALL_MODE_NORMAL || mode > INSTALL_MODE_RECOVERY_MANUAL)
			mode = INSTALL_MODE_NORMAL;
		return mode;
	}

	private String getInstallModeText() {
		final int installMode = getInstallMode();
		switch (installMode) {
			case INSTALL_MODE_NORMAL:
				return getString(R.string.install_mode_normal);
			case INSTALL_MODE_RECOVERY_AUTO:
				return getString(R.string.install_mode_recovery_auto);
			case INSTALL_MODE_RECOVERY_MANUAL:
				return getString(R.string.install_mode_recovery_manual);
		}
		throw new IllegalStateException("unknown install mode " + installMode);
	}

	private boolean install() {
		final int installMode = getInstallMode();

		if (!startShell())
			return false;

		List<String> messages = new LinkedList<String>();
		boolean showAlert = true;
		try {
			messages.add(getString(R.string.file_copying, "Xposed-Disabler-Recovery.zip"));
			if (AssetUtil.writeAssetToSdcardFile("Xposed-Disabler-Recovery.zip", 00644) == null) {
				messages.add("");
				messages.add(getString(R.string.file_extract_failed, "Xposed-Disabler-Recovery.zip"));
				return false;
			}

			File appProcessFile = AssetUtil.writeAssetToFile(APP_PROCESS_NAME, new File(XposedApp.BASE_DIR + "bin/app_process"), 00700);
			if (appProcessFile == null) {
				showAlert(getString(R.string.file_extract_failed, "app_process"));
				return false;
			}

			if (installMode == INSTALL_MODE_NORMAL) {
				// Normal installation
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

					mRootUtil.executeWithBusybox("sync", messages);
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

			} else if (installMode == INSTALL_MODE_RECOVERY_AUTO) {
				if (!prepareAutoFlash(messages, "Xposed-Installer-Recovery.zip"))
					return false;

			} else if (installMode == INSTALL_MODE_RECOVERY_MANUAL) {
				if (!prepareManualFlash(messages, "Xposed-Installer-Recovery.zip"))
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

			messages.add(getString(R.string.file_copying, "XposedBridge.jar"));
			File jarFile = AssetUtil.writeAssetToFile("XposedBridge.jar", new File(JAR_PATH_NEWVERSION), 00644);
			if (jarFile == null) {
				messages.add("");
				messages.add(getString(R.string.file_extract_failed, "XposedBridge.jar"));
				return false;
			}

			mRootUtil.executeWithBusybox("sync", messages);

			showAlert = false;
			messages.add("");
			if (installMode == INSTALL_MODE_NORMAL)
				offerReboot(messages);
			else
				offerRebootToRecovery(messages, "Xposed-Installer-Recovery.zip", installMode);

			return true;

		} finally {
			AssetUtil.removeBusybox();

			if (showAlert)
				showAlert(TextUtils.join("\n", messages).trim());
		}
	}

	private boolean uninstall() {
		final int installMode = getInstallMode();

		new File(JAR_PATH_NEWVERSION).delete();
		new File(JAR_PATH).delete();
		new File(XposedApp.BASE_DIR + "bin/app_process").delete();

		if (!startShell())
			return false;

		List<String> messages = new LinkedList<String>();
		boolean showAlert = true;
		try {
			if (installMode == INSTALL_MODE_NORMAL) {
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
				// Might help on some SELinux-enforced ROMs, shouldn't hurt on others
				mRootUtil.execute("/system/bin/restorecon /system/bin/app_process", null);

			} else if (installMode == INSTALL_MODE_RECOVERY_AUTO) {
				if (!prepareAutoFlash(messages, "Xposed-Disabler-Recovery.zip"))
					return false;

			} else if (installMode == INSTALL_MODE_RECOVERY_MANUAL) {
				if (!prepareManualFlash(messages, "Xposed-Disabler-Recovery.zip"))
					return false;
			}

			showAlert = false;
			messages.add("");
			if (installMode == INSTALL_MODE_NORMAL)
				offerReboot(messages);
			else
				offerRebootToRecovery(messages, "Xposed-Disabler-Recovery.zip", installMode);

			return true;

		} finally {
			AssetUtil.removeBusybox();

			if (showAlert)
				showAlert(TextUtils.join("\n", messages).trim());
		}
	}

	private boolean prepareAutoFlash(List<String> messages, String file) {
		if (mRootUtil.execute("ls /cache/recovery", null) != 0) {
			messages.add(getString(R.string.file_creating_directory, "/cache/recovery"));
			if (mRootUtil.executeWithBusybox("mkdir /cache/recovery", messages) != 0) {
				messages.add("");
				messages.add(getString(R.string.file_create_directory_failed, "/cache/recovery"));
				return false;
			}
		}

		messages.add(getString(R.string.file_copying, file));
		File tempFile = AssetUtil.writeAssetToCacheFile(file, 00644);
		if (tempFile == null) {
			messages.add("");
			messages.add(getString(R.string.file_extract_failed, file));
			return false;
		}

		if (mRootUtil.executeWithBusybox("cp -a " + tempFile.getAbsolutePath() + " /cache/recovery/" + file, messages) != 0) {
			messages.add("");
			messages.add(getString(R.string.file_copy_failed, file, "/cache"));
			tempFile.delete();
			return false;
		}

		tempFile.delete();

		messages.add(getString(R.string.file_writing_recovery_command));
		if (mRootUtil.execute("echo \"--update_package=/cache/recovery/" + file + "\n--show_text\" > /cache/recovery/command", messages) != 0) {
			messages.add("");
			messages.add(getString(R.string.file_writing_recovery_command_failed));
			return false;
		}

		return true;
	}

	private boolean prepareManualFlash(List<String> messages, String file) {
		messages.add(getString(R.string.file_copying, file));
		if (AssetUtil.writeAssetToSdcardFile(file, 00644) == null) {
			messages.add("");
			messages.add(getString(R.string.file_extract_failed, file));
			return false;
		}

		return true;
	}

	private void offerReboot(List<String> messages) {
		messages.add(getString(R.string.file_done));
		messages.add("");
		messages.add(getString(R.string.reboot_confirmation));
		showConfirmDialog(TextUtils.join("\n", messages).trim(),
			new AsyncDialogClickListener(getString(R.string.reboot)) {
				@Override
				protected void onAsyncClick(DialogInterface dialog, int which) {
					reboot(null);
				}
			}, null);
	}

	private void offerRebootToRecovery(List<String> messages, final String file, final int installMode) {
		if (installMode == INSTALL_MODE_RECOVERY_AUTO)
			messages.add(getString(R.string.auto_flash_note, file));
		else
			messages.add(getString(R.string.manual_flash_note, file));

		messages.add("");
		messages.add(getString(R.string.reboot_recovery_confirmation));
		showConfirmDialog(TextUtils.join("\n", messages).trim(),
			new AsyncDialogClickListener(getString(R.string.reboot)) {
				@Override
				protected void onAsyncClick(DialogInterface dialog, int which) {
					reboot("recovery");
				}
			},
			new AsyncDialogClickListener(null) {
				@Override
				protected void onAsyncClick(DialogInterface dialog, int which) {
					if (installMode == INSTALL_MODE_RECOVERY_AUTO) {
						// clean up to avoid unwanted flashing
						mRootUtil.executeWithBusybox("rm /cache/recovery/command", null);
						mRootUtil.executeWithBusybox("rm /cache/recovery/" + file, null);
						AssetUtil.removeBusybox();
					}
				}
			});
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
	}

	private void reboot(String mode) {
		if (!startShell())
			return;

		List<String> messages = new LinkedList<String>();

		String command = "reboot";
		if (mode != null) {
			command += " " + mode;
			if (mode.equals("recovery"))
				// create a flag used by some kernels to boot into recovery
				mRootUtil.executeWithBusybox("touch /cache/recovery/boot", messages);
		}

		if (mRootUtil.executeWithBusybox(command, messages) != 0) {
			messages.add("");
			messages.add(getString(R.string.reboot_failed));
			showAlert(TextUtils.join("\n", messages).trim());
		}
		AssetUtil.removeBusybox();
	}
}
