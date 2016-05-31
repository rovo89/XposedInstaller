package de.robv.android.xposed.installer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
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

import static de.robv.android.xposed.installer.XposedApp.darkenColor;

public class WelcomeActivity extends XposedBaseActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        ModuleListener, RepoListener {

    private static final String SELECTED_ITEM_ID = "SELECTED_ITEM_ID";
    private final Handler mDrawerHandler = new Handler();
    private RepoLoader mRepoLoader;
    private DrawerLayout mDrawerLayout;
    private int mPrevSelectedId;
    private NavigationView mNavigationView;
    private int mSelectedId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setTheme(this);
        setContentView(R.layout.activity_welcome);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        mNavigationView = (NavigationView) findViewById(R.id.navigation_view);
        assert mNavigationView != null;
        mNavigationView.setNavigationItemSelectedListener(this);

        ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(this,
                mDrawerLayout, mToolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSelectedId = mNavigationView.getMenu().getItem(prefs.getInt("default_view", 0)).getItemId();
        mSelectedId = savedInstanceState == null ? mSelectedId : savedInstanceState.getInt(SELECTED_ITEM_ID);
        mPrevSelectedId = mSelectedId;
        mNavigationView.getMenu().findItem(mSelectedId).setChecked(true);

        if (savedInstanceState == null) {
            mDrawerHandler.removeCallbacksAndMessages(null);
            mDrawerHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    navigate(mSelectedId);
                }
            }, 250);

            boolean openDrawer = prefs.getBoolean("open_drawer", false);

            if (openDrawer)
                mDrawerLayout.openDrawer(GravityCompat.START);
            else
                mDrawerLayout.closeDrawers();
        }

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            int value = extras.getInt("fragment", prefs.getInt("default_view", 0));
            switchFragment(value);
        }

        mRepoLoader = RepoLoader.getInstance();
        ModuleUtil.getInstance().addListener(this);
        mRepoLoader.addListener(this, false);

        notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mDrawerLayout.setStatusBarBackgroundColor(darkenColor(XposedApp.getColor(this), 0.85f));

    }

    public void switchFragment(int itemId) {
        mSelectedId = mNavigationView.getMenu().getItem(itemId).getItemId();
        mNavigationView.getMenu().findItem(mSelectedId).setChecked(true);
        mDrawerHandler.removeCallbacksAndMessages(null);
        mDrawerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                navigate(mSelectedId);
            }
        }, 250);
        mDrawerLayout.closeDrawers();
    }

    private void navigate(final int itemId) {
        Fragment navFragment = null;
        switch (itemId) {
            case R.id.drawer_item_1:
                mPrevSelectedId = itemId;
                setTitle(R.string.app_name);
                navFragment = new InstallerFragment();
                break;
            case R.id.drawer_item_2:
                mPrevSelectedId = itemId;
                setTitle(R.string.nav_item_modules);
                navFragment = new ModulesFragment();
                break;
            case R.id.drawer_item_3:
                mPrevSelectedId = itemId;
                setTitle(R.string.nav_item_download);
                navFragment = new DownloadFragment();
                break;
            case R.id.drawer_item_4:
                mPrevSelectedId = itemId;
                setTitle(R.string.nav_item_logs);
                navFragment = new LogsFragment();
                break;
            case R.id.drawer_item_5:
                startActivity(new Intent(this, SettingsActivity.class));
                mNavigationView.getMenu().findItem(mPrevSelectedId).setChecked(true);
                return;
            case R.id.drawer_item_6:
                startActivity(new Intent(this, SupportActivity.class));
                mNavigationView.getMenu().findItem(mPrevSelectedId).setChecked(true);
                return;
            case R.id.drawer_item_7:
                startActivity(new Intent(this, AboutActivity.class));
                mNavigationView.getMenu().findItem(mPrevSelectedId).setChecked(true);
                return;
        }

        if (navFragment != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            try {
                transaction.replace(R.id.content_frame, navFragment).commit();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem menuItem) {
        menuItem.setChecked(true);
        mSelectedId = menuItem.getItemId();
        mDrawerHandler.removeCallbacksAndMessages(null);
        mDrawerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                navigate(mSelectedId);
            }
        }, 250);
        mDrawerLayout.closeDrawers();
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
                Snackbar.make(parentLayout, R.string.welcome_framework_update_available + " " + String.valueOf(frameworkUpdateVersion), Snackbar.LENGTH_LONG).show();
            }
        }

        boolean snackBar = getSharedPreferences(
                getPackageName() + "_preferences", MODE_PRIVATE).getBoolean("snack_bar", true);

        if (moduleUpdateAvailable && snackBar) {
            Snackbar.make(parentLayout, R.string.modules_updates_available, Snackbar.LENGTH_LONG).setAction(getString(R.string.view), new View.OnClickListener() {
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
}