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

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
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

		txtAppProcessInstalledVersion.setText(getInstalledAppProcessVersion());
		txtAppProcessLatestVersion.setText(getLatestAppProcessVersion());
		txtJarInstalledVersion.setText(getJarInstalledVersion());
		txtJarLatestVersion.setText(getJarLatestVersion());
		
		final Button btnInstall = (Button) v.findViewById(R.id.btnInstall);
		final Button btnUninstall = (Button) v.findViewById(R.id.btnUninstall);
		final Button btnCleanup = (Button) v.findViewById(R.id.btnCleanup);
		final Button btnReboot = (Button) v.findViewById(R.id.btnReboot);
		
		btnInstall.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showAlert(install());
				txtAppProcessInstalledVersion.setText(getInstalledAppProcessVersion());
				txtJarInstalledVersion.setText(getJarInstalledVersion());
			}
		});
		
		btnUninstall.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showAlert(uninstall());
				txtAppProcessInstalledVersion.setText(getInstalledAppProcessVersion());
				txtJarInstalledVersion.setText(getJarInstalledVersion());
			}
		});
		btnCleanup.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				areYouSure(R.string.cleanup, new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						showAlert(cleanup());
						txtAppProcessInstalledVersion.setText(getInstalledAppProcessVersion());
						txtJarInstalledVersion.setText(getJarInstalledVersion());
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
	
	private String getInstalledAppProcessVersion() {
		try {
			Process p = Runtime.getRuntime().exec(new String[] { "strings", "/system/bin/app_process" });
			return getAppProcessVersion(p.getInputStream());
		} catch (IOException e) {
			return getString(R.string.none);
		}
	}
	
	private String getLatestAppProcessVersion() {
		try {
			return getAppProcessVersion(getActivity().getAssets().open("app_process"));
		} catch (IOException e) {
			return getString(R.string.none);
		}
	}
	
	private String getAppProcessVersion(InputStream is) throws IOException {
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
		return getString(R.string.none);
	}
	
	private String getJarInstalledVersion() {
		try {
			if (new File("/data/xposed/XposedBridge.jar.newversion").exists())
				return getJarVersion(new FileInputStream("/data/xposed/XposedBridge.jar.newversion"));
			else
				return getJarVersion(new FileInputStream("/data/xposed/XposedBridge.jar"));
		} catch (IOException e) {
			return getString(R.string.none);
		}
	}
	
	private String getJarLatestVersion() {
		try {
			return getJarVersion(getActivity().getAssets().open("XposedBridge.jar"));
		} catch (IOException e) {
			return getString(R.string.none);
		}
	}
	
	private String getJarVersion(InputStream is) throws IOException {
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
		return getString(R.string.none);
	}
	
	private String install() {
		File appProcessFile = writeAssetToFile("app_process");
		if (appProcessFile == null)
			return "Could not find asset \"app_process\"";
		
		File jarFile = writeAssetToFile("XposedBridge.jar");
		if (jarFile == null)
			return "Could not find asset \"XposedBridge.jar\"";
		
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
	
	private String reboot() {
		return executeScript("reboot.sh");
	}
	
	private String executeScript(String name) {
		File scriptFile = writeAssetToFile(name);
		if (scriptFile == null)
			return "Could not find asset \"" + name + "\"";
		
		scriptFile.setReadable(true, false);
		scriptFile.setExecutable(true, false);
		
		try {
			Process p = Runtime.getRuntime().exec(new String[] { "su", "-c", scriptFile.getAbsolutePath() + " 2>&1" });
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
		}
	}
	
	private File writeAssetToFile(String name) {
		File file = null;
		try {
			InputStream in = getActivity().getAssets().open(name);
			file = new File(getActivity().getCacheDir(), name);
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
}
