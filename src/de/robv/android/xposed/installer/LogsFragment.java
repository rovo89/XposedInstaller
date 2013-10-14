package de.robv.android.xposed.installer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class LogsFragment extends Fragment {
	private File mFileDebugLog = new File(XposedApp.BASE_DIR + "log/debug.log");
	private TextView mTxtLog;

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();
		if (activity instanceof XposedDropdownNavActivity)
			((XposedDropdownNavActivity) activity).setNavItem(XposedDropdownNavActivity.TAB_LOGS);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.tab_logs, container, false);
		mTxtLog = (TextView) v.findViewById(R.id.txtLog);
		reloadDebugLog();
		return v;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_logs, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_refresh:
			reloadDebugLog();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void reloadDebugLog() {
		StringBuilder logContent = new StringBuilder(15 * 1024);
		try {
			FileInputStream fis = new FileInputStream(mFileDebugLog);
			Reader reader = new InputStreamReader(fis);
			char[] temp = new char[1024];
			int read;
			while ((read = reader.read(temp)) > 0) {
				logContent.append(temp, 0, read);
			}
			reader.close();
		} catch (IOException e) {
			logContent.append(getResources().getString(R.string.logs_load_failed));
			logContent.append('\n');
			logContent.append(e.getMessage());
		}
		mTxtLog.setText(logContent.toString());
	}
}
