package de.robv.android.xposed.installer.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsService;
import android.support.customtabs.CustomTabsServiceConnection;
import android.support.customtabs.CustomTabsSession;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.XposedBaseActivity;

public final class NavUtil {
	public static final String FINISH_ON_UP_NAVIGATION = "finish_on_up_navigation";
	static final String STABLE_PACKAGE = "com.android.chrome";
	static final String BETA_PACKAGE = "com.chrome.beta";
	static final String DEV_PACKAGE = "com.chrome.dev";
	static final String LOCAL_PACKAGE = "com.google.android.apps.chrome";
	private static String sPackageNameToUse;
	private static CustomTabsClient mClient;
	private static CustomTabsSession mCustomTabsSession;

	public static void setTransitionSlideEnter(Activity activity) {
		activity.overridePendingTransition(R.anim.slide_in_right,
				R.anim.slide_out_left);

		if (activity instanceof XposedBaseActivity)
			((XposedBaseActivity) activity).setLeftWithSlideAnim(true);
	}

	public static void setTransitionSlideLeave(Activity activity) {
		activity.overridePendingTransition(R.anim.slide_in_left,
				R.anim.slide_out_right);
	}

	public static Uri parseURL(String str) {
		if (str == null || str.isEmpty())
			return null;

		Spannable spannable = new SpannableString(str);
		Linkify.addLinks(spannable, Linkify.ALL);
		URLSpan spans[] = spannable.getSpans(0, spannable.length(),
				URLSpan.class);
		return (spans.length > 0) ? Uri.parse(spans[0].getURL()) : null;
	}

	public static void startURL(Activity activity, Uri uri) {
		CustomTabsServiceConnection mCustomTabsServiceConnection = new CustomTabsServiceConnection() {
			@Override
			public void onCustomTabsServiceConnected(
					ComponentName componentName,
					CustomTabsClient customTabsClient) {

				mClient = customTabsClient;
				mClient.warmup(0L);
				mCustomTabsSession = mClient.newSession(null);
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				mClient = null;
			}
		};

		CustomTabsClient.bindCustomTabsService(activity,
				getPackageNameToUse(activity), mCustomTabsServiceConnection);

		CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder(
				mCustomTabsSession)
						.setToolbarColor(XposedApp.getColor(activity))
						.setShowTitle(true).build();

		customTabsIntent.launchUrl(activity, uri);

	}

	/**
	 * Goes through all apps that handle VIEW intents and have a warmup service.
	 * Picks the one chosen by the user if there is one, otherwise makes a best
	 * effort to return a valid package name.
	 *
	 * This is <strong>not</strong> threadsafe.
	 *
	 * @param context
	 *            {@link Context} to use for accessing {@link PackageManager}.
	 * @return The package name recommended to use for connecting to custom tabs
	 *         related components.
	 */
	public static String getPackageNameToUse(Context context) {
		if (sPackageNameToUse != null)
			return sPackageNameToUse;

		PackageManager pm = context.getPackageManager();
		// Get default VIEW intent handler.
		Intent activityIntent = new Intent(Intent.ACTION_VIEW,
				Uri.parse("http://www.example.com"));
		ResolveInfo defaultViewHandlerInfo = pm.resolveActivity(activityIntent,
				0);
		String defaultViewHandlerPackageName = null;
		if (defaultViewHandlerInfo != null) {
			defaultViewHandlerPackageName = defaultViewHandlerInfo.activityInfo.packageName;
		}

		// Get all apps that can handle VIEW intents.
		List<ResolveInfo> resolvedActivityList = pm
				.queryIntentActivities(activityIntent, 0);
		List<String> packagesSupportingCustomTabs = new ArrayList<>();
		for (ResolveInfo info : resolvedActivityList) {
			Intent serviceIntent = new Intent();
			serviceIntent
					.setAction(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
			serviceIntent.setPackage(info.activityInfo.packageName);
			if (pm.resolveService(serviceIntent, 0) != null) {
				packagesSupportingCustomTabs.add(info.activityInfo.packageName);
			}
		}

		// Now packagesSupportingCustomTabs contains all apps that can handle
		// both VIEW intents
		// and service calls.
		if (packagesSupportingCustomTabs.isEmpty()) {
			sPackageNameToUse = null;
		} else if (packagesSupportingCustomTabs.size() == 1) {
			sPackageNameToUse = packagesSupportingCustomTabs.get(0);
		} else if (!TextUtils.isEmpty(defaultViewHandlerPackageName)
				&& !hasSpecializedHandlerIntents(context, activityIntent)
				&& packagesSupportingCustomTabs
						.contains(defaultViewHandlerPackageName)) {
			sPackageNameToUse = defaultViewHandlerPackageName;
		} else if (packagesSupportingCustomTabs.contains(STABLE_PACKAGE)) {
			sPackageNameToUse = STABLE_PACKAGE;
		} else if (packagesSupportingCustomTabs.contains(BETA_PACKAGE)) {
			sPackageNameToUse = BETA_PACKAGE;
		} else if (packagesSupportingCustomTabs.contains(DEV_PACKAGE)) {
			sPackageNameToUse = DEV_PACKAGE;
		} else if (packagesSupportingCustomTabs.contains(LOCAL_PACKAGE)) {
			sPackageNameToUse = LOCAL_PACKAGE;
		}
		return sPackageNameToUse;
	}

	/**
	 * Used to check whether there is a specialized handler for a given intent.
	 * 
	 * @param intent
	 *            The intent to check with.
	 * @return Whether there is a specialized handler for the given intent.
	 */
	private static boolean hasSpecializedHandlerIntents(Context context,
			Intent intent) {
		try {
			PackageManager pm = context.getPackageManager();
			List<ResolveInfo> handlers = pm.queryIntentActivities(intent,
					PackageManager.GET_RESOLVED_FILTER);
			if (handlers == null || handlers.size() == 0) {
				return false;
			}
			for (ResolveInfo resolveInfo : handlers) {
				IntentFilter filter = resolveInfo.filter;
				if (filter == null)
					continue;
				if (filter.countDataAuthorities() == 0
						|| filter.countDataPaths() == 0)
					continue;
				if (resolveInfo.activityInfo == null)
					continue;
				return true;
			}
		} catch (RuntimeException e) {
			Log.e("XposedInstaller",
					"Runtime exception while getting specialized handlers");
		}
		return false;
	}

	public static void startURL(Activity activity, String url) {
		startURL(activity, parseURL(url));
	}
}
