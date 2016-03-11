package de.robv.android.xposed.installer;

import static de.robv.android.xposed.installer.XposedApp.darkenColor;

import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import de.robv.android.xposed.installer.util.NavUtil;
import de.robv.android.xposed.installer.util.ThemeUtil;
import de.robv.android.xposed.installer.util.UIUtil;

public class AboutActivity extends XposedBaseActivity {

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
			ab.setTitle(R.string.nav_item_about);
			ab.setDisplayHomeAsUpEnabled(true);
		}

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new AboutFragment()).commit();
		}
	}

	public static class AboutFragment extends Fragment {
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
			View v = inflater.inflate(R.layout.tab_about, container, false);

			View changelogView = v.findViewById(R.id.changelogView);
			View developersView = v.findViewById(R.id.developersView);
			View licensesView = v.findViewById(R.id.licensesView);
			View translatorsView = v.findViewById(R.id.translatorsView);
			View sourceCodeView = v.findViewById(R.id.sourceCodeView);

			String packageName = getActivity().getPackageName();
			String translator = getResources().getString(R.string.translator);

			SharedPreferences prefs = getContext().getSharedPreferences(
					packageName + "_preferences", MODE_PRIVATE);

			final String changes = prefs
					.getString("changelog_" + XposedApp.THIS_APK_VERSION, null);

			if (changes == null) {
				changelogView.setEnabled(false);
			} else {
				changelogView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						new MaterialDialog.Builder(getContext())
								.title(R.string.changes)
								.content(Html.fromHtml(changes))
								.positiveText(android.R.string.ok).show();
					}
				});
			}

			try {
				String version = getActivity().getPackageManager()
						.getPackageInfo(packageName, 0).versionName;
				((TextView) v.findViewById(R.id.app_version)).setText(version);
			} catch (NameNotFoundException ignored) {
			}

			createListener(licensesView, R.string.about_libraries_label,
					R.string.about_libraries);
			createListener(developersView, R.string.about_developers_label,
					R.string.about_developers);

			sourceCodeView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					NavUtil.startURL(getActivity(),
							getString(R.string.about_source));
				}
			});

			if (translator.isEmpty()) {
				translatorsView.setVisibility(View.GONE);
			}

			return v;
		}

		public void createListener(View v, final int title, final int content) {
			v.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					MaterialDialog dialog = new MaterialDialog.Builder(
							getContext()).title(title).content(content)
									.positiveText(android.R.string.ok).show();

					((TextView) dialog.findViewById(R.id.content))
							.setMovementMethod(
									LinkMovementMethod.getInstance());
				}
			});
		}
	}
}