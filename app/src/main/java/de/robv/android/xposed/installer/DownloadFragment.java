package de.robv.android.xposed.installer;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.FilterQueryProvider;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import java.text.DateFormat;
import java.util.Date;

import de.robv.android.xposed.installer.repo.RepoDb;
import de.robv.android.xposed.installer.repo.RepoDbDefinitions.OverviewColumnsIndexes;
import de.robv.android.xposed.installer.util.Loader;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.ModuleUtil.ModuleListener;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.ThemeUtil;
import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

public class DownloadFragment extends Fragment implements Loader.Listener<RepoLoader>, ModuleListener {
    private SharedPreferences mPref;
    private DownloadsAdapter mAdapter;
    private String mFilterText;
    private RepoLoader mRepoLoader;
    private ModuleUtil mModuleUtil;
    private int mSortingOrder;
    private SearchView mSearchView;
    private StickyListHeadersListView mListView;
    private View mRefreshHint;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPref = XposedApp.getPreferences();
        mRepoLoader = RepoLoader.getInstance();
        mModuleUtil = ModuleUtil.getInstance();
        mAdapter = new DownloadsAdapter(getActivity());
        mAdapter.setFilterQueryProvider(new FilterQueryProvider() {
            @Override
            public Cursor runQuery(CharSequence constraint) {
                return RepoDb.queryModuleOverview(mSortingOrder, constraint);
            }
        });
        mSortingOrder = mPref.getInt("download_sorting_order",
                RepoDb.SORT_STATUS);

        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mAdapter != null && mListView != null) {
            mListView.setAdapter(mAdapter);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.tab_downloader, container, false);

        mRefreshHint = v.findViewById(R.id.refresh_hint);
        final SwipeRefreshLayout refreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swiperefreshlayout);
        refreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimary));
        mRepoLoader.addListener(this);
        mRepoLoader.setSwipeRefreshLayout(refreshLayout);
        mModuleUtil.addListener(this);

        mListView = (StickyListHeadersListView) v.findViewById(R.id.listModules);
        mListView.setAdapter(mAdapter);
        mListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (view.getChildAt(0) != null) {
                    refreshLayout.setEnabled(view.getFirstVisiblePosition() == 0 && view.getChildAt(0).getTop() == 0);
                }
            }
        });
        reloadItems();

        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor cursor = (Cursor) mAdapter.getItem(position);
                String packageName = cursor.getString(OverviewColumnsIndexes.PKGNAME);

                Intent detailsIntent = new Intent(getActivity(), DownloadDetailsActivity.class);
                detailsIntent.setData(Uri.fromParts("package", packageName, null));
                startActivity(detailsIntent);
            }
        });
        mListView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // Expand the search view when the SEARCH key is triggered
                if (keyCode == KeyEvent.KEYCODE_SEARCH && event.getAction() == KeyEvent.ACTION_UP && (event.getFlags() & KeyEvent.FLAG_CANCELED) == 0) {
                    if (mSearchView != null)
                        mSearchView.setIconified(false);
                    return true;
                }
                return false;
            }
        });

        setHasOptionsMenu(true);

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mRepoLoader.removeListener(this);
        mRepoLoader.setSwipeRefreshLayout(null);
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
        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                setFilter(null);
                return true; // Return true to collapse action view
            }

            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true; // Return true to expand action view
            }
        });
    }

    private void setFilter(String filterText) {
        mFilterText = filterText;
        reloadItems();
        mRefreshHint.setVisibility(TextUtils.isEmpty(filterText) ? View.VISIBLE : View.GONE);
    }

    private void reloadItems() {
        mAdapter.getFilter().filter(mFilterText);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_sort:
                new MaterialDialog.Builder(getActivity())
                        .title(R.string.download_sorting_title)
                        .items(R.array.download_sort_order)
                        .itemsCallbackSingleChoice(mSortingOrder,
                                new MaterialDialog.ListCallbackSingleChoice() {
                                    @Override
                                    public boolean onSelection(MaterialDialog materialDialog, View view, int i, CharSequence charSequence) {
                                        mSortingOrder = i;
                                        mPref.edit().putInt("download_sorting_order", mSortingOrder).apply();
                                        reloadItems();
                                        materialDialog.dismiss();
                                        return true;
                                    }
                                })
                        .show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onReloadDone(final RepoLoader loader) {
        reloadItems();
    }

    @Override
    public void onSingleInstalledModuleReloaded(ModuleUtil moduleUtil, String packageName, InstalledModule module) {
        reloadItems();
    }

    @Override
    public void onInstalledModulesReloaded(ModuleUtil moduleUtil) {
        reloadItems();
    }

    private class DownloadsAdapter extends CursorAdapter implements StickyListHeadersAdapter {
        private final Context mContext;
        private final DateFormat mDateFormatter = DateFormat.getDateInstance(DateFormat.SHORT);
        private final LayoutInflater mInflater;
        private String[] sectionHeadersStatus;
        private String[] sectionHeadersDate;

        public DownloadsAdapter(Context context) {
            super(context, null, 0);
            mContext = context;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            Resources res = context.getResources();
            sectionHeadersStatus = new String[]{
                    res.getString(R.string.download_section_framework),
                    res.getString(R.string.download_section_update_available),
                    res.getString(R.string.download_section_installed),
                    res.getString(R.string.download_section_not_installed),};
            sectionHeadersDate = new String[]{
                    res.getString(R.string.download_section_24h),
                    res.getString(R.string.download_section_7d),
                    res.getString(R.string.download_section_30d),
                    res.getString(R.string.download_section_older)};
        }

        @Override
        public View getHeaderView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.list_sticky_header_download, parent, false);
            }

            long section = getHeaderId(position);

            TextView tv = (TextView) convertView.findViewById(android.R.id.title);
            tv.setText(mSortingOrder == RepoDb.SORT_STATUS
                    ? sectionHeadersStatus[(int) section]
                    : sectionHeadersDate[(int) section]);
            return convertView;
        }

        @Override
        public long getHeaderId(int position) {
            Cursor cursor = (Cursor) getItem(position);
            long created = cursor.getLong(OverviewColumnsIndexes.CREATED);
            long updated = cursor.getLong(OverviewColumnsIndexes.UPDATED);
            boolean isFramework = cursor.getInt(OverviewColumnsIndexes.IS_FRAMEWORK) > 0;
            boolean isInstalled = cursor.getInt(OverviewColumnsIndexes.IS_INSTALLED) > 0;
            boolean hasUpdate = cursor.getInt(OverviewColumnsIndexes.HAS_UPDATE) > 0;

            if (mSortingOrder != RepoDb.SORT_STATUS) {
                long timestamp = (mSortingOrder == RepoDb.SORT_UPDATED) ? updated : created;
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
                if (isFramework)
                    return 0;

                if (hasUpdate)
                    return 1;
                else if (isInstalled)
                    return 2;
                else
                    return 3;
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return mInflater.inflate(R.layout.list_item_download, parent, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            String title = cursor.getString(OverviewColumnsIndexes.TITLE);
            String summary = cursor.getString(OverviewColumnsIndexes.SUMMARY);
            String installedVersion = cursor.getString(OverviewColumnsIndexes.INSTALLED_VERSION);
            String latestVersion = cursor.getString(OverviewColumnsIndexes.LATEST_VERSION);
            long created = cursor.getLong(OverviewColumnsIndexes.CREATED);
            long updated = cursor.getLong(OverviewColumnsIndexes.UPDATED);
            boolean isInstalled = cursor.getInt(OverviewColumnsIndexes.IS_INSTALLED) > 0;
            boolean hasUpdate = cursor.getInt(OverviewColumnsIndexes.HAS_UPDATE) > 0;

            TextView txtTitle = (TextView) view.findViewById(android.R.id.text1);
            txtTitle.setText(title);

            TextView txtSummary = (TextView) view.findViewById(android.R.id.text2);
            txtSummary.setText(summary);

            TextView txtStatus = (TextView) view.findViewById(R.id.downloadStatus);
            if (hasUpdate) {
                txtStatus.setText(mContext.getString(
                        R.string.download_status_update_available,
                        installedVersion, latestVersion));
                txtStatus.setTextColor(getResources().getColor(R.color.download_status_update_available));
                txtStatus.setVisibility(View.VISIBLE);
            } else if (isInstalled) {
                txtStatus.setText(mContext.getString(
                        R.string.download_status_installed, installedVersion));
                txtStatus.setTextColor(ThemeUtil.getThemeColor(mContext, R.attr.download_status_installed));
                txtStatus.setVisibility(View.VISIBLE);
            } else {
                txtStatus.setVisibility(View.GONE);
            }

            String creationDate = mDateFormatter.format(new Date(created));
            String updateDate = mDateFormatter.format(new Date(updated));
            ((TextView) view.findViewById(R.id.timestamps)).setText(getString(R.string.download_timestamps, creationDate, updateDate));
        }
    }
}
