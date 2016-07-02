package de.robv.android.xposed.installer.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Browser;
import android.support.customtabs.CustomTabsIntent;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import de.robv.android.xposed.installer.XposedApp;

public final class NavUtil {

    public static Uri parseURL(String str) {
        if (str == null || str.isEmpty())
            return null;

        Spannable spannable = new SpannableString(str);
        Linkify.addLinks(spannable, Linkify.ALL);
        URLSpan spans[] = spannable.getSpans(0, spannable.length(), URLSpan.class);
        return (spans.length > 0) ? Uri.parse(spans[0].getURL()) : null;
    }

    public static void startURL(Activity activity, Uri uri) {
        if (!XposedApp.getPreferences().getBoolean("chrome_tabs", true)) {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, activity.getPackageName());
            activity.startActivity(intent);
            return;
        }

        CustomTabsIntent.Builder customTabsIntent = new CustomTabsIntent.Builder();
        customTabsIntent.setShowTitle(true);
        customTabsIntent.setToolbarColor(XposedApp.getColor(activity));
        customTabsIntent.build().launchUrl(activity, uri);
    }

    public static void startURL(Activity activity, String url) {
        startURL(activity, parseURL(url));
    }
}