package de.robv.android.xposed.installer;

import android.app.ActionBar;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

public class XposedInstallerActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        final ActionBar bar = getActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        
        bar.addTab(bar.newTab()
                .setText(R.string.tabInstall)
                .setTabListener(new TabListener<InstallerFragment>(this, "install", InstallerFragment.class)));
        
        bar.addTab(bar.newTab()
                .setText(R.string.tabModules)
                .setTabListener(new TabListener<ModulesFragment>(this, "modules", ModulesFragment.class)));
        
        if (savedInstanceState != null) {
            bar.setSelectedNavigationItem(savedInstanceState.getInt("tab", 0));
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", getActionBar().getSelectedNavigationIndex());
    }
}
