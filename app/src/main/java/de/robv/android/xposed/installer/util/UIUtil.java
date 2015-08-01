package de.robv.android.xposed.installer.util;

import android.os.Build;

public class UIUtil {
    public static boolean isLollipop() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }
}
