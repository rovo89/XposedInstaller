package de.robv.android.xposed.installer;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.ModuleVersion;
import de.robv.android.xposed.installer.repo.ReleaseType;
import de.robv.android.xposed.installer.repo.RepoParser;
import de.robv.android.xposed.installer.util.DownloadsUtil;
import de.robv.android.xposed.installer.util.HashUtil;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.ThemeUtil;
import de.robv.android.xposed.installer.widget.DownloadView;

public class DownloadDetailsVersionsFragment extends ListFragment {
	private DownloadDetailsActivity mActivity;
	private static VersionsAdapter sAdapter;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mActivity = (DownloadDetailsActivity) activity;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		final Module module = mActivity.getModule();
		if (module == null)
			return;

		if (module.versions.isEmpty()) {
			setEmptyText(getString(R.string.download_no_versions));
			setListShown(true);
		} else {
			RepoLoader repoLoader = RepoLoader.getInstance();
			if (!repoLoader.isVersionShown(module.versions.get(0))) {
				TextView txtHeader = new TextView(getActivity());
				txtHeader.setText(R.string.download_test_version_not_shown);
				txtHeader.setTextColor(getResources().getColor(R.color.warning));
				txtHeader.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						mActivity.gotoPage(DownloadDetailsActivity.DOWNLOAD_SETTINGS);
					}
				});
				getListView().addHeaderView(txtHeader);
			}

			sAdapter = new VersionsAdapter(getActivity());
			
			if (module.versions.size() > 1) {
				sAdapter.add(module.versions.get(0));
				sAdapter.add(module.versions.get(1));
			} else {
				sAdapter.add(module.versions.get(0));
			}
			
			setListAdapter(sAdapter);		
		}

		DisplayMetrics metrics = getResources().getDisplayMetrics();
		int sixDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, metrics);
		int eightDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, metrics);
		getListView().setDivider(null);
		getListView().setDividerHeight(sixDp);
		getListView().setPadding(eightDp, eightDp, eightDp, eightDp);
		getListView().setClipToPadding(false);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		setListAdapter(null);
	}

	static class ViewHolder {
		TextView txtVersion;
		TextView txtRelType;
		TextView txtUploadDate;
		DownloadView downloadView;
		TextView txtChangesTitle;
		TextView txtChanges;
	}

	private class VersionsAdapter extends ArrayAdapter<ModuleVersion> {
		private final DateFormat mDateFormatter = DateFormat.getDateInstance(DateFormat.SHORT);
		private final int mColorRelTypeStable;
		private final int mColorRelTypeOthers;
		private boolean moreshown;

		public VersionsAdapter(Context context) {
			super(context, R.layout.list_item_version);
			mColorRelTypeStable = ThemeUtil.getThemeColor(getContext(), android.R.attr.textColorTertiary);
			mColorRelTypeOthers = getResources().getColor(R.color.warning);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			if (view == null || view.getTag() == null) {
				LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				
				if (position == 1 && !moreshown) {
					view = inflater.inflate(R.layout.list_item_version_more, null, true);
					view.setOnClickListener(new OnClickListener() {
						
						@Override
						public void onClick(View v) {
							sAdapter.remove(sAdapter.getItem(1));
							sAdapter.moreshown = true;						
													
							List<ModuleVersion> versions = mActivity.getModule().versions;
							for (int i = 1; i < versions.size(); i++) {
								if (RepoLoader.getInstance().isVersionShown(versions.get(i)))
									sAdapter.add(versions.get(i));
							}
						}
					});
					return view;
				} else {
					view = inflater.inflate(R.layout.list_item_version, null, true);					
				}
				
				ViewHolder viewHolder = new ViewHolder();
				viewHolder.txtVersion = (TextView) view.findViewById(R.id.txtVersion);
				viewHolder.txtRelType = (TextView) view.findViewById(R.id.txtRelType);
				viewHolder.txtUploadDate = (TextView) view.findViewById(R.id.txtUploadDate);
				viewHolder.downloadView = (DownloadView) view.findViewById(R.id.downloadView);
				viewHolder.txtChangesTitle = (TextView) view.findViewById(R.id.txtChangesTitle);
				viewHolder.txtChanges = (TextView) view.findViewById(R.id.txtChanges);
				view.setTag(viewHolder);
			}

			ViewHolder holder = (ViewHolder) view.getTag();
			ModuleVersion item = (ModuleVersion) getItem(position);
			
			holder.txtVersion.setText(item.name);
			holder.txtRelType.setText(item.relType.getTitleId());
			holder.txtRelType.setTextColor(item.relType == ReleaseType.STABLE ? mColorRelTypeStable : mColorRelTypeOthers);

			if (item.uploaded > 0) {
				holder.txtUploadDate.setText(mDateFormatter.format(new Date(item.uploaded)));
				holder.txtUploadDate.setVisibility(View.VISIBLE);
			} else {
				holder.txtUploadDate.setVisibility(View.GONE);
			}

			holder.downloadView.setUrl(item.downloadLink);
			holder.downloadView.setTitle(mActivity.getModule().name);
			holder.downloadView.setDownloadFinishedCallback(new DownloadModuleCallback(item));

			if (item.changelog != null && !item.changelog.isEmpty()) {
				holder.txtChangesTitle.setVisibility(View.VISIBLE);
				holder.txtChanges.setVisibility(View.VISIBLE);

				if (item.changelogIsHtml) {
					holder.txtChanges.setText(RepoParser.parseSimpleHtml(item.changelog));
					holder.txtChanges.setMovementMethod(LinkMovementMethod.getInstance());
				} else {
					holder.txtChanges.setText(item.changelog);
					holder.txtChanges.setMovementMethod(null);
				}

			} else {
				holder.txtChangesTitle.setVisibility(View.GONE);
				holder.txtChanges.setVisibility(View.GONE);
			}

			return view;
		}
	}

	private static class DownloadModuleCallback implements DownloadsUtil.DownloadFinishedCallback {
		private final ModuleVersion moduleVersion;

		public DownloadModuleCallback(ModuleVersion moduleVersion) {
			this.moduleVersion = moduleVersion;
		}

		@Override
		public void onDownloadFinished(Context context, DownloadsUtil.DownloadInfo info) {
			File localFile = new File(info.localFilename);
			if (!localFile.isFile())
				return;

			if (moduleVersion.md5sum != null && !moduleVersion.md5sum.isEmpty()) {
				try {
					String actualMd5Sum = HashUtil.md5(localFile);
					if (!moduleVersion.md5sum.equals(actualMd5Sum)) {
						Toast.makeText(context, context.getString(R.string.download_md5sum_incorrect,
								actualMd5Sum, moduleVersion.md5sum), Toast.LENGTH_LONG).show();

						return;
					}
				} catch (Exception e) {
					Toast.makeText(context, context.getString(R.string.download_could_not_read_file,
							e.getMessage()), Toast.LENGTH_LONG).show();
					return;
				}
			}

			PackageManager pm = context.getPackageManager();
			PackageInfo packageInfo = pm.getPackageArchiveInfo(info.localFilename, 0);

			if (packageInfo == null) {
				Toast.makeText(context, R.string.download_no_valid_apk, Toast.LENGTH_LONG).show();
				return;
			}

			if (!packageInfo.packageName.equals(moduleVersion.module.packageName)) {
				Toast.makeText(context,
						context.getString(R.string.download_incorrect_package_name,
								packageInfo.packageName, moduleVersion.module.packageName),
						Toast.LENGTH_LONG).show();

				return;
			}

			Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
			installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			installIntent.setDataAndType(Uri.fromFile(localFile), DownloadsUtil.MIME_TYPE_APK);
			//installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
			//installIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
			installIntent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.getApplicationInfo().packageName);
			context.startActivity(installIntent);
		}
	}
}
