package de.robv.android.xposed.installer;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;

import de.robv.android.xposed.installer.installation.StatusInstallerFragment;
import de.robv.android.xposed.installer.util.Loader;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.ModuleUtil.ModuleListener;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.ThemeUtil;

public class WelcomeActivity extends XposedBaseActivity implements NavigationView.OnNavigationItemSelectedListener,
        DrawerLayout.DrawerListener, ModuleListener, Loader.Listener<RepoLoader> {

    private RepoLoader mRepoLoader;
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private Fragment fragmentToOpen;
    private Class activityToOpen;
    private int currItemId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setTheme(this);
        setContentView(R.layout.activity_welcome);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mNavigationView = (NavigationView) findViewById(R.id.navigation_view);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setHomeAsUpIndicator(R.drawable.ic_menu);
        }

        mDrawerLayout.addDrawerListener(this);
        mNavigationView.setNavigationItemSelectedListener(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int defaultViewId = prefs.getInt("default_view", 0);
        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            int intentViewId = extras.getInt("fragment", defaultViewId);
            switchFragment(intentViewId);
        } else if (savedInstanceState == null) {
            switchFragment(defaultViewId);
        }

        mRepoLoader = RepoLoader.getInstance();
        ModuleUtil.getInstance().addListener(this);
        mRepoLoader.addListener(this);

        notifyDataSetChanged();
    }

    public void switchFragment(int menuItemIdx) {
        int itemId = mNavigationView.getMenu().getItem(menuItemIdx).getItemId();
        prepareNavigation(itemId);
        navigate();
        mNavigationView.getMenu().findItem(itemId).setChecked(true);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        // True selects menu item, false doesn't
        return prepareNavigation(menuItem.getItemId());
    }

    @Override
    public void onDrawerClosed(View drawerView) {
        navigate();
    }

    private boolean prepareNavigation(int itemId) {
        switch (itemId) {
            case R.id.nav_item_framework:
                fragmentToOpen = new StatusInstallerFragment();
                break;
            case R.id.nav_item_modules:
                fragmentToOpen = new ModulesFragment();
                break;
            case R.id.nav_item_downloads:
                fragmentToOpen = new DownloadFragment();
                break;
            case R.id.nav_item_logs:
                fragmentToOpen = new LogsFragment();
                break;
            case R.id.nav_item_settings:
                activityToOpen = SettingsActivity.class;
                break;
            case R.id.nav_item_support:
                activityToOpen = SupportActivity.class;
                break;
            case R.id.nav_item_about:
                activityToOpen = AboutActivity.class;
        }

        if (activityToOpen == null)
            currItemId = itemId;

        mDrawerLayout.closeDrawers();

        // Returns true if Fragment, false if Activity
        return (activityToOpen == null);
    }

    private void navigate() {
        if (fragmentToOpen != null) {
            FragmentTransaction transaction = getFragmentManager().beginTransaction();

            if (getFragmentManager().findFragmentById(R.id.content_frame) != null) {
                transaction.replace(R.id.content_frame, fragmentToOpen, String.valueOf(currItemId))
                        .addToBackStack(null)
                        .commit();
            } else {
                transaction.add(R.id.content_frame, fragmentToOpen, String.valueOf(currItemId))
                        .commit();
            }
        }

        if (activityToOpen != null) {
            startActivity(new Intent(this, activityToOpen));
        }

        activityToOpen = null;
        fragmentToOpen = null;
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
            // Reselect previous menu items when navigating through the back stack
            mNavigationView.getMenu()
                    .findItem(Integer.parseInt(getFragmentManager().findFragmentById(R.id.content_frame).getTag()))
                    .setChecked(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Open the navigation drawer if the hamburger button was clicked
                mDrawerLayout.openDrawer(GravityCompat.START);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void notifyDataSetChanged() {
        View parentLayout = findViewById(R.id.content_frame);
        String frameworkUpdateVersion = mRepoLoader.getFrameworkUpdateVersion();
        boolean moduleUpdateAvailable = mRepoLoader.hasModuleUpdates();

        Fragment currentFragment = getFragmentManager().findFragmentById(R.id.content_frame);
        if (currentFragment instanceof DownloadDetailsFragment) {
            if (frameworkUpdateVersion != null) {
                Snackbar.make(parentLayout, R.string.welcome_framework_update_available + " " + String.valueOf(frameworkUpdateVersion), Snackbar.LENGTH_LONG).show();
            }
        }

        boolean snackBar = XposedApp.getPreferences().getBoolean("snack_bar", true);

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
    public void onReloadDone(RepoLoader loader) {
        notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ModuleUtil.getInstance().removeListener(this);
        mRepoLoader.removeListener(this);
    }

    @Override
    public void onDrawerSlide(View drawerView, float slideOffset) {}

    @Override
    public void onDrawerOpened(View drawerView) {}

    @Override
    public void onDrawerStateChanged(int newState) {}
}
