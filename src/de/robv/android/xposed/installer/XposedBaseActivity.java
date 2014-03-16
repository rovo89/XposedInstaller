package de.robv.android.xposed.installer;

import android.app.Activity;
import android.os.Bundle;

import de.robv.android.xposed.installer.util.NavUtil;

public abstract class XposedBaseActivity extends Activity {
	public boolean leftActivityWithSlideAnim = false;

	private boolean mDarkThemeEnabled;

	@Override
	protected void onCreate(Bundle savedInstanceBundle) {
		super.onCreate(savedInstanceBundle);

		mDarkThemeEnabled = XposedApp.getPreferences().getBoolean("use_dark_theme", false);
		if (mDarkThemeEnabled)
			setTheme(R.style.Theme_Dark);
	}

	@Override
	protected void onResume() {
		super.onResume();

		boolean darkThemeEnabled = XposedApp.getPreferences().getBoolean("use_dark_theme", false);
		if (mDarkThemeEnabled != darkThemeEnabled) {
			mDarkThemeEnabled = darkThemeEnabled;
			recreate();
		}

		if (leftActivityWithSlideAnim)
			NavUtil.setTransitionSlideLeave(this);
		leftActivityWithSlideAnim = false;
	}

	public void setLeftWithSlideAnim(boolean newValue) {
		this.leftActivityWithSlideAnim = newValue;
	}
}
