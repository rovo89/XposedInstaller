package de.robv.android.xposed.installer;

import android.app.ActionBar;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
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
        
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancelAll();
        
        final ActionBar bar = getActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        
        bar.addTab(bar.newTab()
                .setText(R.string.tabInstall)
                .setTabListener(new TabListener<InstallerFragment>(this, "install", InstallerFragment.class, false)));
        
        bar.addTab(bar.newTab()
                .setText(R.string.tabModules)
                .setTabListener(new TabListener<ModulesFragment>(this, "modules", ModulesFragment .class, false)));
        
        bar.addTab(bar.newTab()
                .setText(R.string.tabDownload)
                .setTabListener(new TabListener<DownloadFragment>(this, "download", DownloadFragment .class, false)));
        
        int selectTabIndex = -1; 
        if (getIntent().hasExtra(EXTRA_OPEN_TAB)) {
        	bar.setSelectedNavigationItem(getIntent().getIntExtra(EXTRA_OPEN_TAB, 0));
        	Object extra = getIntent().getExtras().get(EXTRA_OPEN_TAB);
        	try {
        		if (extra != null)
        			selectTabIndex = (Integer) extra;
        	} catch (ClassCastException e) {
        		String extraS = extra.toString();
        		if (extraS.equals("install"))
        			selectTabIndex = 0;
        		else if (extraS.equals("modules")) 
        			selectTabIndex = 1;
        	}
        } else if (savedInstanceState != null) {
        	selectTabIndex = savedInstanceState.getInt("tab", -1);
        }
        
    	if (selectTabIndex >= 0 && selectTabIndex < bar.getTabCount())
    		bar.setSelectedNavigationItem(getIntent().getIntExtra(EXTRA_OPEN_TAB, 0));
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", getActionBar().getSelectedNavigationIndex());
    }
}
