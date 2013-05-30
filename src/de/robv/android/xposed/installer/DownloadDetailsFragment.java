package de.robv.android.xposed.installer;

import android.animation.Animator;
import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import de.robv.android.xposed.installer.util.AnimatorUtil;

public class DownloadDetailsFragment extends Fragment {
	public static final String ARGUMENT_PACKAGE = "package";

	public static DownloadDetailsFragment newInstance(String packageName) {
		DownloadDetailsFragment fragment = new DownloadDetailsFragment();
		
		Bundle args = new Bundle();
		args.putString(ARGUMENT_PACKAGE, packageName);
		fragment.setArguments(args);
		
		return fragment;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();
		if (activity instanceof XposedInstallerActivity)
			((XposedInstallerActivity) activity).setNavItem(XposedInstallerActivity.TAB_DOWNLOAD, "downloads_overview");
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		TextView v = new TextView(getActivity());
		Bundle args = getArguments();
		String packageName = args.getString(ARGUMENT_PACKAGE);
		v.setText("DownloadDetailsFragment for " + packageName);
		return v;
	}
	
	@Override
	public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
		return AnimatorUtil.createSlideAnimation(this, nextAnim);
	}
}
