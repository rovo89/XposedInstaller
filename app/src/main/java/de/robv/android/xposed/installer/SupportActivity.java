package de.robv.android.xposed.installer;

import static de.robv.android.xposed.installer.XposedApp.darkenColor;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.robv.android.xposed.installer.util.NavUtil;
import de.robv.android.xposed.installer.util.ThemeUtil;
import de.robv.android.xposed.installer.util.UIUtil;

public class SupportActivity extends XposedBaseActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ThemeUtil.setTheme(this);
		setContentView(R.layout.activity_container);

		Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(mToolbar);

		mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				finish();
			}
		});

		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setTitle(R.string.nav_item_support);
			ab.setDisplayHomeAsUpEnabled(true);
		}

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new SupportFragment()).commit();
		}
	}

	public static class SupportFragment extends Fragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
		}

		@Override
		public void onResume() {
			super.onResume();
			if (UIUtil.isLollipop())
				getActivity().getWindow().setStatusBarColor(
						darkenColor(XposedApp.getColor(getActivity()), 0.85f));
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View v = inflater.inflate(R.layout.tab_support, container, false);

			View installerSupportView = v
					.findViewById(R.id.installerSupportView);
			View faqView = v.findViewById(R.id.faqView);
			View donateView = v.findViewById(R.id.donateView);
			TextView txtModuleSupport = (TextView) v
					.findViewById(R.id.tab_support_module_description);

			txtModuleSupport
					.setText(getString(R.string.support_modules_description,
							getString(R.string.module_support)));

			setupView(installerSupportView, R.string.about_support);
			setupView(faqView, R.string.support_faq_url);
			setupView(donateView, R.string.support_donate_url);

			return v;
		}

		public void setupView(View v, final int url) {
			v.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					NavUtil.startURL(getActivity(), getString(url));
				}
			});
		}
	}
}