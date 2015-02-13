package de.robv.android.xposed.installer;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class SupportFragment extends Fragment {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();
		if (activity instanceof XposedDropdownNavActivity)
			((XposedDropdownNavActivity) activity).setNavItem(XposedDropdownNavActivity.TAB_SUPPORT);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ViewGroup vg = (ViewGroup) inflater.inflate(R.layout.tab_support, container, false);

		TextView txtModuleSupport = ((TextView) vg.findViewById(R.id.tab_support_module_description));
		txtModuleSupport.setText(getString(R.string.support_modules_description, getString(R.string.module_support)));
		txtModuleSupport.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(getActivity(), XposedInstallerActivity.class);
				intent.putExtra(XposedInstallerActivity.EXTRA_SECTION, XposedDropdownNavActivity.TAB_MODULES);
				startActivity(intent);
			}
		});

		return vg;
	}
}
