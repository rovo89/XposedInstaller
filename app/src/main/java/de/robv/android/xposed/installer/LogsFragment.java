package de.robv.android.xposed.installer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Calendar;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class LogsFragment extends Fragment {
	private File mFileErrorLog = new File(XposedApp.BASE_DIR + "log/error.log");
	private File mFileErrorLogOld = new File(XposedApp.BASE_DIR + "log/error.log.old");
	private static final int MAX_LOG_SIZE = 2*1024*1024; // 2 MB
	private TextView mTxtLog;
	private ScrollView mSVLog;
	private HorizontalScrollView mHSVLog;

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
		mSVLog = (ScrollView) v.findViewById(R.id.svLog);
		mHSVLog = (HorizontalScrollView) v.findViewById(R.id.hsvLog);
		reloadErrorLog();
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
			reloadErrorLog();
			return true;
		case R.id.menu_send:
			send();
			return true;
		case R.id.menu_save:
			save();
			return true;
		case R.id.menu_clear:
			clear();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void reloadErrorLog() {
		StringBuilder logContent = new StringBuilder(15 * 1024);
		try {
			FileInputStream fis = new FileInputStream(mFileErrorLog);
			long skipped = skipLargeFile(fis, mFileErrorLog.length());
			if (skipped > 0) {
				logContent.append("-----------------\n");
				logContent.append(getResources().getString(R.string.log_too_large, MAX_LOG_SIZE / 1024, skipped / 1024));
				logContent.append("\n-----------------\n\n");
			}
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

		if (logContent.length() > 0)
			mTxtLog.setText(logContent.toString());
		else
			mTxtLog.setText(R.string.log_is_empty);

		mSVLog.post(new Runnable() {
			@Override
			public void run() {
				mSVLog.scrollTo(0, mTxtLog.getHeight());
			}
		});
		mHSVLog.post(new Runnable() {
			@Override
			public void run() {
				mHSVLog.scrollTo(0, 0);
			}
		});
	}

	private void clear() {
		try {
			new FileOutputStream(mFileErrorLog).close();;
			mFileErrorLogOld.delete();
			Toast.makeText(getActivity(), R.string.logs_cleared, Toast.LENGTH_SHORT).show();
			reloadErrorLog();
		} catch (IOException e) {
			Toast.makeText(getActivity(),
					getResources().getString(R.string.logs_clear_failed) + "\n" + e.getMessage(),
					Toast.LENGTH_LONG).show();
			return;
		}
	}

	private void send() {
		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_SEND);
		sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(mFileErrorLog));
		sendIntent.setType("application/text"); // text/plain is handled wrongly by too many apps
		startActivity(Intent.createChooser(sendIntent, getResources().getString(R.string.menuSend)));
	}

	@SuppressLint("DefaultLocale")
	private void save() {
		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			Toast.makeText(getActivity(), R.string.sdcard_not_writable, Toast.LENGTH_LONG).show();
			return;
		}

		Calendar now = Calendar.getInstance();
		String filename = String.format("xposed_%s_%04d%02d%02d_%02d%02d%02d.log", "error",
				now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH),
				now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND));
		File targetFile = new File(getActivity().getExternalFilesDir(null), filename);

		try {
			FileInputStream in = new FileInputStream(mFileErrorLog);
			FileOutputStream out = new FileOutputStream(targetFile);

			long skipped = skipLargeFile(in, mFileErrorLog.length());
			if (skipped > 0) {
				StringBuilder logContent = new StringBuilder(512);
				logContent.append("-----------------\n");
				logContent.append(getResources().getString(R.string.log_too_large, MAX_LOG_SIZE / 1024, skipped / 1024));
				logContent.append("\n-----------------\n\n");
				out.write(logContent.toString().getBytes());
			}

			byte[] buffer = new byte[1024];
			int len;
			while ((len = in.read(buffer)) > 0){
				out.write(buffer, 0, len);
			}
			in.close();
			out.close();
		} catch (IOException e) {
			Toast.makeText(getActivity(),
					getResources().getString(R.string.logs_save_failed) + "\n" + e.getMessage(),
					Toast.LENGTH_LONG).show();
			return;
		}

		Toast.makeText(getActivity(), targetFile.toString(), Toast.LENGTH_LONG).show();
	}

	private long skipLargeFile(InputStream is, long length) throws IOException {
		if (length < MAX_LOG_SIZE)
			return 0;

		long skipped = length - MAX_LOG_SIZE;
		long yetToSkip = skipped;
		do {
			yetToSkip -= is.skip(yetToSkip);
		} while (yetToSkip > 0);

		int c;
		do {
			c = is.read();
			if (c == -1)
				break;
			skipped++;
		} while (c != '\n');

		return skipped;
	}
}
