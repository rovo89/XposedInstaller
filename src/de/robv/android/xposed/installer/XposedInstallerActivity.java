package de.robv.android.xposed.installer;

import java.util.HashMap;

import android.content.Intent;
import android.os.Bundle;

public class XposedInstallerActivity extends XposedDropdownNavActivity {
	public static final String EXTRA_OPEN_TAB = "opentab";

	private static final HashMap<String, Integer> TABS;
	static {
		TABS = new HashMap<String, Integer>(TAB_COUNT, 1);
		TABS.put("install", TAB_INSTALL);
		TABS.put("modules", TAB_MODULES);
		TABS.put("download", TAB_DOWNLOAD);
		TABS.put("settings", TAB_SETTINGS);
		TABS.put("about", TAB_ABOUT);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		selectInitialTab(getIntent(), savedInstanceState);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt("tab", getActionBar().getSelectedNavigationIndex());
	}

	private void selectInitialTab(Intent intent, Bundle savedInstanceState) {
		int selectTabIndex = -1;
		if (intent.hasExtra(XposedInstallerActivity.EXTRA_OPEN_TAB)) {
			Object extra = intent.getExtras().get(XposedInstallerActivity.EXTRA_OPEN_TAB);
			if (extra instanceof Integer)
				selectTabIndex = (Integer) extra;
			else if (extra instanceof String && TABS.containsKey(extra))
				selectTabIndex = TABS.get(extra);

		} else if (savedInstanceState != null) {
			selectTabIndex = savedInstanceState.getInt("tab", -1);
		}

		if (selectTabIndex >= 0 && selectTabIndex < TAB_COUNT)
			getActionBar().setSelectedNavigationItem(selectTabIndex);
	}
}
