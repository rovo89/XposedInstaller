package de.robv.android.xposed.installer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;

import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.ModuleUtil.ModuleListener;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.RepoLoader.RepoListener;
import de.robv.android.xposed.installer.util.ThemeUtil;

public class WelcomeActivity extends XposedBaseActivity
		implements NavigationView.OnNavigationItemSelectedListener,
		ModuleListener, RepoListener {

	private static final String SELECTED_ITEM_ID = "SELECTED_ITEM_ID";
	private RepoLoader mRepoLoader;
	private Toolbar mToolbar;
	private DrawerLayout mDrawerLayout;
	private NavigationView mNavigationView;
	private ActionBarDrawerToggle mDrawerToggle;
	private int mSelectedId;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ThemeUtil.setTheme(this);
		setContentView(R.layout.activity_welcome);

		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mToolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(mToolbar);

		mNavigationView = (NavigationView) findViewById(R.id.navigation_view);
		mNavigationView.setNavigationItemSelectedListener(this);

		mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, mToolbar,
				R.string.navigation_drawer_open,
				R.string.navigation_drawer_close);
		mDrawerLayout.setDrawerListener(mDrawerToggle);
		mDrawerLayout.setStatusBarBackgroundColor(
				getResources().getColor(R.color.colorPrimaryDark));
		mDrawerToggle.syncState();

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		mSelectedId = mNavigationView.getMenu()
				.getItem(prefs.getInt("default_view", 0)).getItemId();
		mSelectedId = savedInstanceState == null ? mSelectedId
				: savedInstanceState.getInt(SELECTED_ITEM_ID);
		mNavigationView.getMenu().findItem(mSelectedId).setChecked(true);

		if (savedInstanceState == null) {
			navigate(mSelectedId);
		}

		mRepoLoader = RepoLoader.getInstance();
		ModuleUtil.getInstance().addListener(this);
		mRepoLoader.addListener(this, false);

		notifyDataSetChanged();
	}

	public void switchFragment(int itemId) {
		mSelectedId = mNavigationView.getMenu().getItem(itemId).getItemId();
		mNavigationView.getMenu().findItem(mSelectedId).setChecked(true);
		navigate(mSelectedId);
	}

	private void navigate(final int itemId) {
		Fragment navFragment = null;
		switch (itemId) {
			case R.id.drawer_item_1:
				setTitle(R.string.app_name);
				navFragment = new InstallerFragment();
				break;
			case R.id.drawer_item_2:
				setTitle(R.string.nav_item_modules);
				navFragment = new ModulesFragment();
				break;
			case R.id.drawer_item_3:
				setTitle(R.string.nav_item_download);
				navFragment = new DownloadFragment();
				break;
			case R.id.drawer_item_4:
				setTitle(R.string.nav_item_logs);
				navFragment = new LogsFragment();
				break;
			case R.id.drawer_item_5:
				startActivity(new Intent(this, SettingsActivity.class));
				return;
			case R.id.drawer_item_6:
				startActivity(new Intent(this, SupportActivity.class));
				return;
			case R.id.drawer_item_7:
				startActivity(new Intent(this, AboutActivity.class));
				return;
		}

		if (navFragment != null) {
			FragmentTransaction transaction = getSupportFragmentManager()
					.beginTransaction();
			transaction.replace(R.id.content_frame, navFragment).commit();
		}
	}

	@Override
	public boolean onNavigationItemSelected(MenuItem menuItem) {
		menuItem.setChecked(true);
		mSelectedId = menuItem.getItemId();
		mDrawerLayout.closeDrawer(GravityCompat.START);
		navigate(mSelectedId);
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
		View parentLayout = findViewById(R.id.content_frame);
		String frameworkUpdateVersion = mRepoLoader.getFrameworkUpdateVersion();
		boolean moduleUpdateAvailable = mRepoLoader.hasModuleUpdates();

		Fragment currentFragment = getSupportFragmentManager()
				.findFragmentById(R.id.content_frame);
		if (currentFragment instanceof DownloadDetailsFragment) {
			if (frameworkUpdateVersion != null) {
				Snackbar.make(parentLayout,
						R.string.welcome_framework_update_available + " "
								+ String.valueOf(frameworkUpdateVersion),
						Snackbar.LENGTH_LONG).show();
			}
		}

		if (moduleUpdateAvailable) {
			Snackbar.make(parentLayout, R.string.modules_updates_available,
					Snackbar.LENGTH_LONG)
					.setAction("VIEW", new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							switchFragment(2);
						}
					}).show();
		}
	}

	@Override
	public void onInstalledModulesReloaded(ModuleUtil moduleUtil) {
		notifyDataSetChanged();
	}

	@Override
	public void onSingleInstalledModuleReloaded(ModuleUtil moduleUtil,
			String packageName, InstalledModule module) {
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
}
