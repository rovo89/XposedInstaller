package de.robv.android.xposed.installer;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import de.robv.android.xposed.installer.util.ThemeUtil;

public abstract class XposedBaseActivity extends AppCompatActivity {
    public int mTheme = -1;

    @Override
    protected void onCreate(Bundle savedInstanceBundle) {
        super.onCreate(savedInstanceBundle);
        ThemeUtil.setTheme(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        XposedApp.setColors(getSupportActionBar(), XposedApp.getColor(this), this);
        ThemeUtil.reloadTheme(this);
    }
}