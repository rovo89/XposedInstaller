package de.robv.android.xposed.installer.util;

import android.content.Context;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.XposedBaseActivity;

public final class ThemeUtil {
	private ThemeUtil() {};

	private static int[] THEMES = new int[] {
		R.style.Theme_Light,
		R.style.Theme_Dark,
		R.style.Theme_Dark_Black,
	};

	public static int getSelectTheme() {
		int theme = XposedApp.getPreferences().getInt("theme", 0);
		return (theme >= 0 && theme < THEMES.length) ? theme : 0;
	}

	public static void setTheme(XposedBaseActivity activity) {
		activity.mTheme = getSelectTheme();
		activity.setTheme(THEMES[activity.mTheme]);
	}

	public static void reloadTheme(XposedBaseActivity activity) {
		int theme = getSelectTheme();
		if (theme != activity.mTheme)
			activity.recreate();
	}

	public static int getThemeColor(Context context, int id) {
		Theme theme = context.getTheme();
		TypedArray a = theme.obtainStyledAttributes(new int[] {id});
		int result = a.getColor(0, 0);
		a.recycle();
		return result;
	}
}
