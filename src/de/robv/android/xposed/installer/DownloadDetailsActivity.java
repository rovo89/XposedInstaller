package de.robv.android.xposed.installer;

import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;

public class DownloadDetailsActivity extends XposedDropdownNavActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		String packageName = getIntent().getData().getSchemeSpecificPart();
		DownloadDetailsFragment detailsFragment = DownloadDetailsFragment.newInstance(packageName);

		FragmentTransaction tx = getFragmentManager().beginTransaction();
		tx.replace(android.R.id.content, detailsFragment);
		tx.commit();
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
}
