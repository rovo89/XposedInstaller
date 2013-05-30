package de.robv.android.xposed.installer;

import android.animation.Animator;
import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.ModuleGroup;
import de.robv.android.xposed.installer.repo.ModuleVersion;
import de.robv.android.xposed.installer.util.AnimatorUtil;
import de.robv.android.xposed.installer.util.RepoLoader;

public class DownloadDetailsFragment extends Fragment {
	public static final String ARGUMENT_PACKAGE = "package";
	private String packageName;
	private ModuleGroup moduleGroup;
	private Module module;

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
		View view = inflater.inflate(R.layout.download_details, container, false);

		Bundle args = getArguments();
		packageName = args.getString(ARGUMENT_PACKAGE);
		moduleGroup = RepoLoader.getInstance().getModule(packageName);
		module = moduleGroup.getModule();

		TextView title = (TextView) view.findViewById(R.id.download_title);
		title.setText(module.name);

		TextView description = (TextView) view.findViewById(R.id.download_description);
		if (module.description != null) {
			if (module.descriptionIsHtml) {
				description.setText(parseSimpleHtml(module.description));
				description.setMovementMethod(LinkMovementMethod.getInstance());
			} else {
				description.setText(module.description);
			}
		} else {
			description.setVisibility(View.GONE);
		}

		final ModuleVersion latestVersion = module.versions.isEmpty() ? null : module.versions.get(0);

		Button btnDownload = (Button) view.findViewById(R.id.btn_download);
		if (latestVersion != null && latestVersion.downloadLink != null) {
			btnDownload.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Toast.makeText(getActivity(), latestVersion.downloadLink, Toast.LENGTH_LONG).show();
				}
			});
		} else {
			btnDownload.setEnabled(false);
		}

		return view;
	}

	private Spanned parseSimpleHtml(String source) {
		source = source.replaceAll("<li>", "\t• ");
		source = source.replaceAll("</li>", "<br>");
		return Html.fromHtml(source);
	}

	@Override
	public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
		return AnimatorUtil.createSlideAnimation(this, nextAnim);
	}
}
