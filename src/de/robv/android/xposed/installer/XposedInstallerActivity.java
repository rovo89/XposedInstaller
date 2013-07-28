package de.robv.android.xposed.installer;

import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;

public class XposedInstallerActivity extends ActionBarActivity {
	static final String TAG = "xposed_installer";
	
	static final String EXTRA_OPEN_TAB = "opentab";
	static final int TAB_INSTALL = 0;
	static final int TAB_MODULES = 1;

	static final int NOTIFICATION_MODULE_NOT_ACTIVATED_YET = 1;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.abc_screen);
        findViewById(R.id.title_container).setVisibility(View.GONE);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancelAll();
        
        final ActionBar bar = getSupportActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        
        bar.addTab(bar.newTab()
                .setText(R.string.tabInstall)
                .setTabListener(new TabListener<InstallerFragment>(this, "install", InstallerFragment.class, false)));
        
        bar.addTab(bar.newTab()
                .setText(R.string.tabModules)
                .setTabListener(new TabListener<ModulesFragment>(this, "modules", ModulesFragment .class, false)));
        
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
        outState.putInt("tab", getSupportActionBar().getSelectedNavigationIndex());
    }
}
