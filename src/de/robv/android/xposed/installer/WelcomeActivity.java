package de.robv.android.xposed.installer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.ModuleUtil.ModuleListener;
import de.robv.android.xposed.installer.util.NavUtil;
import de.robv.android.xposed.installer.util.RepoLoader;
import de.robv.android.xposed.installer.util.RepoLoader.RepoListener;

public class WelcomeActivity extends XposedBaseActivity implements ModuleListener, RepoListener {
	private RepoLoader mRepoLoader;
	private WelcomeAdapter mAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		mRepoLoader = RepoLoader.getInstance();

		setContentView(R.layout.activity_welcome);

		mAdapter = new WelcomeAdapter(this);
		// TODO add proper description texts and load them from resources, add icons, make it more fancy, ...
		mAdapter.add(new WelcomeItem(R.string.tabInstall, R.string.tabInstallDescription));
		mAdapter.add(new WelcomeItem(R.string.tabModules, R.string.tabModulesDescription));
		mAdapter.add(new WelcomeItem(R.string.tabDownload, R.string.tabDownloadDescription));
		mAdapter.add(new WelcomeItem(R.string.tabSettings, R.string.tabSettingsDescription));
		mAdapter.add(new WelcomeItem(R.string.tabLogs, R.string.tabLogsDescription));
		mAdapter.add(new WelcomeItem(R.string.tabAbout, R.string.tabAboutDescription));

		ListView lv = (ListView) findViewById(R.id.welcome_list);
		lv.setAdapter(mAdapter);
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Intent intent = new Intent(WelcomeActivity.this, XposedInstallerActivity.class);
				intent.putExtra(XposedInstallerActivity.EXTRA_SECTION, position);
				intent.putExtra(NavUtil.FINISH_ON_UP_NAVIGATION, true);
				startActivity(intent);
				NavUtil.setTransitionSlideEnter(WelcomeActivity.this);
			}
		});

		ModuleUtil.getInstance().addListener(this);
		mRepoLoader.addListener(this, false);
	}

	private void notifyDataSetChanged() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				synchronized (mAdapter) {
					mAdapter.notifyDataSetChanged();
				}
			}
		});
	}

	@Override
	public void onInstalledModulesReloaded(ModuleUtil moduleUtil) {
		notifyDataSetChanged();
	}

	@Override
	public void onSingleInstalledModuleReloaded(ModuleUtil moduleUtil, String packageName, InstalledModule module) {
		notifyDataSetChanged();
	}

	@Override
	public void onRepoReloaded(RepoLoader loader) {
		notifyDataSetChanged();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		ModuleUtil.getInstance().removeListener(this);
		mRepoLoader.removeListener(this);
	}

	class WelcomeAdapter extends ArrayAdapter<WelcomeItem> {
		public WelcomeAdapter(Context context) {
			super(context, R.layout.list_item_welcome, android.R.id.text1);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = super.getView(position, convertView, parent);
			WelcomeItem item = getItem(position);

			TextView description = (TextView) view.findViewById(android.R.id.text2);
			description.setText(item.description);

			boolean xposedActive = true;
			String frameworkUpdateVersion = null;
			boolean moduleUpdateAvailable = false;
			if (position == XposedInstallerActivity.TAB_INSTALL) {
				xposedActive = XposedApp.getActiveXposedVersion() >= InstallerFragment.getJarLatestVersion();
			} else if (position == XposedInstallerActivity.TAB_DOWNLOAD) {
				frameworkUpdateVersion = mRepoLoader.getFrameworkUpdateVersion();
				moduleUpdateAvailable = mRepoLoader.hasModuleUpdates();

				if (frameworkUpdateVersion != null) {
					((TextView) view.findViewById(R.id.txtFrameworkUpdateAvailable)).setText(
						getResources().getString(R.string.welcome_framework_update_available, (String)frameworkUpdateVersion));
				}
			}

			view.findViewById(R.id.txtXposedNotActive).setVisibility(!xposedActive ? View.VISIBLE : View.GONE);
			view.findViewById(R.id.txtFrameworkUpdateAvailable).setVisibility(frameworkUpdateVersion != null ? View.VISIBLE : View.GONE);
			view.findViewById(R.id.txtUpdateAvailable).setVisibility(moduleUpdateAvailable ? View.VISIBLE : View.GONE);

			return view;
		}
	}

	class WelcomeItem {
		public final String title;
		public final String description;

		protected WelcomeItem(int titleResId, int descriptionResId) {
			this.title = getString(titleResId);
			this.description = getString(descriptionResId);
		}

		@Override
		public String toString() {
			return title;
		}
	}
}
