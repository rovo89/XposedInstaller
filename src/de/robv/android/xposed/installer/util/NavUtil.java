package de.robv.android.xposed.installer.util;

import android.app.Activity;
import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedBaseActivity;

public final class NavUtil {
	public static final String FINISH_ON_UP_NAVIGATION = "finish_on_up_navigation";

	public static void setTransitionSlideEnter(Activity activity) {
		activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

		if (activity instanceof XposedBaseActivity)
			((XposedBaseActivity) activity).setLeftWithSlideAnim(true);
	}

	public static void setTransitionSlideLeave(Activity activity) {
		activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
	}
}
