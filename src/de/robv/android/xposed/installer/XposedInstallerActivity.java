package de.robv.android.xposed.installer;

import android.view.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.OnNavigationListener;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.Window;
import android.widget.SimpleAdapter;

public class XposedInstallerActivity extends ActionBarActivity {
	static final String TAG = "xposed_installer";
	
	static final String EXTRA_OPEN_TAB = "opentab";
	static final int TAB_INSTALL = 0;
	static final int TAB_MODULES = 1;
	static final int TAB_DOWNLOAD = 2;
	static final int TAB_SETTINGS = 3;
	static final int TAB_ABOUT= 4;

	static final int NOTIFICATION_MODULE_NOT_ACTIVATED_YET = 1;
	
	int currentNavItem = -1;
	String popToStateOnUp = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.abc_screen);
        findViewById(R.id.title_container).setVisibility(View.GONE);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancelAll();
        
		final ActionBar bar = getSupportActionBar();
		bar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		bar.setDisplayShowTitleEnabled(false);
		bar.setDisplayHomeAsUpEnabled(true);

		final List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
		data.add(makeNavigationItem(getString(R.string.tabInstall), InstallerFragment.class));
		data.add(makeNavigationItem(getString(R.string.tabModules), ModulesFragment.class));
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
			data.add(makeNavigationItem(getString(R.string.tabDownload), DownloadFragment.class));
		} else {
			data.add(makeNavigationItem(getString(R.string.tabDownload)));
		}
		data.add(makeNavigationItem(getString(R.string.tabSettings)));
		data.add(makeNavigationItem(getString(R.string.tabAbout), AboutFragment.class));

		SimpleAdapter adapter = new SimpleAdapter(this, data,
				android.R.layout.simple_spinner_dropdown_item,
				new String[] { "title" },
				new int[] { android.R.id.text1 });

		bar.setListNavigationCallbacks(adapter, new OnNavigationListener() {
			@Override
			public boolean onNavigationItemSelected(int itemPosition, long itemId) {
				if (currentNavItem == itemPosition)
					return true;

				Map<String, Object> map = data.get(itemPosition);

				Fragment fragment = (Fragment) map.get("fragment");
				if (fragment == null) {
					String fragmentClass = (String) map.get("fragment_class");
					if (fragmentClass == null) {
						return false;
					}
					fragment = Fragment.instantiate(XposedInstallerActivity.this, fragmentClass);
					map.put("fragment", fragment);
				}

				FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
				tx.replace(R.id.action_bar_activity_content, fragment);
				if (currentNavItem != -1)
					tx.addToBackStack(null);
				currentNavItem = itemPosition;
				tx.commit();

				getSupportFragmentManager().executePendingTransactions();

				return true;
			}
		});

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
        			selectTabIndex = TAB_INSTALL;
        		else if (extraS.equals("modules")) 
        			selectTabIndex = TAB_MODULES;
        		else if (extraS.equals("download"))
        			selectTabIndex = TAB_DOWNLOAD;
				else if (extraS.equals("settings"))
					selectTabIndex = TAB_SETTINGS;
				else if (extraS.equals("about"))
					selectTabIndex = TAB_ABOUT;
			}
        } else if (savedInstanceState != null) {
        	selectTabIndex = savedInstanceState.getInt("tab", -1);
        }
        
        if (selectTabIndex >= 0 && selectTabIndex < bar.getNavigationItemCount())
    		bar.setSelectedNavigationItem(getIntent().getIntExtra(EXTRA_OPEN_TAB, 0));
    }
    
	private Map<String, Object> makeNavigationItem(String title, Class<? extends Fragment> fragmentClass) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("title", title);
		map.put("fragment_class", fragmentClass.getName());
		return map;
	}

	private Map<String, Object> makeNavigationItem(String title) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("title", title + "(x)");
		map.put("fragment_class", null);
		return map;
	}

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", getSupportActionBar().getSelectedNavigationIndex());
    }

	void setNavItem(int position, String popToStateOnUp) {
		this.currentNavItem = position;
		this.popToStateOnUp = popToStateOnUp;
		getSupportActionBar().setSelectedNavigationItem(position);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			if (popToStateOnUp != null) {
				getSupportFragmentManager().popBackStack(popToStateOnUp, FragmentManager.POP_BACK_STACK_INCLUSIVE);
			} else {
				finish();
			}
		}
	    return super.onOptionsItemSelected(item);
	}

	@Override
	public void finish() {
	    super.finish();
	    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
	}
}
