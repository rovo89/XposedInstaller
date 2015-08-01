package de.robv.android.xposed.installer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.ModuleUtil.ModuleListener;
import de.robv.android.xposed.installer.util.NavUtil;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.RepoLoader.RepoListener;

public class WelcomeActivity extends XposedBaseActivity implements
		NavigationView.OnNavigationItemSelectedListener, ModuleListener, RepoListener {

	private RepoLoader mRepoLoader;
	private WelcomeAdapter mAdapter;

	private static final String SELECTED_ITEM_ID = "SELECTED_ITEM_ID";

	private Toolbar mToolbar;
	private final Handler mDrawerActionHandler = new Handler();
	private DrawerLayout mDrawerLayout;
	private ActionBarDrawerToggle mDrawerToggle;
	private int mSelectedId;

	private Fragment mMainFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_welcome);

		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mToolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(mToolbar);

		if (savedInstanceState != null) {
			/*mMainFragment = new InstallerFragment();
			final FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
			transaction.replace(R.id.content_frame, mMainFragment).commit();
		} else {
		*/
			mMainFragment = getSupportFragmentManager()
					.findFragmentById(R.id.content_frame);
		}

		// listen for navigation events
		NavigationView navigationView = (NavigationView) findViewById(R.id.navigation_view);
		navigationView.setNavigationItemSelectedListener(this);

		// set up the hamburger icon to open and close the drawer
		mDrawerToggle = new ActionBarDrawerToggle(this,
				mDrawerLayout,
				mToolbar,
				R.string.navigation_drawer_open,
				R.string.navigation_drawer_close);
		mDrawerLayout.setDrawerListener(mDrawerToggle);
		mDrawerLayout.setStatusBarBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
		mDrawerToggle.syncState();

		//ToDo add a setting to load a default view
		// select the first item
		mSelectedId = savedInstanceState == null ? R.id.drawer_item_1 : savedInstanceState.getInt(SELECTED_ITEM_ID);
		navigate(mSelectedId);

		// select the correct nav menu item
		navigationView.getMenu().findItem(mSelectedId).setChecked(true);

		//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		mRepoLoader = RepoLoader.getInstance();
