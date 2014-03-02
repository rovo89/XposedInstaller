package de.robv.android.xposed.installer;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;

public class DownloadDetailsActivity extends XposedDropdownNavActivity {

	private ViewPager mPager;
	private String[] mPageTitles;
	private String mPackageName;

	private static final int DOWNLOAD_DESCRIPTION = 0;
	private static final int DOWNLOAD_VERSIONS = 1;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setNavItem(XposedDropdownNavActivity.TAB_DOWNLOAD);
		setContentView(R.layout.activity_download_details);

		mPackageName = getIntent().getData().getSchemeSpecificPart();

		mPageTitles = new String[] {getString(R.string.description_page), getString(R.string.versions_page)};
		mPager = (ViewPager) findViewById(R.id.download_pager);
		mPager.setAdapter(new ScreenSlidePagerAdapter(getFragmentManager()));
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
					return DownloadDetailsFragment.newInstance(mPackageName);
				case DOWNLOAD_VERSIONS:
					return DownloadDetailsVersionsFragment.newInstance(mPackageName);
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
