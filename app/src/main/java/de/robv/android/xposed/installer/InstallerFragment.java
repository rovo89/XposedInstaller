package de.robv.android.xposed.installer;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v7.widget.CardView;
import android.system.Os;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.installer.util.AssetUtil;
import de.robv.android.xposed.installer.util.DownloadsUtil;
import de.robv.android.xposed.installer.util.NavUtil;
import de.robv.android.xposed.installer.util.NotificationUtil;
import de.robv.android.xposed.installer.util.RootUtil;
import de.robv.android.xposed.installer.util.ThemeUtil;
import de.robv.android.xposed.installer.util.XposedZip;

import static android.content.Context.MODE_PRIVATE;
import static de.robv.android.xposed.installer.XposedApp.WRITE_EXTERNAL_PERMISSION;
import static de.robv.android.xposed.installer.util.XposedZip.Installer;
import static de.robv.android.xposed.installer.util.XposedZip.Uninstaller;

public class InstallerFragment extends Fragment
		implements DownloadsUtil.DownloadFinishedCallback {
	public static final String JAR_PATH = "/system/framework/XposedBridge.jar";
	private static final int INSTALL_MODE_NORMAL = 0;
	private static final int INSTALL_MODE_RECOVERY_AUTO = 1;
	private static final int INSTALL_MODE_RECOVERY_MANUAL = 2;
	private static String JSON_LINK = "https://raw.githubusercontent.com/DVDAndroid/XposedInstaller/material/app/xposed.json";
	private static List<String> messages = new LinkedList<>();
	private static ArrayList<Installer> installers;
	private static ArrayList<Uninstaller> uninstallers;
	private final LinkedList<String> mCompatibilityErrors = new LinkedList<>();
	private String APP_PROCESS_NAME = null;
	private RootUtil mRootUtil = new RootUtil();
	private boolean mHadSegmentationFault = false;
	private MaterialDialog.Builder dlgProgress;
	private TextView txtInstallError, txtKnownIssue;
	private Button btnInstall, btnUninstall;
	private ProgressBar mInstallersLoading;
	private Spinner mInstallersChooser;
	private ProgressBar mUninstallersLoading;
	private Spinner mUninstallersChooser;
	private ImageView mInfoInstaller, mInfoUninstaller;
	private String newApkVersion = XposedApp.THIS_APK_VERSION;
	private String newApkLink;
	private String newApkChangelog;
	private CardView mUpdateView;
	private Button mUpdateButton;
	private TextView mInstallForbidden;
	private ImageView mInfoUpdate;
	private Button mClickedButton;

	private static int extractIntPart(String str) {
		int result = 0, length = str.length();
		for (int offset = 0; offset < length; offset++) {
			char c = str.charAt(offset);
			if ('0' <= c && c <= '9')
				result = result * 10 + (c - '0');
			else
				break;
		}
		return result;
	}

	private static boolean checkClassExists(String className) {
		try {
			Class.forName(className);
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	public static ArrayList<Installer> getInstallersBySdk(int sdk) {
		ArrayList<Installer> list = new ArrayList<>();
		for (Installer i : installers) {
			if (i.sdk == sdk)
				list.add(i);
		}

		if (list.size() == 0) {
			list.add(new Installer());
		}
		return list;
	}

	/*
	 * public static ArrayList<Installer> getInstallersBySdkAndArchitecture( int
	 * sdk, String architecture) { ArrayList<Installer> list = new
	 * ArrayList<>(); ArrayList<Installer> list2 = new ArrayList<>(); for
	 * (Installer i : installers) { if (i.sdk == sdk) list.add(i); } for
	 * (Installer i : list) { if (i.architecture.equals(architecture))
	 * list2.add(i); } return list2; }
	 *
	 * public static ArrayList<Uninstaller> getUninstallersByArchitecture(
	 * String architecture) { ArrayList<Uninstaller> list = new ArrayList<>();
	 * for (Uninstaller u : uninstallers) { if
	 * (u.architecture.equals(architecture)) list.add(u); } return list; }
	 */

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();

		dlgProgress = new MaterialDialog.Builder(activity).progress(true, 0);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.tab_installer, container, false);

		txtInstallError = (TextView) v
				.findViewById(R.id.framework_install_errors);
		txtKnownIssue = (TextView) v.findViewById(R.id.framework_known_issue);

		btnInstall = (Button) v.findViewById(R.id.btnInstall);
		btnUninstall = (Button) v.findViewById(R.id.btnUninstall);

		mInstallersLoading = (ProgressBar) v
				.findViewById(R.id.loadingInstallers);
		mUninstallersLoading = (ProgressBar) v
				.findViewById(R.id.loadingUninstallers);
		mInstallersChooser = (Spinner) v.findViewById(R.id.chooserInstallers);
		mUninstallersChooser = (Spinner) v
				.findViewById(R.id.chooserUninstallers);
		mUpdateView = (CardView) v.findViewById(R.id.updateView);
		mUpdateButton = (Button) v.findViewById(R.id.updateButton);

		mInfoInstaller = (ImageView) v.findViewById(R.id.infoInstaller);
		mInfoUninstaller = (ImageView) v.findViewById(R.id.infoUninstaller);

		mInstallForbidden = (TextView) v
				.findViewById(R.id.installationForbidden);
		mInfoUpdate = (ImageView) v.findViewById(R.id.infoUpdate);

		String installedXposedVersion = XposedApp.getXposedProp()
				.get("version");

		if (Build.VERSION.SDK_INT >= 21) {
			if (installedXposedVersion == null) {
				txtInstallError.setText(R.string.installation_lollipop);
				txtInstallError
						.setTextColor(getResources().getColor(R.color.warning));
			} else {
				int installedXposedVersionInt = extractIntPart(
						installedXposedVersion);
				if (installedXposedVersionInt == XposedApp.getXposedVersion()) {
					txtInstallError
							.setText(getString(R.string.installed_lollipop,
									installedXposedVersion));
					txtInstallError.setTextColor(
							getResources().getColor(R.color.darker_green));
				} else {
					txtInstallError.setText(
							getString(R.string.installed_lollipop_inactive,
									installedXposedVersion));
					txtInstallError.setTextColor(
							getResources().getColor(R.color.warning));
				}
			}
		} else {
			if (XposedApp.getXposedVersion() != 0) {
				txtInstallError.setText(getString(R.string.installed_lollipop,
						XposedApp.getXposedVersion()));
				txtInstallError.setTextColor(
						getResources().getColor(R.color.darker_green));
			} else {
				txtInstallError
						.setText(getString(R.string.not_installed_no_lollipop));
				txtInstallError
						.setTextColor(getResources().getColor(R.color.warning));
			}
		}

		txtInstallError.setVisibility(View.VISIBLE);

		if (!XposedApp.getPreferences().getBoolean("hide_install_warning",
				false)) {
			final View dontShowAgainView = inflater
					.inflate(R.layout.dialog_install_warning, null);

			new MaterialDialog.Builder(getActivity())
					.title(R.string.install_warning_title)
					.customView(dontShowAgainView, false)
					.positiveText(android.R.string.ok)
					.callback(new MaterialDialog.ButtonCallback() {
						@Override
						public void onPositive(MaterialDialog dialog) {
							super.onPositive(dialog);
							CheckBox checkBox = (CheckBox) dontShowAgainView
									.findViewById(android.R.id.checkbox);
							if (checkBox.isChecked())
								XposedApp.getPreferences().edit().putBoolean(
										"hide_install_warning", true).apply();
						}
					}).cancelable(false).show();
		}

		new JSONParser(JSON_LINK).execute();

		mInfoInstaller.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Installer selectedInstaller = (Installer) mInstallersChooser
						.getSelectedItem();
				String s = getString(R.string.infoInstaller,
						selectedInstaller.name, selectedInstaller.sdk,
						selectedInstaller.architecture,
						selectedInstaller.version);

				new MaterialDialog.Builder(getContext()).title(R.string.info)
						.content(s).positiveText(android.R.string.ok).show();
			}
		});
		mInfoUninstaller.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Uninstaller selectedUninstaller = (Uninstaller) mUninstallersChooser
						.getSelectedItem();
				String s = getString(R.string.infoUninstaller,
						selectedUninstaller.name,
						selectedUninstaller.architecture,
						selectedUninstaller.date);

				new MaterialDialog.Builder(getContext()).title(R.string.info)
						.content(s).positiveText(android.R.string.ok).show();
			}
		});

		btnInstall.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mClickedButton = btnInstall;
				if (checkPermissions())
					return;

				areYouSure(R.string.warningArchitecture,
						new MaterialDialog.ButtonCallback() {
					@Override
					public void onPositive(MaterialDialog dialog) {
						super.onPositive(dialog);

						Installer selectedInstaller = (Installer) mInstallersChooser
								.getSelectedItem();

						DownloadsUtil.add(getContext(), selectedInstaller.name,
								selectedInstaller.link, InstallerFragment.this,
								DownloadsUtil.MIME_TYPES.ZIP, true);
					}
				});
			}
		});

		btnUninstall.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mClickedButton = btnUninstall;
				if (checkPermissions())
					return;

				areYouSure(R.string.warningArchitecture,
						new MaterialDialog.ButtonCallback() {
					@Override
					public void onPositive(MaterialDialog dialog) {
						super.onPositive(dialog);

						Uninstaller selectedUninstaller = (Uninstaller) mUninstallersChooser
								.getSelectedItem();

						DownloadsUtil.add(getContext(),
								selectedUninstaller.name,
								selectedUninstaller.link,
								InstallerFragment.this,
								DownloadsUtil.MIME_TYPES.ZIP, true);
					}
				});
			}
		});

		mUpdateButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mClickedButton = mUpdateButton;
				if (checkPermissions())
					return;

				DownloadsUtil.add(getContext(), "XposedInstaller_by_dvdandroid",
						newApkLink,
						new DownloadsUtil.DownloadFinishedCallback() {
					@Override
					public void onDownloadFinished(Context context,
							DownloadsUtil.DownloadInfo info) {
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setDataAndType(
								Uri.fromFile(new File(Environment
										.getExternalStorageDirectory()
										.getAbsolutePath()
										+ "/XposedInstaller/XposedInstaller_by_dvdandroid.apk")),
								DownloadsUtil.MIME_TYPE_APK);
						intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						context.startActivity(intent);
					}
				}, DownloadsUtil.MIME_TYPES.APK, true);
			}
		});

		return v;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_installer, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
			case R.id.help:
				new MaterialDialog.Builder(getContext()).title(R.string.help)
						.content(R.string.helpChoose)
						.positiveText(android.R.string.ok).show();
				break;
			case R.id.installation_mode:
				Intent intent = new Intent(getActivity(),
						SettingsActivity.class);
				startActivity(intent);
				break;
			case R.id.reboot:
				areYouSure(R.string.reboot,
						new MaterialDialog.ButtonCallback() {
							@Override
							public void onPositive(MaterialDialog dialog) {
								super.onPositive(dialog);
								reboot(null);
							}
						});
				break;
			case R.id.soft_reboot:
				areYouSure(R.string.reboot,
						new MaterialDialog.ButtonCallback() {
							@Override
							public void onPositive(MaterialDialog dialog) {
								super.onPositive(dialog);
								softReboot();
							}
						});
				break;
			case R.id.reboot_recovery:
				areYouSure(R.string.reboot_recovery,
						new MaterialDialog.ButtonCallback() {
							@Override
							public void onPositive(MaterialDialog dialog) {
								super.onPositive(dialog);
								reboot("recovery");
							}
						});
				break;
		}

		return super.onOptionsItemSelected(item);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public void performFileSearch() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("application/zip");
		startActivityForResult(intent, 123);
	}

	@Override
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public void onActivityResult(int requestCode, int resultCode,
			Intent resultData) {
		if (requestCode == 123 && resultCode == Activity.RESULT_OK) {
			if (resultData != null) {
				Uri uri = resultData.getData();
				String resolved = null;
				try (ParcelFileDescriptor fd = getActivity()
						.getContentResolver().openFileDescriptor(uri, "r")) {
					final File procfsFdFile = new File(
							"/proc/self/fd/" + fd.getFd());

					resolved = Os.readlink(procfsFdFile.getAbsolutePath());

					if (TextUtils.isEmpty(resolved) || resolved.charAt(0) != '/'
							|| resolved.startsWith("/proc/")
							|| resolved.startsWith("/fd/"))
						;
				} catch (Exception errnoe) {
					Log.e(XposedApp.TAG, "ReadError");
				}
				mRootUtil.execute("cp " + resolved + " /cache/xposed.zip",
						messages);
				installXposedZip();
			}
		}
	}

	private void installXposedZip() {
		mRootUtil.execute(
				"echo 'install /cache/xposed.zip' >/cache/recovery/openrecoveryscript ",
				messages);
		mRootUtil.execute("sync", messages);
		reboot("recovery");
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
			@NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions,
				grantResults);
		if (requestCode == WRITE_EXTERNAL_PERMISSION) {
			if (grantResults.length == 1
					&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				if (mClickedButton != null) {
					new Handler().postDelayed(new Runnable() {
						@Override
						public void run() {
							mClickedButton.performClick();
						}
					}, 500);
				}
			} else {
				Toast.makeText(getActivity(), R.string.permissionNotGranted,
						Toast.LENGTH_LONG).show();
			}
		}
	}

	private boolean checkPermissions() {
		if (ActivityCompat.checkSelfPermission(getActivity(),
				Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			requestPermissions(
					new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
					WRITE_EXTERNAL_PERMISSION);
			return true;
		}
		return false;
	}

	@Override
	public void onResume() {
		super.onResume();
		NotificationUtil.cancel(NotificationUtil.NOTIFICATION_MODULES_UPDATED);
		mHadSegmentationFault = false;
		refreshKnownIssue();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mRootUtil.dispose();
	}

	private String versionToText(int version) {
		return (version == 0) ? getString(R.string.none)
				: Integer.toString(version);
	}

	private void refreshKnownIssue() {
		String issueName = null;
		String issueLink = null;

		if (new File("/system/framework/core.jar.jex").exists()) {
			issueName = "Aliyun OS";
			issueLink = "http://forum.xda-developers.com/showpost.php?p=52289793&postcount=5";

		} else if (new File("/data/miui/DexspyInstaller.jar").exists()
				|| checkClassExists("miui.dexspy.DexspyInstaller")) {
			issueName = "MIUI/Dexspy";
			issueLink = "http://forum.xda-developers.com/showpost.php?p=52291098&postcount=6";

		} else if (mHadSegmentationFault) {
			issueName = "Segmentation fault";
			issueLink = "http://forum.xda-developers.com/showpost.php?p=52292102&postcount=7";

		} else
			if (checkClassExists("com.huawei.android.content.res.ResourcesEx")
					|| checkClassExists("android.content.res.NubiaResources")) {
			issueName = "Resources subclass";
			issueLink = "http://forum.xda-developers.com/showpost.php?p=52801382&postcount=8";
		}

		if (issueName != null) {
			final String issueLinkFinal = issueLink;
			txtKnownIssue.setText(
					getString(R.string.install_known_issue, issueName));
			txtKnownIssue.setVisibility(View.VISIBLE);
			txtKnownIssue.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					NavUtil.startURL(getActivity(), issueLinkFinal);
				}
			});

			txtInstallError.setTextColor(ThemeUtil.getThemeColor(getActivity(),
					android.R.attr.textColorTertiary));
		} else {
			txtKnownIssue.setVisibility(View.GONE);
		}
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

		MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
				.content(result).positiveText(android.R.string.ok).build();
		dialog.show();

		TextView txtMessage = (TextView) dialog
				.findViewById(android.R.id.message);
		try {
			txtMessage.setTextSize(14);
		} catch (NullPointerException ignored) {
		}

		mHadSegmentationFault = result.toLowerCase(Locale.US)
				.contains("segmentation fault");
		refreshKnownIssue();
	}

	private void areYouSure(int contentTextId,
			MaterialDialog.ButtonCallback yesHandler) {
		new MaterialDialog.Builder(getActivity()).title(R.string.areyousure)
				.content(contentTextId)
				.iconAttr(android.R.attr.alertDialogIcon)
				.positiveText(android.R.string.yes)
				.negativeText(android.R.string.no).callback(yesHandler).show();
	}

	private void showConfirmDialog(final String message,
			final MaterialDialog.ButtonCallback callback) {
		if (Looper.myLooper() != Looper.getMainLooper()) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					showConfirmDialog(message, callback);
				}
			});
			return;
		}

		MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
				.content(message).positiveText(android.R.string.yes)
				.negativeText(android.R.string.no).callback(callback).build();

		TextView txtMessage = (TextView) dialog
				.findViewById(android.R.id.message);
		txtMessage.setTextSize(14);

		mHadSegmentationFault = message.toLowerCase(Locale.US)
				.contains("segmentation fault");
		refreshKnownIssue();
	}

	private boolean checkCompatibility() {
		mCompatibilityErrors.clear();
		return checkAppProcessCompatibility();
	}

	private boolean checkAppProcessCompatibility() {
		try {
			if (APP_PROCESS_NAME == null)
				return false;

			File testFile = AssetUtil.writeAssetToCacheFile(APP_PROCESS_NAME,
					"app_process", 00700);
			if (testFile == null) {
				mCompatibilityErrors
						.add("could not write app_process to cache");
				return false;
			}

			Process p = Runtime.getRuntime().exec(new String[] {
					testFile.getAbsolutePath(), "--xposedversion" });

			BufferedReader stdout = new BufferedReader(
					new InputStreamReader(p.getInputStream()));
			String result = stdout.readLine();
			stdout.close();

			BufferedReader stderr = new BufferedReader(
					new InputStreamReader(p.getErrorStream()));
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

	private boolean startShell() {
		if (mRootUtil.startShell())
			return true;

		showAlert(getString(R.string.root_failed));
		return false;
	}

	private int getInstallMode() {
		int mode = XposedApp.getPreferences().getInt("install_mode",
				INSTALL_MODE_NORMAL);
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

	private boolean prepareAutoFlash(List<String> messages, String file) {
		if (mRootUtil.execute("ls /cache/recovery", null) != 0) {
			messages.add(getString(R.string.file_creating_directory,
					"/cache/recovery"));
			if (mRootUtil.executeWithBusybox("mkdir /cache/recovery",
					messages) != 0) {
				messages.add("");
				messages.add(getString(R.string.file_create_directory_failed,
						"/cache/recovery"));
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

		if (mRootUtil.executeWithBusybox("cp -a " + tempFile.getAbsolutePath()
				+ " /cache/recovery/" + file, messages) != 0) {
			messages.add("");
			messages.add(getString(R.string.file_copy_failed, file, "/cache"));
			tempFile.delete();
			return false;
		}

		tempFile.delete();

		messages.add(getString(R.string.file_writing_recovery_command));
		if (mRootUtil.execute(
				"echo \"--update_package=/cache/recovery/" + file
						+ "\n--show_text\" > /cache/recovery/command",
				messages) != 0) {
			messages.add("");
			messages.add(
					getString(R.string.file_writing_recovery_command_failed));
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
				new MaterialDialog.ButtonCallback() {
					@Override
					public void onPositive(MaterialDialog dialog) {
						super.onPositive(dialog);
						reboot(null);
					}
				});
	}

	private void offerRebootToRecovery(List<String> messages, final String file,
			final int installMode) {
		if (installMode == INSTALL_MODE_RECOVERY_AUTO)
			messages.add(getString(R.string.auto_flash_note, file));
		else
			messages.add(getString(R.string.manual_flash_note, file));

		messages.add("");
		messages.add(getString(R.string.reboot_recovery_confirmation));
		showConfirmDialog(TextUtils.join("\n", messages).trim(),
				new MaterialDialog.ButtonCallback() {
					@Override
					public void onPositive(MaterialDialog dialog) {
						super.onPositive(dialog);
						reboot("recovery");
					}

					@Override
					public void onNegative(MaterialDialog dialog) {
						super.onNegative(dialog);
						if (installMode == INSTALL_MODE_RECOVERY_AUTO) {
							// clean up to avoid unwanted flashing
							mRootUtil.executeWithBusybox(
									"rm /cache/recovery/command", null);
							mRootUtil.executeWithBusybox(
									"rm /cache/recovery/" + file, null);
							AssetUtil.removeBusybox();
						}
					}
				}

		);
	}

	private void softReboot() {
		if (!startShell())
			return;

		List<String> messages = new LinkedList<String>();
		if (mRootUtil.execute(
				"setprop ctl.restart surfaceflinger; setprop ctl.restart zygote",
				messages) != 0) {
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
				mRootUtil.executeWithBusybox("touch /cache/recovery/boot",
						messages);
		}

		if (mRootUtil.executeWithBusybox(command, messages) != 0) {
			messages.add("");
			messages.add(getString(R.string.reboot_failed));
			showAlert(TextUtils.join("\n", messages).trim());
		}
		AssetUtil.removeBusybox();
	}

	@Override
	public void onDownloadFinished(final Context context,
			DownloadsUtil.DownloadInfo info) {
		Toast.makeText(context,
				getString(R.string.downloadZipOk, info.localFilename),
				Toast.LENGTH_LONG).show();

		if (getInstallMode() == INSTALL_MODE_RECOVERY_MANUAL)
			return;

		areYouSure(R.string.install_warning,
				new MaterialDialog.ButtonCallback() {
					@Override
					public void onPositive(MaterialDialog dialog) {
						super.onPositive(dialog);
						Toast.makeText(context, R.string.selectFile,
								Toast.LENGTH_LONG).show();

						performFileSearch();
					}
				});
	}

	private abstract class AsyncClickListener implements View.OnClickListener {
		private final CharSequence mProgressDlgText;

		public AsyncClickListener(CharSequence progressDlgText) {
			mProgressDlgText = progressDlgText;
		}

		@Override
		public final void onClick(final View v) {
			if (mProgressDlgText != null) {
				dlgProgress.content(mProgressDlgText);
				dlgProgress.show();
			}
			new Thread() {
				public void run() {
					onAsyncClick(v);
					dlgProgress.build().dismiss();
				}
			}.start();
		}

		protected abstract void onAsyncClick(View v);
	}

	private abstract class AsyncDialogClickListener
			implements DialogInterface.OnClickListener {
		private final CharSequence mProgressDlgText;

		public AsyncDialogClickListener(CharSequence progressDlgText) {
			mProgressDlgText = progressDlgText;
		}

		@Override
		public void onClick(final DialogInterface dialog, final int which) {
			if (mProgressDlgText != null) {
				dlgProgress.content(mProgressDlgText);
				dlgProgress.show();
			}
			new Thread() {
				public void run() {
					onAsyncClick(dialog, which);
					dlgProgress.build().dismiss();
				}
			}.start();
		}

		protected abstract void onAsyncClick(DialogInterface dialog, int which);
	}

	private class JSONParser extends AsyncTask<Void, Void, Boolean> {
		private URL mUrl;

		public JSONParser(String mUrl) {
			try {
				this.mUrl = new URL(mUrl);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			mInstallersLoading.setVisibility(View.VISIBLE);
			mUninstallersLoading.setVisibility(View.VISIBLE);
			mInfoInstaller.setVisibility(View.GONE);
			mInfoUninstaller.setVisibility(View.GONE);
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			try {
				HttpURLConnection c = (HttpURLConnection) mUrl.openConnection();
				c.setRequestMethod("GET");
				c.setDoOutput(true);
				c.connect();

				BufferedReader br = new BufferedReader(
						new InputStreamReader(c.getInputStream()));
				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = br.readLine()) != null) {
					sb.append(line).append("\n");
				}
				br.close();

				JSONObject json = new JSONObject(sb.toString());
				JSONArray installerArray = json.getJSONArray("installer");
				JSONArray uninstallerArray = json.getJSONArray("uninstaller");

				installers = new ArrayList<>();
				uninstallers = new ArrayList<>();

				for (int i = 0; i < installerArray.length(); i++) {
					JSONObject jsonObject = installerArray.getJSONObject(i);

					String link = jsonObject.getString("link");
					String name = jsonObject.getString("name");
					String architecture = jsonObject.getString("architecture");
					String version = jsonObject.getString("version");
					int sdk = jsonObject.getInt("sdk");

					installers.add(new XposedZip.Installer(link, name,
							architecture, sdk, version));
				}

				if (Build.VERSION.SDK_INT >= 21) {
					for (int i = 0; i < uninstallerArray.length(); i++) {
						JSONObject jsonObject = uninstallerArray
								.getJSONObject(i);

						String link = jsonObject.getString("link");
						String name = jsonObject.getString("name");
						String architecture = jsonObject
								.getString("architecture");
						String date = jsonObject.getString("date");

						uninstallers.add(new Uninstaller(link, name,
								architecture, date));
					}
				} else {
					uninstallers.add(new Uninstaller());
				}

				newApkVersion = json.getJSONObject("apk").getString("version");
				newApkLink = json.getJSONObject("apk").getString("link");
				newApkChangelog = json.getJSONObject("apk")
						.getString("changelog");
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			btnInstall.setEnabled(result);
			btnUninstall.setEnabled(result);

			mInstallersLoading.setVisibility(View.GONE);
			mUninstallersLoading.setVisibility(View.GONE);

			int i = result ? View.VISIBLE : View.GONE;

			mInstallersChooser.setVisibility(i);
			mUninstallersChooser.setVisibility(i);

			mInfoInstaller.setVisibility(i);
			mInfoUninstaller.setVisibility(i);

			if (Build.VERSION.SDK_INT < 21) {
				btnInstall.setEnabled(false);
				btnUninstall.setEnabled(false);
				mInfoInstaller.setEnabled(false);
				mInfoUninstaller.setEnabled(false);
				mInstallersChooser.setEnabled(false);
				mUninstallersChooser.setEnabled(false);

				mInstallForbidden.setVisibility(View.VISIBLE);
			}

			try {

				if (!result) {
					Toast.makeText(getContext(), R.string.loadingError,
							Toast.LENGTH_LONG).show();
					return;
				}

				String arch = System.getProperty("os.arch");
				int archPos = 0;
				if (arch.contains("64")) {
					archPos = 1;
				} else if (arch.contains("86")) {
					archPos = 2;
				}

				mInstallersChooser
						.setAdapter(new XposedZip.MyAdapter<>(getContext(),
								getInstallersBySdk(Build.VERSION.SDK_INT)));
				mInstallersChooser.setSelection(archPos);

				mUninstallersChooser.setAdapter(
						new XposedZip.MyAdapter<>(getContext(), uninstallers));
				mUninstallersChooser.setSelection(archPos);

				if (newApkChangelog != null) {
					mInfoUpdate.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							new MaterialDialog.Builder(getContext())
									.title(R.string.changes)
									.content(Html.fromHtml(newApkChangelog))
									.positiveText(android.R.string.ok).show();
						}
					});
				} else {
					mInfoUpdate.setVisibility(View.GONE);
				}

				if (newApkVersion == null)
					return;

				SharedPreferences prefs = null;
				try {
					prefs = getContext().getSharedPreferences(
							getContext().getPackageName() + "_preferences",
							MODE_PRIVATE);

					prefs.edit().putString("changelog_" + newApkVersion,
							newApkChangelog).apply();
				} catch (NullPointerException ignored) {
				}

				BigInteger a = new BigInteger(XposedApp.THIS_APK_VERSION);
				BigInteger b = new BigInteger(newApkVersion);

				if (a.compareTo(b) == -1) {
					mUpdateView.setVisibility(View.VISIBLE);
				}
			} catch (NullPointerException ignored) {
			}
		}
	}
}