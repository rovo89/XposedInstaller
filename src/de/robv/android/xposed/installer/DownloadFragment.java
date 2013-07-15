package de.robv.android.xposed.installer;

import android.animation.Animator;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.ModuleGroup;
import de.robv.android.xposed.installer.repo.ModuleVersion;
import de.robv.android.xposed.installer.util.AnimatorUtil;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.RepoLoader.RepoListener;

public class DownloadFragment extends Fragment implements RepoListener {
	private DownloadsAdapter adapter;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setHasOptionsMenu(true);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();
		if (activity instanceof XposedInstallerActivity)
			((XposedInstallerActivity) activity).setNavItem(XposedInstallerActivity.TAB_DOWNLOAD, null);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.tab_downloader, container, false);
		ListView lv = (ListView) v.findViewById(R.id.listModules);
		
		adapter = new DownloadsAdapter(getActivity());
		RepoLoader.getInstance().addListener(this, true);
		lv.setAdapter(adapter);
		
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				ModuleGroup ref = adapter.getItem(position);
				DownloadDetailsFragment fragment = DownloadDetailsFragment.newInstance(ref.packageName);

				FragmentTransaction tx = getFragmentManager().beginTransaction();
				// requires onCreateAnimator() to be overridden!
				tx.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
						R.anim.slide_in_left, R.anim.slide_out_right);
				tx.replace(android.R.id.content, fragment);
				tx.addToBackStack("downloads_overview");
				tx.commit();
			}
		});
		return v;
	}
	
	@Override
	public void onDestroyView() {
	    super.onDestroyView();
	    adapter = null;
	    RepoLoader.getInstance().removeListener(this);
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_download, menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_refresh:
				RepoLoader.getInstance().triggerReload();
				Toast.makeText(getActivity(), "Reloading...", Toast.LENGTH_SHORT).show();
				break;
		}
	    return super.onOptionsItemSelected(item);
	}

	@Override
	public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
		return AnimatorUtil.createSlideAnimation(this, nextAnim);
	}
	
	@Override
	public void onRepoReloaded(final RepoLoader loader) {
		if (adapter == null)
			return;
		
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				synchronized (adapter) {
					adapter.clear();
					adapter.addAll(loader.getModules().values());
					adapter.sort(null);
		        }
			}
		});
	}

	private class DownloadsAdapter extends ArrayAdapter<ModuleGroup> {
		public DownloadsAdapter(Context context) {
			super(context, R.layout.list_item_download, android.R.id.text1);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = super.getView(position, convertView, parent);

			Module module = getItem(position).getModule();
			ModuleVersion latest = ModuleUtil.getInstance().getLatestVersion(module);
			InstalledModule installed = ModuleUtil.getInstance().getModule(module.packageName);

			TextView txtSummary = (TextView) view.findViewById(android.R.id.text2);
			txtSummary.setText(module.summary);
			
			TextView txtStatus = (TextView) view.findViewById(R.id.downloadStatus);
			if (installed != null && installed.isUpdate(latest)) {
				txtStatus.setText(String.format("Update available (version %s \u2192 %s)", installed.versionName, latest.name));
				txtStatus.setTextColor(getResources().getColor(R.color.download_status_update_available));
				txtStatus.setVisibility(View.VISIBLE);
			} else if (installed != null) {
				txtStatus.setText(String.format("Installed (version %s)", installed.versionName));
				txtStatus.setTextColor(getResources().getColor(R.color.download_status_installed));
				txtStatus.setVisibility(View.VISIBLE);
			} else {
				txtStatus.setVisibility(View.GONE);
			}

			return view;
		}
	}
}
