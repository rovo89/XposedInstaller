package de.robv.android.xposed.installer.util;

import android.content.Context;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;

public final class ThemeUtil {
	private ThemeUtil() {};

	public static int getThemeColor(Context context, int id) {
		Theme theme = context.getTheme();
		TypedArray a = theme.obtainStyledAttributes(new int[] {id});
		int result = a.getColor(0, 0);
		a.recycle();
		return result;
	}
}
