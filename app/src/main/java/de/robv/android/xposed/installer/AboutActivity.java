package de.robv.android.xposed.installer;

import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.robv.android.xposed.installer.util.ThemeUtil;
import de.robv.android.xposed.installer.util.UIUtil;

public class AboutActivity extends XposedBaseActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ThemeUtil.setTheme(this);
		setContentView(R.layout.activity_container);

		if (UIUtil.isLollipop()) {
			this.getWindow().setStatusBarColor(this.getResources().getColor(R.color.colorPrimaryDark));
		}

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
			ab.setTitle(R.string.nav_item_about);
			ab.setDisplayHomeAsUpEnabled(true);
		}

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new AboutFragment())
					.commit();
		}
	}

	public static class AboutFragment extends Fragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
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
}