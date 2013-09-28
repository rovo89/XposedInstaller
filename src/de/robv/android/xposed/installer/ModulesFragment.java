package de.robv.android.xposed.installer;

import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.RepoLoader;

public class ModulesFragment extends ListFragment {
	public static final String SETTINGS_CATEGORY = "de.robv.android.xposed.category.MODULE_SETTINGS";
	private int installedXposedVersion;
	private ModuleUtil mModuleUtil;
	private RepoLoader mRepoLoader;

	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    mModuleUtil = ModuleUtil.getInstance();
	    mRepoLoader = RepoLoader.getInstance();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		Activity activity = getActivity();
		if (activity instanceof XposedInstallerActivity)
			((XposedInstallerActivity) activity).setNavItem(XposedInstallerActivity.TAB_MODULES, null);

		installedXposedVersion = InstallerFragment.getJarInstalledVersion();

		ModuleAdapter modules = new ModuleAdapter(getActivity());
		modules.addAll(mModuleUtil.getModules().values());
		modules.sort(new Comparator<InstalledModule>() {
			@Override
			public int compare(InstalledModule lhs, InstalledModule rhs) {
				return lhs.getAppName().compareTo(rhs.getAppName());
			}
		});

		setListAdapter(modules);
		setEmptyText(getActivity().getString(R.string.no_xposed_modules_found));

		getListView().setFastScrollEnabled(true);

		getListView().setDivider(getResources().getDrawable(R.color.list_divider));
		getListView().setDividerHeight(1);
		registerForContextMenu(getListView());
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		String packageName = (String) v.getTag();
		Intent launchIntent = getSettingsIntent(packageName);
		if (launchIntent != null)
			startActivity(launchIntent);
		else
			Toast.makeText(getActivity(), getActivity().getString(R.string.module_no_ui), Toast.LENGTH_LONG).show();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		InstalledModule module = getItemFromContextMenuInfo(menuInfo);
		menu.setHeaderTitle(module.getAppName());

		getActivity().getMenuInflater().inflate(R.menu.context_menu_modules, menu);

		if (getSettingsIntent(module.packageName) == null)
			menu.removeItem(R.id.menu_launch);

		if (mRepoLoader.getModuleGroup(module.packageName) == null)
			menu.removeItem(R.id.menu_download_updates);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		InstalledModule module = getItemFromContextMenuInfo(item.getMenuInfo());
		switch (item.getItemId()) {
			case R.id.menu_launch:
				startActivity(getSettingsIntent(module.packageName));
				return true;

			case R.id.menu_download_updates:
				DownloadDetailsFragment detailsFragment = DownloadDetailsFragment.newInstance(module.packageName);

				FragmentTransaction tx = getFragmentManager().beginTransaction();
				tx.replace(android.R.id.content, detailsFragment);
				// FIXME Up navigation brings us back to the module section instead of the downloads overview
				tx.addToBackStack("downloads_overview");
				tx.commit();
				return true;

			case R.id.menu_application_settings:
				startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
					Uri.fromParts("package", module.packageName, null)));
	            return true;

			case R.id.menu_uninstall:
				startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
					Uri.fromParts("package", module.packageName, null)));
				return true;
		}

		return false;
	}

	private InstalledModule getItemFromContextMenuInfo(ContextMenuInfo menuInfo) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		return (InstalledModule) getListAdapter().getItem(info.position);
	}

	private Intent getSettingsIntent(String packageName) {
		// taken from ApplicationPackageManager.getLaunchIntentForPackage(String)
		// first looks for an Xposed-specific category, falls back to getLaunchIntentForPackage
		PackageManager pm = getActivity().getPackageManager();

		Intent intentToResolve = new Intent(Intent.ACTION_MAIN);
		intentToResolve.addCategory(SETTINGS_CATEGORY);
		intentToResolve.setPackage(packageName);
		List<ResolveInfo> ris = pm.queryIntentActivities(intentToResolve, 0);

		if (ris == null || ris.size() <= 0) {
			return pm.getLaunchIntentForPackage(packageName);
		}

		Intent intent = new Intent(intentToResolve);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.setClassName(ris.get(0).activityInfo.packageName, ris.get(0).activityInfo.name);
		return intent;
	}

	private class ModuleAdapter extends ArrayAdapter<InstalledModule> {
		public ModuleAdapter(Context context) {
			super(context, R.layout.list_item_module, R.id.text);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = super.getView(position, convertView, parent);

			if (convertView == null) {
				// The reusable view was created for the first time, set up the listener on the checkbox
				((CheckBox) view.findViewById(R.id.checkbox)).setOnCheckedChangeListener(new OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						String packageName = (String) buttonView.getTag();
						boolean changed = mModuleUtil.isModuleEnabled(packageName) ^ isChecked;
						if (changed) {
							mModuleUtil.setModuleEnabled(packageName, isChecked);
							mModuleUtil.updateModulesList();
						}
					}
				});
			}

			InstalledModule item = getItem(position);
			// Store the package name in some views' tag for later access
			((CheckBox) view.findViewById(R.id.checkbox)).setTag(item.packageName);
			view.setTag(item.packageName);

			((ImageView) view.findViewById(R.id.icon)).setImageDrawable(item.getIcon());

			TextView descriptionText = (TextView) view.findViewById(R.id.description);
			if (!item.getDescription().isEmpty()) {
				descriptionText.setText(item.getDescription());
				descriptionText.setTextColor(0xFF777777);
			} else {
				descriptionText.setText(getActivity().getString(R.string.module_empty_description));
				descriptionText.setTextColor(0xFFCC7700);
			}

			CheckBox checkbox = (CheckBox) view.findViewById(R.id.checkbox);
			checkbox.setChecked(mModuleUtil.isModuleEnabled(item.packageName));
			TextView warningText = (TextView) view.findViewById(R.id.warning);

			if (item.minVersion == 0) {
				checkbox.setEnabled(false);
				warningText.setText(getString(R.string.no_min_version_specified));
				warningText.setVisibility(View.VISIBLE);
			} else if (installedXposedVersion != 0 && item.minVersion > installedXposedVersion) {
				checkbox.setEnabled(false);
				warningText.setText(String.format(getString(R.string.warning_xposed_min_version), 
						item.minVersion));
				warningText.setVisibility(View.VISIBLE);
			} else if (item.minVersion < ModuleUtil.MIN_MODULE_VERSION) {
				checkbox.setEnabled(false);
				warningText.setText(String.format(getString(R.string.warning_min_version_too_low), 
						item.minVersion, ModuleUtil.MIN_MODULE_VERSION));
				warningText.setVisibility(View.VISIBLE);
			} else {
				checkbox.setEnabled(true);
				warningText.setVisibility(View.GONE);
			}
			return view;
		}

	}
}
