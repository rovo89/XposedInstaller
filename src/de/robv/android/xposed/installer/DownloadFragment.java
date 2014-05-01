package de.robv.android.xposed.installer;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import com.emilsjolander.components.stickylistheaders.StickyListHeadersAdapter;

import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.ModuleGroup;
import de.robv.android.xposed.installer.repo.ModuleVersion;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.ModuleUtil.ModuleListener;
import de.robv.android.xposed.installer.util.NavUtil;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.RepoLoader.RepoListener;
import de.robv.android.xposed.installer.util.ThemeUtil;

public class DownloadFragment extends Fragment implements RepoListener, ModuleListener {
	private SharedPreferences mPref;
	private DownloadsAdapter mAdapter;
	private String mFilterText;
	private RepoLoader mRepoLoader;
	private ModuleUtil mModuleUtil;
	private int mSortingOrder;
	private SearchView mSearchView;

	private static final int SORT_STATUS = 0;
	private static final int SORT_UPDATED = 1;
	private static final int SORT_CREATED = 2;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mPref = XposedApp.getPreferences();
		mRepoLoader = RepoLoader.getInstance();
		mModuleUtil = ModuleUtil.getInstance();
		mAdapter = new DownloadsAdapter(getActivity());
		mSortingOrder = mPref.getInt("download_sorting_order", SORT_STATUS);
		setHasOptionsMenu(true);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();
		if (activity instanceof XposedDropdownNavActivity)
			((XposedDropdownNavActivity) activity).setNavItem(XposedDropdownNavActivity.TAB_DOWNLOAD);

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.tab_downloader, container, false);
		ListView lv = (ListView) v.findViewById(R.id.listModules);

		mRepoLoader.addListener(this, true);
		mModuleUtil.addListener(this);
		lv.setAdapter(mAdapter);

		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				DownloadItem item = mAdapter.getItem(position);

				Intent detailsIntent = new Intent(getActivity(), DownloadDetailsActivity.class);
				detailsIntent.setData(Uri.fromParts("package", item.packageName, null));
				detailsIntent.putExtra(NavUtil.FINISH_ON_UP_NAVIGATION, true);
				startActivity(detailsIntent);
				NavUtil.setTransitionSlideEnter(getActivity());
			}
		});
		lv.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				// Expand the search view when the SEARCH key is trigered
				if (keyCode == KeyEvent.KEYCODE_SEARCH && event.getAction() == KeyEvent.ACTION_UP
						&& (event.getFlags() & KeyEvent.FLAG_CANCELED) == 0) {
					if (mSearchView != null)
						mSearchView.setIconified(false);
					return true;
				}
				return false;
			}
		});
		return v;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		mRepoLoader.removeListener(this);
		mModuleUtil.removeListener(this);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_download, menu);

		// Setup search button
		final MenuItem searchItem = menu.findItem(R.id.menu_search);
		mSearchView = (SearchView) searchItem.getActionView();
		mSearchView.setIconifiedByDefault(true);
		mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				setFilter(query);
				mSearchView.clearFocus();
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				setFilter(newText);
				return true;
			}
		});
		searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
			@Override
			public boolean onMenuItemActionExpand(MenuItem item) {
				return true;
			}

			@Override
			public boolean onMenuItemActionCollapse(MenuItem item) {
				setFilter(null);
				return true;
			}
		});
	}

	private void setFilter(String filterText) {
		mFilterText = filterText;
		if (mAdapter != null)
			mAdapter.getFilter().filter(mFilterText);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_refresh:
				mRepoLoader.triggerReload(true);
				return true;
			case R.id.menu_sort:
				AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
				builder.setTitle(R.string.download_sorting_title);
				builder.setSingleChoiceItems(R.array.download_sort_order, mSortingOrder, new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						mSortingOrder = which;
						mPref.edit().putInt("download_sorting_order", mSortingOrder).commit();
						mAdapter.notifyDataSetChanged();
						dialog.dismiss();
					}
				});
				builder.show();
				return true;
		}
		return super.onOptionsItemSelected(item);
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
					mAdapter.setNotifyOnChange(false);
					mAdapter.setItems(items);
					mAdapter.notifyDataSetChanged();
				}
			}
		});
	}

	private void notifyDataSetChanged() {
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				synchronized (mAdapter) {
					mAdapter.notifyDataSetChanged();
				}
			}
		});
	}

	@Override
	public void onSingleInstalledModuleReloaded(ModuleUtil moduleUtil, String packageName, InstalledModule module) {
		notifyDataSetChanged();
	}

	@Override
	public void onInstalledModulesReloaded(ModuleUtil moduleUtil) {
		notifyDataSetChanged();
	}



	private class DownloadsAdapter extends ArrayAdapter<DownloadItem> implements StickyListHeadersAdapter, Filterable {
		private final DateFormat mDateFormatter = DateFormat.getDateInstance(DateFormat.SHORT);
		private final LayoutInflater mInflater;
		private String[] sectionHeadersStatus;
		private String[] sectionHeadersDate;
		private ArrayList<DownloadItem> mOriginalValues;
		private Filter mFilter;

		public DownloadsAdapter(Context context) {
			super(context, R.layout.list_item_download, android.R.id.text1);
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

			Resources res = context.getResources();
			sectionHeadersStatus = new String[] {
				res.getString(R.string.download_section_framework),
				res.getString(R.string.download_section_update_available),
				res.getString(R.string.download_section_installed),
				res.getString(R.string.download_section_not_installed),
			};
			sectionHeadersDate = new String[] {
				res.getString(R.string.download_section_24h),
				res.getString(R.string.download_section_7d),
				res.getString(R.string.download_section_30d),
				res.getString(R.string.download_section_older)
			};
		}

		public void setItems(List<DownloadItem> items) {
			mOriginalValues = new ArrayList<DownloadItem>(items);
			getFilter().filter(mFilterText);
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
				txtStatus.setText(getContext().getString(R.string.download_status_update_available, installed.versionName, latest.name));
				txtStatus.setTextColor(getResources().getColor(R.color.download_status_update_available));
				txtStatus.setVisibility(View.VISIBLE);
			} else if (installStatus == DownloadItem.INSTALL_STATUS_INSTALLED) {
				txtStatus.setText(getContext().getString(R.string.download_status_installed, installed.versionName));
				txtStatus.setTextColor(ThemeUtil.getThemeColor(getContext(), R.attr.download_status_installed));
				txtStatus.setVisibility(View.VISIBLE);
			} else {
				txtStatus.setVisibility(View.GONE);
			}

			String creationDate = mDateFormatter.format(new Date(item.getCreationDate()));
			String updateDate = mDateFormatter.format(new Date(item.getLastUpdate()));
			((TextView) view.findViewById(R.id.timestamps)).setText(
				getString(R.string.download_timestamps, creationDate, updateDate));

			return view;
		}

		@Override
		public void notifyDataSetChanged() {
			setNotifyOnChange(false);
			mAdapter.sort(null);
			super.notifyDataSetChanged();
		}

		@Override
		public View getHeaderView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.list_sticky_header_download, parent, false);
			}

			long section = getHeaderId(position);

			TextView tv = (TextView) convertView.findViewById(android.R.id.title);
			tv.setText(mSortingOrder == SORT_STATUS ? sectionHeadersStatus[(int)section] : sectionHeadersDate[(int) section]);
			return convertView;
		}

		@Override
		public long getHeaderId(int position) {
			DownloadItem item = getItem(position);

			if (mSortingOrder != SORT_STATUS) {
				long timestamp = (mSortingOrder ==  SORT_UPDATED) ? item.getLastUpdate() : item.getCreationDate();
				long age = System.currentTimeMillis() - timestamp;
				final long mSecsPerDay = 24 * 60 * 60 * 1000L;
				if (age < mSecsPerDay)
					return 0;
				if (age < 7 * mSecsPerDay)
					return 1;
				if (age < 30 * mSecsPerDay)
					return 2;
				return 3;
			} else {
				if (item.isFramework)
					return 0;

				int installStatus = item.getInstallStatus();
				if (installStatus == DownloadItem.INSTALL_STATUS_HAS_UPDATE)
					return 1;
				else if (installStatus == DownloadItem.INSTALL_STATUS_INSTALLED)
					return 2;
				else
					return 3;
			}
		}

		@Override
		public Filter getFilter() {
			if (mFilter == null) {
				mFilter = new DownloadFilter();
			}
			return mFilter;
		}

		private class DownloadFilter extends Filter {
			@Override
			protected FilterResults performFiltering(CharSequence filter) {
				FilterResults results = new FilterResults();

				ArrayList<DownloadItem> list = new ArrayList<DownloadItem>(mOriginalValues);
				if (filter == null || filter.length() == 0) {
					results.values = list;
					results.count = list.size();
				} else {
					int count = list.size();
					ArrayList<DownloadItem> newValues = new ArrayList<DownloadItem>();
					String filterStr = filter.toString();

					for (int i = 0; i < count; i++) {
						DownloadItem value = list.get(i);
						if (value.containsText(filterStr))
							newValues.add(value);
					}

					results.values = newValues;
					results.count = newValues.size();
				}

				return results;
			}

			@SuppressWarnings("unchecked")
			@Override
			protected void publishResults(CharSequence constraint, FilterResults results) {
				clear();
				addAll((List<DownloadItem>) results.values);
				if (results.count > 0) {
					notifyDataSetChanged();
				} else {
					notifyDataSetInvalidated();
				}
			}
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
			return mRepoLoader.getLatestVersion(group.getModule());
		}

		public InstalledModule getInstalled() {
			return isFramework ? mModuleUtil.getFramework() : mModuleUtil.getModule(group.packageName);
		}

		public int getInstallStatus() {
			InstalledModule installed = getInstalled();
			if (installed == null)
				return INSTALL_STATUS_NOT_INSTALLED;

			return installed.isUpdate(getLatestVersion()) ? INSTALL_STATUS_HAS_UPDATE : INSTALL_STATUS_INSTALLED;
		}

		public long getCreationDate() {
			return group.getModule().created;
		}

		public long getLastUpdate() {
			return group.getModule().updated;
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

			if (mSortingOrder == SORT_UPDATED) {
				long updateDiff = other.getLastUpdate() - this.getLastUpdate();
				if (updateDiff != 0)
					return updateDiff > 0 ? 1 : -1;
			} else if (mSortingOrder == SORT_CREATED) {
				long updateDiff = other.getCreationDate() - this.getCreationDate();
				if (updateDiff != 0)
					return updateDiff > 0 ? 1 : -1;
			}

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

		@SuppressLint("DefaultLocale")
		public boolean containsText(String text) {
			text = text.toLowerCase();
			if (stringContainsText(getDisplayName(), text))
				return true;
			if (stringContainsText(getModule().summary, text))
				return true;
			if (stringContainsText(getModule().description, text))
				return true;
			if (stringContainsText(getModule().author, text))
				return true;
			return false;
		}

		@SuppressLint("DefaultLocale")
		private boolean stringContainsText(String value, String text) {
			return value != null && value.toLowerCase().contains(text);
		}
	}
}
