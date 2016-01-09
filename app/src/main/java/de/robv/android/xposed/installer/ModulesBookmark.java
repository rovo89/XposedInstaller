package de.robv.android.xposed.installer;

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

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.ThemeUtil;

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
			implements AdapterView.OnItemClickListener {

		private List<Module> mBookmarkedModules = new ArrayList<>();

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			SharedPreferences bookmarksPref = getContext()
					.getSharedPreferences("bookmarks", MODE_PRIVATE);

			for (String s : bookmarksPref.getAll().keySet()) {
				boolean isBookmarked = bookmarksPref.getBoolean(s, false);

				if (isBookmarked) {
					mBookmarkedModules.add(mRepoLoader.getModule(s));
				}
			}

		}

		@Override
		public void onActivityCreated(@Nullable Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);

			getListView().setDivider(null);
			getListView().setDividerHeight(getDp(6));
			getListView().setPadding(getDp(8), getDp(8), getDp(8), getDp(8));
			getListView().setOnItemClickListener(this);

			BookmarkModuleAdapter adapter = new BookmarkModuleAdapter(
					getContext());
			adapter.addAll(mBookmarkedModules);
			final Collator col = Collator.getInstance(Locale.getDefault());
			adapter.sort(new Comparator<Module>() {
				@Override
				public int compare(Module lhs, Module rhs) {
					return col.compare(lhs.name, rhs.name);
				}
			});
			setListAdapter(adapter);
			adapter.notifyDataSetChanged();

			setHasOptionsMenu(true);
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
