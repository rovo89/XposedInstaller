package de.robv.android.xposed.installer.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Browser;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedBaseActivity;

public final class NavUtil {
	public static final String FINISH_ON_UP_NAVIGATION = "finish_on_up_navigation";
	public static final Uri EXAMPLE_URI = Uri.fromParts("http", "//example.org", null);

	public static void setTransitionSlideEnter(Activity activity) {
		activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

		if (activity instanceof XposedBaseActivity)
			((XposedBaseActivity) activity).setLeftWithSlideAnim(true);
	}

	public static void setTransitionSlideLeave(Activity activity) {
		activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
	}

	public static Uri parseURL(String str) {
		if (str == null || str.isEmpty())
			return null;

		Spannable spannable = new SpannableString(str);
		Linkify.addLinks(spannable, Linkify.ALL);
		URLSpan spans[] = spannable.getSpans(0, spannable.length(), URLSpan.class);
		return (spans.length > 0) ? Uri.parse(spans[0].getURL()) : null;
	}

	public static void startURL(Context context, Uri uri) {
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());

		if ("http".equals(uri.getScheme()) && "repo.xposed.info".equals(uri.getHost())) {
			Intent browser = new Intent(Intent.ACTION_VIEW, EXAMPLE_URI);
			ComponentName browserApp = browser.resolveActivity(context.getPackageManager());
			intent.setComponent(browserApp);
		}

		context.startActivity(intent);
	}

	public static void startURL(Context context, String url) {
		startURL(context, parseURL(url));
	}
}
