package de.robv.android.xposed.installer;

import android.app.ActionBar;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

public class XposedInstallerActivity extends Activity {
	static final String TAG = "xposed_installer";
	
	static final String EXTRA_OPEN_TAB = "opentab";
	static final int TAB_INSTALL = 0;
	static final int TAB_MODULES = 1;

	static final int NOTIFICATION_MODULE_NOT_ACTIVATED_YET = 1;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancelAll();
        
        final ActionBar bar = getActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        
        bar.addTab(bar.newTab()
                .setText(R.string.tabInstall)
                .setTabListener(new TabListener<InstallerFragment>(this, "install", InstallerFragment.class)));
        
        bar.addTab(bar.newTab()
                .setText(R.string.tabModules)
                .setTabListener(new TabListener<ModulesFragment>(this, "modules", ModulesFragment .class)));
        
        if (getIntent().hasExtra(EXTRA_OPEN_TAB)) {
        	bar.setSelectedNavigationItem(getIntent().getIntExtra(EXTRA_OPEN_TAB, 0));
        } else if (savedInstanceState != null) {
            bar.setSelectedNavigationItem(savedInstanceState.getInt("tab", 0));
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", getActionBar().getSelectedNavigationIndex());
    }
}