/*
		mAdapter = new WelcomeAdapter(this);
		// TODO add proper description texts and load them from resources, add icons, make it more fancy, ...
		mAdapter.add(new WelcomeItem(R.string.tabInstall, R.string.tabInstallDescription));
		mAdapter.add(new WelcomeItem(R.string.tabModules, R.string.tabModulesDescription));
		mAdapter.add(new WelcomeItem(R.string.tabDownload, R.string.tabDownloadDescription));
		mAdapter.add(new WelcomeItem(R.string.tabSettings, R.string.tabSettingsDescription));
		mAdapter.add(new WelcomeItem(R.string.tabSupport, R.string.tabSupportDescription));
		mAdapter.add(new WelcomeItem(R.string.tabLogs, R.string.tabLogsDescription));
		mAdapter.add(new WelcomeItem(R.string.tabAbout, R.string.tabAboutDescription));
/*
		ListView lv = (ListView) findViewById(R.id.welcome_list);
		lv.setAdapter(mAdapter);
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Intent intent = new Intent(WelcomeActivity.this, XposedInstallerActivity.class);
				intent.putExtra(XposedInstallerActivity.EXTRA_SECTION, position);
				intent.putExtra(NavUtil.FINISH_ON_UP_NAVIGATION, true);
				startActivity(intent);
				NavUtil.setTransitionSlideEnter(WelcomeActivity.this);
			}
		});
*/
		ModuleUtil.getInstance().addListener(this);
		mRepoLoader.addListener(this, false);
	}

	private void navigate(final int itemId) {
		// perform the actual navigation logic, updating the main content fragment etc
		Fragment navFragment = null;
		switch (itemId) {
			case R.id.drawer_item_1:
				mToolbar.setTitle(R.string.app_name);
				navFragment = new InstallerFragment();
				break;
			case R.id.drawer_item_2:
				mToolbar.setTitle(R.string.nav_item_modules);
				navFragment = new ModulesFragment();
				break;
			case R.id.drawer_item_3:
				mToolbar.setTitle(R.string.nav_item_download);
				navFragment = new DownloadFragment();
				break;
			case R.id.drawer_item_4:
				mToolbar.setTitle(R.string.nav_item_logs);
				navFragment = new LogsFragment();
				break;
			case R.id.drawer_item_5:
				startActivity(new Intent(this, SettingsActivity.class));
				return;
			case R.id.drawer_item_6:
				return;
			case R.id.drawer_item_7:
				return;
		}

		if (navFragment != null) {
			FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
			transaction.replace(R.id.content_frame, navFragment).commit();
		}
	}

	@Override
	public boolean onNavigationItemSelected(MenuItem menuItem) {

		menuItem.setChecked(true);
		mSelectedId = menuItem.getItemId();

		navigate(mSelectedId);

		mDrawerLayout.closeDrawer(GravityCompat.START);
		return true;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(SELECTED_ITEM_ID, mSelectedId);
	}

	@Override
	public void onBackPressed() {
		if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
			mDrawerLayout.closeDrawer(GravityCompat.START);
		} else {
			super.onBackPressed();
		}
	}

	private void notifyDataSetChanged() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				synchronized (mAdapter) {
					mAdapter.notifyDataSetChanged();
				}
			}
		});
	}

	@Override
	public void onInstalledModulesReloaded(ModuleUtil moduleUtil) {
		notifyDataSetChanged();
	}

	@Override
	public void onSingleInstalledModuleReloaded(ModuleUtil moduleUtil, String packageName, InstalledModule module) {
		notifyDataSetChanged();
	}

	@Override
	public void onRepoReloaded(RepoLoader loader) {
		notifyDataSetChanged();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		ModuleUtil.getInstance().removeListener(this);
		mRepoLoader.removeListener(this);
	}

	class WelcomeAdapter extends ArrayAdapter<WelcomeItem> {
		public WelcomeAdapter(Context context) {
			super(context, R.layout.list_item_welcome, android.R.id.text1);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = super.getView(position, convertView, parent);
			WelcomeItem item = getItem(position);

			TextView description = (TextView) view.findViewById(android.R.id.text2);
			description.setText(item.description);

			boolean xposedActive = true;
			String frameworkUpdateVersion = null;
			boolean moduleUpdateAvailable = false;
			//ToDo fix this
			/*if (position == XposedInstallerActivity.TAB_INSTALL) {
				xposedActive = XposedApp.getActiveXposedVersion() > 0;
			} else if (position == XposedInstallerActivity.TAB_DOWNLOAD) {
				frameworkUpdateVersion = mRepoLoader.getFrameworkUpdateVersion();
				moduleUpdateAvailable = mRepoLoader.hasModuleUpdates();

				if (frameworkUpdateVersion != null) {
					((TextView) view.findViewById(R.id.txtFrameworkUpdateAvailable)).setText(
						getResources().getString(R.string.welcome_framework_update_available, (String)frameworkUpdateVersion));
				}
			}*/

			view.findViewById(R.id.txtXposedNotActive).setVisibility(!xposedActive ? View.VISIBLE : View.GONE);
			view.findViewById(R.id.txtFrameworkUpdateAvailable).setVisibility(frameworkUpdateVersion != null ? View.VISIBLE : View.GONE);
			view.findViewById(R.id.txtUpdateAvailable).setVisibility(moduleUpdateAvailable ? View.VISIBLE : View.GONE);

			return view;
		}
	}

	class WelcomeItem {
		public final String title;
		public final String description;

		protected WelcomeItem(int titleResId, int descriptionResId) {
			this.title = getString(titleResId);
			this.description = getString(descriptionResId);
		}

		@Override
		public String toString() {
			return title;
		}
	}
}
