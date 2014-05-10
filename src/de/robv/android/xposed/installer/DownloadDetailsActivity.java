package de.robv.android.xposed.installer;

import java.util.List;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.ModuleGroup;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.RepoLoader.RepoListener;


public class DownloadDetailsActivity extends XposedDropdownNavActivity implements RepoListener {

	private ViewPager mPager;
	private String[] mPageTitles;
	private String mPackageName;
	private static RepoLoader sRepoLoader = RepoLoader.getInstance();
	private ModuleGroup mModuleGroup;
	private Module mModule;

	public static final int DOWNLOAD_DESCRIPTION = 0;
	public static final int DOWNLOAD_VERSIONS = 1;
	public static final int DOWNLOAD_SETTINGS = 2;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mPackageName = getModulePackageName();
		mModuleGroup = sRepoLoader.getModuleGroup(mPackageName);
		if (mModuleGroup != null)
			mModule = mModuleGroup.getModule();

		super.onCreate(savedInstanceState);
		sRepoLoader.addListener(this, false);
		setNavItem(XposedDropdownNavActivity.TAB_DOWNLOAD);

		if (mModuleGroup != null) {
			setContentView(R.layout.activity_download_details);

			((TextView) findViewById(android.R.id.title)).setText(mModule.name);

			mPageTitles = new String[] {
				getString(R.string.download_details_page_description),
				getString(R.string.download_details_page_versions),
				getString(R.string.download_details_page_settings),
			};
			mPager = (ViewPager) findViewById(R.id.download_pager);
			mPager.setAdapter(new ScreenSlidePagerAdapter(getFragmentManager()));

			// Updates available => start on the versions page
			InstalledModule installed = ModuleUtil.getInstance().getModule(mPackageName);
			if (installed != null && installed.isUpdate(sRepoLoader.getLatestVersion(mModule)))
				mPager.setCurrentItem(DOWNLOAD_VERSIONS);

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
	}

	public Module getModule() {
		return mModule;
	}

	public void gotoPage(int page) {
		mPager.setCurrentItem(page);
	}

	@Override
	public void onRepoReloaded(RepoLoader loader) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				recreate();
			}
		});
	}

	@Override
	protected boolean navigateViaIntent() {
		return true;
	}

	@Override
	protected Intent getParentIntent() {
		Intent intent = new Intent(this, XposedInstallerActivity.class);
		intent.putExtra(XposedInstallerActivity.EXTRA_SECTION, TAB_DOWNLOAD);
		return intent;
	}

	private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {
		public ScreenSlidePagerAdapter(FragmentManager fm) {
			super(fm);
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
		public int getCount() {
			return mPageTitles.length;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return mPageTitles[position];
		}
	}

}
