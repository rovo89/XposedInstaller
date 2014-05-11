package de.robv.android.xposed.installer.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Parcelable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedBaseActivity;

public final class NavUtil {
	public static final String FINISH_ON_UP_NAVIGATION = "finish_on_up_navigation";
	private static final String XPOSED_INSTALLER_PACKAGE_NAME = "de.robv.android.xposed.installer";

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
		List<Intent> targetIntents = new ArrayList<Intent>();

		// Build a list of intents for activities that can open the URL, excluding this app
		List<ResolveInfo> intentActivities = context.getPackageManager().queryIntentActivities(intent, 0);
		for (ResolveInfo activityInfo : intentActivities) {
			String packageName = activityInfo.activityInfo.packageName;
			if (!packageName.equals(XPOSED_INSTALLER_PACKAGE_NAME)) {
				Intent targetIntent = new Intent(Intent.ACTION_VIEW, uri);
				targetIntent.setPackage(packageName);
				targetIntents.add(targetIntent);
			}
		}

		Intent chooserIntent = Intent.createChooser(targetIntents.remove(0),context.getString(R.string.whichApplication));
		chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toArray(new Parcelable[targetIntents.size()]));
		context.startActivity(chooserIntent);
	}

	public static void startURL(Context context, String url) {
		startURL(context, parseURL(url));
	}
}
