package de.robv.android.xposed.installer;

import static de.robv.android.xposed.installer.XposedApp.darkenColor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.ThemeUtil;
import de.robv.android.xposed.installer.util.UIUtil;

public class ModulesBookmark extends XposedBaseActivity {

	private static Toolbar mToolbar;
	private static RepoLoader mRepoLoader;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ThemeUtil.setTheme(this);
		setContentView(R.layout.activity_container);

		mRepoLoader = RepoLoader.getInstance();

		mToolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(mToolbar);

		mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				finish();
			}
		});

		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setTitle(R.string.bookmarks);
			ab.setDisplayHomeAsUpEnabled(true);
		}

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new ModulesBookmarkFragment())
					.commit();
		}
	}

	public static class ModulesBookmarkFragment extends ListFragment
			implements AdapterView.OnItemClickListener,
			SharedPreferences.OnSharedPreferenceChangeListener {

		private List<Module> mBookmarkedModules = new ArrayList<>();
		private BookmarkModuleAdapter mAdapter;
		private SharedPreferences mBookmarksPref;
		private boolean changed;

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			mBookmarksPref = getContext().getSharedPreferences("bookmarks",
					MODE_PRIVATE);
			mBookmarksPref.registerOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onResume() {
			super.onResume();

			if (changed)
				getModules();

			if (UIUtil.isLollipop()) {
				getActivity().getWindow().setStatusBarColor(
						darkenColor(XposedApp.getColor(getActivity()), 0.85f));
			}
		}

		@Override
		public void onDestroy() {
			super.onDestroy();
			mBookmarksPref.unregisterOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onActivityCreated(@Nullable Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);

			getListView().setDivider(null);
			getListView().setDividerHeight(getDp(6));
			getListView().setPadding(getDp(8), getDp(8), getDp(8), getDp(8));
			getListView().setOnItemClickListener(this);
			getListView().setClipToPadding(false);
			setEmptyText(getString(R.string.no_bookmark_added));

			mAdapter = new BookmarkModuleAdapter(getContext());
			getModules();
			setListAdapter(mAdapter);

			setHasOptionsMenu(true);
		}

		private void getModules() {
			mAdapter.clear();
			mBookmarkedModules.clear();
			for (String s : mBookmarksPref.getAll().keySet()) {
				boolean isBookmarked = mBookmarksPref.getBoolean(s, false);

				if (isBookmarked) {
					mBookmarkedModules.add(mRepoLoader.getModule(s));
				}
			}
			Collections.sort(mBookmarkedModules, new Comparator<Module>() {
				@Override
				public int compare(Module mod1, Module mod2) {
					return mod1.name.compareTo(mod2.name);
				}
			});
			mAdapter.addAll(mBookmarkedModules);
			mAdapter.notifyDataSetChanged();
		}

		private int getDp(float value) {
			DisplayMetrics metrics = getResources().getDisplayMetrics();

			return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
					value, metrics);
		}

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			Intent detailsIntent = new Intent(getActivity(),
					DownloadDetailsActivity.class);
			detailsIntent.setData(Uri.fromParts("package",
					mBookmarkedModules.get(position).packageName, null));
			startActivity(detailsIntent);
		}

		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			changed = true;
		}
	}

	private static class BookmarkModuleAdapter extends ArrayAdapter<Module> {
		public BookmarkModuleAdapter(Context context) {
			super(context, R.layout.list_item_module, R.id.title);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = super.getView(position, convertView, parent);

			view.findViewById(R.id.checkbox).setVisibility(View.GONE);
			view.findViewById(R.id.version_name).setVisibility(View.GONE);
			view.findViewById(R.id.icon).setVisibility(View.GONE);

			Module item = getItem(position);

			((TextView) view.findViewById(R.id.title)).setText(item.name);
			((TextView) view.findViewById(R.id.description))
					.setText(item.summary);

			return view;
		}
	}
}
