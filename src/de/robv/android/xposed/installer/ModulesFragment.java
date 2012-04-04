package de.robv.android.xposed.installer;

import android.app.Fragment;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class ModulesFragment extends Fragment {
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.tab_modules, container, false);
		
		PackageManager pm = getActivity().getPackageManager();
		for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
			if (app.metaData == null || !app.metaData.containsKey("xposedmodule"))
				continue;
			Log.d("PackageList", "package: " + app.packageName + ", sourceDir: " + app.sourceDir);
		}
		
		return v;
	}
}