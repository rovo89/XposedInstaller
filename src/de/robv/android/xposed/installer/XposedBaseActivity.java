package de.robv.android.xposed.installer;

import android.app.Activity;
import android.os.Bundle;

import de.robv.android.xposed.installer.util.NavUtil;

public abstract class XposedBaseActivity extends Activity {
	public boolean leftActivityWithSlideAnim = false;

	@Override
	protected void onCreate(Bundle savedInstanceBundle) {
		super.onCreate(savedInstanceBundle);

		if (XposedApp.getPreferences().getBoolean("use_dark_theme", false))
			setTheme(R.style.Theme_Dark);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (leftActivityWithSlideAnim)
			NavUtil.setTransitionSlideLeave(this);
		leftActivityWithSlideAnim = false;
	}

	public void setLeftWithSlideAnim(boolean newValue) {
		this.leftActivityWithSlideAnim = newValue;
	}
}
