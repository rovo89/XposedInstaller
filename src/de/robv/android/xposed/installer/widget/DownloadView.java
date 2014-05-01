package de.robv.android.xposed.installer.widget;

import android.app.DownloadManager;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.util.DownloadsUtil;
import de.robv.android.xposed.installer.util.DownloadsUtil.DownloadFinishedCallback;
import de.robv.android.xposed.installer.util.DownloadsUtil.DownloadInfo;

public class DownloadView extends LinearLayout {
	private DownloadInfo mInfo = null;
	private String mUrl = null;
	private String mTitle = null;
	private DownloadFinishedCallback mCallback = null;

	private final Button btnDownload;
	private final Button btnDownloadCancel;
	private final Button btnInstall;
	private final ProgressBar progressBar;
	private final TextView txtInfo;

	public DownloadView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setFocusable(false);
		setOrientation(LinearLayout.VERTICAL);

		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.download_view, this, true);

		btnDownload = (Button) findViewById(R.id.btnDownload);
		btnDownloadCancel = (Button) findViewById(R.id.btnDownloadCancel);
		btnInstall = (Button) findViewById(R.id.btnInstall);

		btnDownload.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mInfo = DownloadsUtil.add(getContext(), mTitle, mUrl, mCallback);
				refreshViewFromUiThread();

				if (mInfo != null)
					new DownloadMonitor().start();
			}
		});

		btnDownloadCancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mInfo == null)
					return;

				DownloadsUtil.removeById(getContext(), mInfo.id);
				// UI update will happen automatically by the DownloadMonitor
			}
		});

		btnInstall.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mCallback == null)
					return;

				mCallback.onDownloadFinished(getContext(), mInfo);
			}
		});

		progressBar = (ProgressBar) findViewById(R.id.progress);
		txtInfo = (TextView) findViewById(R.id.txtInfo);

		refreshViewFromUiThread();
	}

	private void refreshViewFromUiThread() {
		refreshViewRunnable.run();
	}

	private void refreshView() {
		post(refreshViewRunnable);
	}

	private final Runnable refreshViewRunnable = new Runnable() {
		@Override
		public void run() {
			if (mUrl == null) {
				btnDownload.setVisibility(View.GONE);
				btnDownloadCancel.setVisibility(View.GONE);
				btnInstall.setVisibility(View.GONE);
				progressBar.setVisibility(View.GONE);
				txtInfo.setVisibility(View.VISIBLE);
				txtInfo.setText(R.string.download_view_no_url);
				return;
			} else if (mInfo == null) {
				btnDownload.setVisibility(View.VISIBLE);
				btnDownloadCancel.setVisibility(View.GONE);
				btnInstall.setVisibility(View.GONE);
				progressBar.setVisibility(View.GONE);
				txtInfo.setVisibility(View.GONE);
			} else {
				switch (mInfo.status) {
					case DownloadManager.STATUS_PENDING:
					case DownloadManager.STATUS_PAUSED:
					case DownloadManager.STATUS_RUNNING:
						btnDownload.setVisibility(View.GONE);
						btnDownloadCancel.setVisibility(View.VISIBLE);
						btnInstall.setVisibility(View.GONE);
						progressBar.setVisibility(View.VISIBLE);
						txtInfo.setVisibility(View.VISIBLE);
						if (mInfo.totalSize <= 0 || mInfo.status != DownloadManager.STATUS_RUNNING) {
							progressBar.setIndeterminate(true);
							txtInfo.setText(R.string.download_view_waiting);
						} else {
							progressBar.setIndeterminate(false);
							progressBar.setMax(mInfo.totalSize);
							progressBar.setProgress(mInfo.bytesDownloaded);
							txtInfo.setText(getContext().getString(R.string.download_view_running,
									mInfo.bytesDownloaded / 1024, mInfo.totalSize / 1024));
						}
						break;

					case DownloadManager.STATUS_FAILED:
						btnDownload.setVisibility(View.VISIBLE);
						btnDownloadCancel.setVisibility(View.GONE);
						btnInstall.setVisibility(View.GONE);
						progressBar.setVisibility(View.GONE);
						txtInfo.setVisibility(View.VISIBLE);
						txtInfo.setText(getContext().getString(R.string.download_view_failed, mInfo.reason));
						break;

					case DownloadManager.STATUS_SUCCESSFUL:
						btnDownload.setVisibility(View.GONE);
						btnDownloadCancel.setVisibility(View.GONE);
						btnInstall.setVisibility(View.VISIBLE);
						progressBar.setVisibility(View.GONE);
						txtInfo.setVisibility(View.VISIBLE);
						txtInfo.setText(R.string.download_view_successful);
						break;
				}
			}
		}
	};

	public void setUrl(String url) {
		mUrl = url;

		if (mUrl != null)
			mInfo = DownloadsUtil.getLatestForUrl(getContext(), mUrl);
		else
			mInfo = null;

		refreshView();
	}

	public String getUrl() {
		return mUrl;
	}

	public void setTitle(String title) {
		this.mTitle = title;
	}

	public String getTitle() {
		return mTitle;
	}

	public void setDownloadFinishedCallback(DownloadFinishedCallback downloadFinishedCallback) {
		this.mCallback = downloadFinishedCallback;
	}

	public DownloadFinishedCallback getDownloadFinishedCallback() {
		return mCallback;
	}

	private class DownloadMonitor extends Thread {
		public DownloadMonitor() {
			super("DownloadMonitor");
		}

		@Override
		public void run() {
			while (true) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					return;
				}

				mInfo = DownloadsUtil.getById(getContext(), mInfo.id);
				refreshView();
				if (mInfo == null)
					return;

				if (mInfo.status != DownloadManager.STATUS_PENDING
				 && mInfo.status != DownloadManager.STATUS_PAUSED
				 && mInfo.status != DownloadManager.STATUS_RUNNING)
					return;
			}
		}
	}
}
