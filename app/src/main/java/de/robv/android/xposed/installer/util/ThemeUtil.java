package de.robv.android.xposed.installer.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.WelcomeActivity;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.XposedBaseActivity;

public final class ThemeUtil {
	private static int[] THEMES = new int[] {
			R.style.Theme_XposedInstaller_Light,
			R.style.Theme_XposedInstaller_Dark,
			R.style.Theme_XposedInstaller_Dark_Black, };

	private ThemeUtil() {
	}

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int color = activity.getResources().getColor(R.color.colorPrimaryDark);
            Drawable drawable = activity.getResources().getDrawable(R.mipmap.ic_launcher);

            if (!(activity instanceof WelcomeActivity))
                activity.getWindow().setStatusBarColor(color);

            ActivityManager.TaskDescription tDesc = new ActivityManager.TaskDescription(activity.getString(R.string.app_name), drawableToBitmap(drawable), color);
            activity.setTaskDescription(tDesc);
        }
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static int getThemeColor(Context context, int id) {
        Theme theme = context.getTheme();
        TypedArray a = theme.obtainStyledAttributes(new int[] { id });
		int result = a.getColor(0, 0);
		a.recycle();
		return result;
	}
}
