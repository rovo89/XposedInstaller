package de.robv.android.xposed.installer.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class DownloadsUtil {
	public static final String MIME_TYPE_APK = "application/vnd.android.package-archive";
	private static final Map<String, DownloadFinishedCallback> mCallbacks = new HashMap<String, DownloadFinishedCallback>();

	public static DownloadInfo add(Context context, String title, String url, DownloadFinishedCallback callback) {
		removeAllForUrl(context, url);

		synchronized (mCallbacks) {
			mCallbacks.put(url, callback);
		}

		DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
		Request request = new Request(Uri.parse(url));
		request.setTitle(title);
		request.setMimeType(MIME_TYPE_APK);
		request.setNotificationVisibility(Request.VISIBILITY_VISIBLE);
		long id = dm.enqueue(request);

		return getById(context, id);
	}

	public static DownloadInfo getById(Context context, long id) {
		DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
		Cursor c = dm.query(new Query().setFilterById(id));
		if (!c.moveToFirst())
			return null;

		int columnId = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID);
		int columnUri = c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI);
		int columnTitle = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE);
		int columnLastMod = c.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP);
		int columnFilename = c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_FILENAME);
		int columnStatus = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS);
		int columnTotalSize = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
		int columnBytesDownloaded = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
		int columnReason = c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON);

		String localFilename = c.getString(columnFilename);
		if (localFilename != null && !localFilename.isEmpty() && !new File(localFilename).isFile()) {
			dm.remove(c.getLong(columnId));
			return null;
		}

		return new DownloadInfo(
				c.getLong(columnId),
				c.getString(columnUri),
				c.getString(columnTitle),
				c.getLong(columnLastMod),
				localFilename,
				c.getInt(columnStatus),
				c.getInt(columnTotalSize),
				c.getInt(columnBytesDownloaded),
				c.getInt(columnReason)
				);
	}

	public static DownloadInfo getLatestForUrl(Context context, String url) {
		List<DownloadInfo> all = getAllForUrl(context, url);
		return all.isEmpty() ? null : all.get(0);
	}

	public static List<DownloadInfo> getAllForUrl(Context context, String url) {
		DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
		Cursor c = dm.query(new Query());
		int columnId = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID);
		int columnUri = c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI);
		int columnTitle = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE);
		int columnLastMod = c.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP);
		int columnFilename = c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_FILENAME);
		int columnStatus = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS);
		int columnTotalSize = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
		int columnBytesDownloaded = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
		int columnReason = c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON);

		List<DownloadInfo> downloads = new ArrayList<DownloadInfo>();
		while (c.moveToNext()) {
			if (!url.equals(c.getString(columnUri)))
				continue;

			String localFilename = c.getString(columnFilename);
			if (localFilename != null && !localFilename.isEmpty() && !new File(localFilename).isFile()) {
				dm.remove(c.getLong(columnId));
				continue;
			}

			downloads.add(new DownloadInfo(
					c.getLong(columnId),
					c.getString(columnUri),
					c.getString(columnTitle),
					c.getLong(columnLastMod),
					localFilename,
					c.getInt(columnStatus),
					c.getInt(columnTotalSize),
					c.getInt(columnBytesDownloaded),
					c.getInt(columnReason)
					));
		}

		Collections.sort(downloads);
		return downloads;
	}

	public static void removeById(Context context, long id) {
		DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
		dm.remove(id);
	}

	public static void removeAllForUrl(Context context, String url) {
		DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
		Cursor c = dm.query(new Query());
		int columnId = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID);
		int columnUri = c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI);

		List<Long> idsList = new ArrayList<Long>();
		while (c.moveToNext()) {
			if (url.equals(c.getString(columnUri)))
				idsList.add(c.getLong(columnId));
		}

		if (idsList.isEmpty())
			return;

		long ids[] = new long[idsList.size()];
		for (int i = 0; i < ids.length; i++)
			ids[i] = idsList.get(0);

		dm.remove(ids);
	}


	public static void removeOutdated(Context context, long cutoff) {
		DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
		Cursor c = dm.query(new Query());
		int columnId = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID);
		int columnLastMod = c.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP);

		List<Long> idsList = new ArrayList<Long>();
		while (c.moveToNext()) {
			if (c.getLong(columnLastMod) < cutoff)
				idsList.add(c.getLong(columnId));
		}

		if (idsList.isEmpty())
			return;

		long ids[] = new long[idsList.size()];
		for (int i = 0; i < ids.length; i++)
			ids[i] = idsList.get(0);

		dm.remove(ids);
	}


	public static void triggerDownloadFinishedCallback(Context context, long id) {
		DownloadInfo info = getById(context, id);
		if (info == null || info.status != DownloadManager.STATUS_SUCCESSFUL)
			return;

		DownloadFinishedCallback callback = null;
		synchronized (mCallbacks) {
			callback = mCallbacks.get(info.url);
		}

		if (callback == null)
			return;

		callback.onDownloadFinished(context, info);
	}

	public static class DownloadInfo implements Comparable<DownloadInfo> {
		public final long id;
		public final String url;
		public final String title;
		public final long lastModification;
		public final String localFilename;
		public final int status;
		public final int totalSize;
		public final int bytesDownloaded;
		public final int reason;

		private DownloadInfo(long id, String url, String title, long lastModification, String localFilename,
				int status, int totalSize, int bytesDownloaded, int reason) {
			this.id = id;
			this.url = url;
			this.title = title;
			this.lastModification = lastModification;
			this.localFilename = localFilename;
			this.status = status;
			this.totalSize = totalSize;
			this.bytesDownloaded = bytesDownloaded;
			this.reason = reason;
		}

		@Override
		public int compareTo(DownloadInfo another) {
			int compare = (int)(another.lastModification - this.lastModification);
			if (compare != 0)
				return compare;
			return this.url.compareTo(another.url);
		}
	}

	public static interface DownloadFinishedCallback {
		public void onDownloadFinished(Context context, DownloadInfo info);
	}
}

