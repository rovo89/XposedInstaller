package de.robv.android.xposed.installer;

import android.animation.Animator;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.ModuleGroup;
import de.robv.android.xposed.installer.repo.ModuleVersion;
import de.robv.android.xposed.installer.util.AnimatorUtil;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.widget.DownloadView;
import de.robv.android.xposed.installer.widget.ExpandableStaticListView;

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
		final View view = inflater.inflate(R.layout.download_details, container, false);

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

		ExpandableStaticListView lv = (ExpandableStaticListView) view.findViewById(R.id.listVersions);
		VersionsAdapter adapter = new VersionsAdapter();
		lv.setAdapter(adapter);
		if (!adapter.isEmpty())
			lv.expandGroup(0);

		return view;
	}

	private Spanned parseSimpleHtml(String source) {
		source = source.replaceAll("<li>", "\t• ");
		source = source.replaceAll("</li>", "<br>");
		Spanned html = Html.fromHtml(source);
		
		// trim trailing newlines
		int len = html.length();
		int end = len;
		for (int i = len - 1; i >= 0; i--) {
			if (html.charAt(i) != '\n')
				break;
			end = i;
		}
		
		if (end == len)
			return html;
		else
			return new SpannableStringBuilder(html, 0, end);
	}

	@Override
	public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
		return AnimatorUtil.createSlideAnimation(this, nextAnim);
	}

	
	private class VersionsAdapter extends BaseExpandableListAdapter {
		private final LayoutInflater mLayoutInflater;

		public VersionsAdapter() {
			mLayoutInflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				v = mLayoutInflater.inflate(R.layout.list_item_version_group, parent, false);
			}

			ModuleVersion item = (ModuleVersion) getGroup(groupPosition);

			TextView txtVersion = (TextView) v.findViewById(R.id.txtVersion);
			TextView txtBranch = (TextView) v.findViewById(R.id.txtBranch);

			txtVersion.setText(item.name);
			txtBranch.setText("(main branch)");
			return v;
		}

		@Override
		public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				v = mLayoutInflater.inflate(R.layout.list_item_version, parent, false);
			}

			ModuleVersion item = (ModuleVersion) getGroup(groupPosition);

			DownloadView downloadView = (DownloadView) v.findViewById(R.id.downloadView);
			TextView txtChangesTitle = (TextView) v.findViewById(R.id.txtChangesTitle);
			TextView txtChanges = (TextView) v.findViewById(R.id.txtChanges);

			downloadView.setUrl(item.downloadLink);
			downloadView.setTitle(module.name);

			if (item.changelog != null && !item.changelog.isEmpty()) {
				txtChangesTitle.setVisibility(View.VISIBLE);
				txtChanges.setVisibility(View.VISIBLE);

				if (item.changelogIsHtml) {
					txtChanges.setText(parseSimpleHtml(item.changelog));
					txtChanges.setMovementMethod(LinkMovementMethod.getInstance());
				} else {
					txtChanges.setText(item.changelog);
					txtChanges.setMovementMethod(null);
				}

			} else {
				txtChangesTitle.setVisibility(View.GONE);
				txtChanges.setVisibility(View.GONE);
			}

			return v;
		}

		@Override
		public int getGroupCount() {
			return module.versions.size();
		}

		@Override
		public Object getGroup(int groupPosition) {
			return module.versions.get(groupPosition);
		}

		@Override
		public Object getChild(int groupPosition, int childPosition) {
			return module.versions.get(groupPosition);
		}

		@Override
		public int getChildrenCount(int groupPosition) {
			return 1;
		}

		@Override
		public long getGroupId(int groupPosition) {
			return groupPosition;
		}

		@Override
		public long getChildId(int groupPosition, int childPosition) {
			return childPosition;
		}

		@Override
		public boolean hasStableIds() {
			return false;
		}

		@Override
		public boolean isChildSelectable(int groupPosition, int childPosition) {
			return false;
		}
	}
}
