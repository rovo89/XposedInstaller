package de.robv.android.xposed.installer;

import android.app.Activity;
import android.app.Fragment;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class AboutFragment extends Fragment {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();
		if (activity instanceof XposedDropdownNavActivity)
			((XposedDropdownNavActivity) activity).setNavItem(XposedDropdownNavActivity.TAB_ABOUT);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.tab_about, container, false);

		try {
			String packageName = getActivity().getPackageName();
			String version = getActivity().getPackageManager().getPackageInfo(packageName, 0).versionName;
			((TextView) v.findViewById(R.id.version)).setText(version);
		} catch (NameNotFoundException e) {
			// should not happen
		}

		((TextView) v.findViewById(R.id.about_developers)).setMovementMethod(LinkMovementMethod.getInstance());
		((TextView) v.findViewById(R.id.about_libraries)).setMovementMethod(LinkMovementMethod.getInstance());

		String translator = getResources().getString(R.string.translator);
		if (translator.isEmpty()) {
			v.findViewById(R.id.about_translator_label).setVisibility(View.GONE);
			v.findViewById(R.id.about_translator).setVisibility(View.GONE);
		} else {
			((TextView) v.findViewById(R.id.about_translator)).setMovementMethod(LinkMovementMethod.getInstance());
		}

		return v;
	}
}
