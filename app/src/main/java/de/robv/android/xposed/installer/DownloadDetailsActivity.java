package de.robv.android.xposed.installer;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.util.List;

import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.util.Loader;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.ModuleUtil.ModuleListener;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.ThemeUtil;

public class DownloadDetailsActivity extends XposedBaseActivity implements Loader.Listener<RepoLoader>, ModuleListener {

    public static final int DOWNLOAD_DESCRIPTION = 0;
    public static final int DOWNLOAD_VERSIONS = 1;
    public static final int DOWNLOAD_SETTINGS = 2;
    private static RepoLoader sRepoLoader = RepoLoader.getInstance();
    private static ModuleUtil sModuleUtil = ModuleUtil.getInstance();
    private ViewPager mPager;
    private String mPackageName;
    private Module mModule;
    private InstalledModule mInstalledModule;
    private MenuItem mItemBookmark;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        ThemeUtil.setTheme(this);

        mPackageName = getModulePackageName();
        mModule = sRepoLoader.getModule(mPackageName);

        mInstalledModule = ModuleUtil.getInstance().getModule(mPackageName);

        super.onCreate(savedInstanceState);
        sRepoLoader.addListener(this);
        sModuleUtil.addListener(this);

        if (mModule != null) {
            setContentView(R.layout.activity_download_details);

            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);

            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finish();
                }
            });

            ActionBar ab = getSupportActionBar();

            if (ab != null) {
                ab.setTitle(R.string.nav_item_download);
                ab.setDisplayHomeAsUpEnabled(true);
            }

            setFloating(toolbar, 0);

            setupTabs();

            Boolean directDownload = getIntent().getBooleanExtra("direct_download", false);
            // Updates available => start on the versions page
            if (mInstalledModule != null && mInstalledModule.isUpdate(sRepoLoader.getLatestVersion(mModule)) || directDownload)
                mPager.setCurrentItem(DOWNLOAD_VERSIONS);

            if (Build.VERSION.SDK_INT >= 21)
                findViewById(R.id.fake_elevation).setVisibility(View.GONE);

        } else {
            setContentView(R.layout.activity_download_details_not_found);

            TextView txtMessage = (TextView) findViewById(android.R.id.message);
            txtMessage.setText(getResources().getString(R.string.download_details_not_found, mPackageName));

            findViewById(R.id.reload).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.setEnabled(false);
                    sRepoLoader.triggerReload(true);
                }
            });
        }
    }

    private void setupTabs() {
        mPager = (ViewPager) findViewById(R.id.download_pager);
        mPager.setAdapter(new SwipeFragmentPagerAdapter(getFragmentManager()));
        TabLayout mTabLayout = (TabLayout) findViewById(R.id.sliding_tabs);
        mTabLayout.setupWithViewPager(mPager);
    }

    private String getModulePackageName() {
        Uri uri = getIntent().getData();
        if (uri == null)
            return null;

        String scheme = uri.getScheme();
        if (TextUtils.isEmpty(scheme)) {
            return null;
        } else if (scheme.equals("package")) {
            return uri.getSchemeSpecificPart();
        } else if (scheme.equals("http")) {
            List<String> segments = uri.getPathSegments();
            if (segments.size() > 1)
                return segments.get(1);
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sRepoLoader.removeListener(this);
        sModuleUtil.removeListener(this);
    }

    public Module getModule() {
        return mModule;
    }

    public InstalledModule getInstalledModule() {
        return mInstalledModule;
    }

    public void gotoPage(int page) {
        mPager.setCurrentItem(page);
    }

    private void reload() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                recreate();
            }
        });
    }

    @Override
    public void onReloadDone(RepoLoader loader) {
        reload();
    }

    @Override
    public void onInstalledModulesReloaded(ModuleUtil moduleUtil) {
        reload();
    }

    @Override
    public void onSingleInstalledModuleReloaded(ModuleUtil moduleUtil, String packageName, InstalledModule module) {
        if (packageName.equals(mPackageName))
            reload();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_download_details, menu);

        // TODO maybe enable again after checking the implementation
        menu.findItem(R.id.menu_bookmark).setVisible(false);
        menu.findItem(R.id.menu_share).setVisible(false);

        //mItemBookmark = menu.findItem(R.id.menu_bookmark);
        //setupBookmark(false);
        return true;
    }

    private void setupBookmark(boolean clicked) {
        SharedPreferences myPref = getSharedPreferences("bookmarks", MODE_PRIVATE);

        boolean saved = myPref.getBoolean(mModule.packageName, false);
        boolean newValue;

        if (clicked) {
            newValue = !saved;
            myPref.edit().putBoolean(mModule.packageName, newValue).apply();

            int msg = newValue ? R.string.bookmark_added : R.string.bookmark_removed;

            Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).show();
        }

        saved = myPref.getBoolean(mModule.packageName, false);

        if (saved) {
            mItemBookmark.setTitle(R.string.remove_bookmark);
            mItemBookmark.setIcon(R.drawable.ic_bookmark);
        } else {
            mItemBookmark.setTitle(R.string.add_bookmark);
            mItemBookmark.setIcon(R.drawable.ic_bookmark_outline);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_bookmark:
                setupBookmark(true);
                break;
            case R.id.menu_refresh:
                RepoLoader.getInstance().triggerReload(true);
                return true;
            case R.id.menu_share:
                String text = mModule.name + " - ";

                if (isPackageInstalled(mPackageName, this)) {
                    String s = getPackageManager().getInstallerPackageName(mPackageName);
                    boolean playStore;

                    try {
                        playStore = s.equals(ModulesFragment.PLAY_STORE_PACKAGE);
                    } catch (NullPointerException e) {
                        playStore = false;
                    }

					if (playStore) {
                        text += String.format(ModulesFragment.PLAY_STORE_LINK, mPackageName);
                    } else {
                        text += String.format(ModulesFragment.XPOSED_REPO_LINK, mPackageName);
                    }
                } else {
                    text += String.format(ModulesFragment.XPOSED_REPO_LINK,
                            mPackageName);
                }

                Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                sharingIntent.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(sharingIntent, getString(R.string.share)));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isPackageInstalled(String packagename, Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(packagename, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    class SwipeFragmentPagerAdapter extends FragmentPagerAdapter {
        final int PAGE_COUNT = 3;
        private String tabTitles[] = new String[]{getString(R.string.download_details_page_description), getString(R.string.download_details_page_versions), getString(R.string.download_details_page_settings),};

        public SwipeFragmentPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return PAGE_COUNT;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case DOWNLOAD_DESCRIPTION:
                    return new DownloadDetailsFragment();
                case DOWNLOAD_VERSIONS:
                    return new DownloadDetailsVersionsFragment();
                case DOWNLOAD_SETTINGS:
                    return new DownloadDetailsSettingsFragment();
                default:
                    return null;
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            // Generate title based on item position
            return tabTitles[position];
        }
    }
}
