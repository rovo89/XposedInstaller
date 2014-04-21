package de.robv.android.xposed.installer;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.widget.TextView;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.ModuleGroup;
import de.robv.android.xposed.installer.util.RepoLoader;

public class DownloadDetailsActivity extends XposedDropdownNavActivity {

	private ViewPager mPager;
	private String[] mPageTitles;
	private String mPackageName;
	private ModuleGroup mModuleGroup;
	private Module mModule;

	private static final int DOWNLOAD_DESCRIPTION = 0;
	private static final int DOWNLOAD_VERSIONS = 1;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setNavItem(XposedDropdownNavActivity.TAB_DOWNLOAD);

		mPackageName = getIntent().getData().getSchemeSpecificPart();
		mModuleGroup = RepoLoader.getInstance().waitForFirstLoadFinished().getModuleGroup(mPackageName);
		if (mModuleGroup == null) {
			setContentView(R.layout.activity_download_details_not_found);
			TextView txtMessage = (TextView) findViewById(android.R.id.message);
			txtMessage.setText(getResources().getString(R.string.download_details_not_found, mPackageName));
			return;
		}

		mModule = mModuleGroup.getModule();

		setContentView(R.layout.activity_download_details);
		mPageTitles = new String[] {getString(R.string.description_page), getString(R.string.versions_page)};
		mPager = (ViewPager) findViewById(R.id.download_pager);
		mPager.setAdapter(new ScreenSlidePagerAdapter(getFragmentManager()));
	}

	public Module getModule() {
		return mModule;
	}

	@Override
	protected boolean navigateViaIntent() {
		return true;
	}

	@Override
	protected Intent getParentIntent() {
		Intent intent = new Intent(this, XposedInstallerActivity.class);
		intent.putExtra(XposedInstallerActivity.EXTRA_OPEN_TAB, TAB_DOWNLOAD);
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
