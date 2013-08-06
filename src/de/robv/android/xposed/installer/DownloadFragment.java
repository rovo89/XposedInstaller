package de.robv.android.xposed.installer;

import java.util.ArrayList;
import java.util.List;

import android.animation.Animator;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.ModuleGroup;
import de.robv.android.xposed.installer.repo.ModuleVersion;
import de.robv.android.xposed.installer.util.AnimatorUtil;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.RepoLoader.RepoListener;

public class DownloadFragment extends Fragment implements RepoListener {
	private DownloadsAdapter mAdapter;
	private ModuleUtil mModuleUtil;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mModuleUtil = ModuleUtil.getInstance();
		setHasOptionsMenu(true);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();
		if (activity instanceof XposedInstallerActivity)
			((XposedInstallerActivity) activity).setNavItem(XposedInstallerActivity.TAB_DOWNLOAD, null);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.tab_downloader, container, false);
		ListView lv = (ListView) v.findViewById(R.id.listModules);
		
		mAdapter = new DownloadsAdapter(getActivity());
		mAdapter.setNotifyOnChange(false);
		RepoLoader.getInstance().addListener(this, true);
		lv.setAdapter(mAdapter);
		
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				DownloadItem item = mAdapter.getItem(position);
				DownloadDetailsFragment fragment = DownloadDetailsFragment.newInstance(item.packageName);

				FragmentTransaction tx = getFragmentManager().beginTransaction();
				// requires onCreateAnimator() to be overridden!
				tx.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
						R.anim.slide_in_left, R.anim.slide_out_right);
				tx.replace(android.R.id.content, fragment);
				tx.addToBackStack("downloads_overview");
				tx.commit();
			}
		});
		return v;
	}
	
	@Override
	public void onDestroyView() {
	    super.onDestroyView();
	    mAdapter = null;
	    RepoLoader.getInstance().removeListener(this);
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_download, menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_refresh:
				RepoLoader.getInstance().triggerReload();
				break;
		}
	    return super.onOptionsItemSelected(item);
	}

	@Override
	public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
		return AnimatorUtil.createSlideAnimation(this, nextAnim);
	}
	
	@Override
	public void onRepoReloaded(final RepoLoader loader) {
		if (mAdapter == null)
			return;

		final List<DownloadItem> items = new ArrayList<DownloadItem>();
		for (ModuleGroup group : loader.getModules().values()) {
			items.add(new DownloadItem(group));
		}

		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				synchronized (mAdapter) {
					mAdapter.clear();
					mAdapter.addAll(items);
					mAdapter.notifyDataSetChanged();
				}
			}
		});
	}



	private class DownloadsAdapter extends ArrayAdapter<DownloadItem> {
		public DownloadsAdapter(Context context) {
			super(context, R.layout.list_item_download, android.R.id.text1);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = super.getView(position, convertView, parent);

			DownloadItem item = getItem(position);
			Module module = item.getModule();
			ModuleVersion latest = item.getLatestVersion();
			InstalledModule installed = item.getInstalled();
			int installStatus = item.getInstallStatus();

			TextView txtSummary = (TextView) view.findViewById(android.R.id.text2);
			txtSummary.setText(module.summary);

			TextView txtStatus = (TextView) view.findViewById(R.id.downloadStatus);
			if (installStatus == DownloadItem.INSTALL_STATUS_HAS_UPDATE) {
				txtStatus.setText(String.format("Update available (version %s \u2192 %s)", installed.versionName, latest.name));
				txtStatus.setTextColor(getResources().getColor(R.color.download_status_update_available));
				txtStatus.setVisibility(View.VISIBLE);
			} else if (installStatus == DownloadItem.INSTALL_STATUS_INSTALLED) {
				txtStatus.setText(String.format("Installed (version %s)", installed.versionName));
				txtStatus.setTextColor(getResources().getColor(R.color.download_status_installed));
				txtStatus.setVisibility(View.VISIBLE);
			} else {
				txtStatus.setVisibility(View.GONE);
			}

			return view;
		}

		@Override
		public void notifyDataSetChanged() {
			mAdapter.sort(null);
		    super.notifyDataSetChanged();
		}
	}



	private class DownloadItem implements Comparable<DownloadItem> {
		public final ModuleGroup group;
		public final String packageName;
		public final boolean isFramework;

		public final static int INSTALL_STATUS_NOT_INSTALLED = 0;
		public final static int INSTALL_STATUS_INSTALLED = 1;
		public final static int INSTALL_STATUS_HAS_UPDATE = 2;

		public DownloadItem(ModuleGroup group) {
			this.group = group;
			this.packageName = group.packageName;
			this.isFramework = mModuleUtil.isFramework(group.packageName);
		}

		public Module getModule() {
			return group.getModule();
		}

		public ModuleVersion getLatestVersion() {
			return mModuleUtil.getLatestVersion(group.getModule());
		}

		public InstalledModule getInstalled() {
			return mModuleUtil.getModule(group.packageName);
		}

		public int getInstallStatus() {
			InstalledModule installed = getInstalled();
			if (installed == null)
				return INSTALL_STATUS_NOT_INSTALLED;

			return installed.isUpdate(getLatestVersion()) ? INSTALL_STATUS_HAS_UPDATE : INSTALL_STATUS_INSTALLED; 
		}

		public String getDisplayName() {
			return group.getModule().name;
		}

		@Override
		public String toString() {
			return getDisplayName();
		}

		@Override
		public int compareTo(DownloadItem other) {
			if (other == null)
				return 1;

			if (this.isFramework != other.isFramework)
				return this.isFramework ? -1 : 1;

			int order = other.getInstallStatus() - this.getInstallStatus();
			if (order != 0)
				return order;

			order = getDisplayName().compareToIgnoreCase(other.getDisplayName());
			if (order != 0)
				return order;

			order = this.packageName.compareTo(other.packageName);
			return order;
		}
	}
}
