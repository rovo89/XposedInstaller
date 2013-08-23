package de.robv.android.xposed.installer;

import android.app.Activity;
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
import de.robv.android.xposed.installer.util.RepoLoader;

public class WelcomeActivity extends Activity {
	private RepoLoader repoLoader;
	private WelcomeAdapter mAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		repoLoader = RepoLoader.getInstance();

		setContentView(R.layout.activity_welcome);
		
		mAdapter = new WelcomeAdapter(this);
		// TODO add proper description texts and load them from resources, add icons, make it more fancy, ... 
		mAdapter.add(new WelcomeItem(R.string.tabInstall, R.string.tabInstallDescription));
		mAdapter.add(new WelcomeItem(R.string.tabModules, R.string.tabModulesDescription));
		mAdapter.add(new WelcomeItem(R.string.tabDownload, R.string.tabDownloadDescription));
		mAdapter.add(new WelcomeItem(R.string.tabSettings, R.string.tabSettingsDescription));
		mAdapter.add(new WelcomeItem(R.string.tabAbout, R.string.tabAboutDescription));
		
		ListView lv = (ListView) findViewById(R.id.welcome_list);
		lv.setAdapter(mAdapter);
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Intent intent = new Intent(WelcomeActivity.this, XposedInstallerActivity.class);
				intent.putExtra(XposedInstallerActivity.EXTRA_OPEN_TAB, position);
				startActivity(intent);
				overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
			}
		});
	}
	
	@Override
	protected void onRestart() {
	    super.onRestart();
	    // refresh update status
	    mAdapter.notifyDataSetChanged();
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

		    boolean frameworkUpdateAvailable = false;
		    boolean moduleUpdateAvailable = false;
		    if (position == XposedInstallerActivity.TAB_DOWNLOAD) {
				frameworkUpdateAvailable = repoLoader.hasFrameworkUpdate();
				moduleUpdateAvailable = repoLoader.hasModuleUpdates();
		    }

		    view.findViewById(R.id.txtFrameworkUpdateAvailable).setVisibility(frameworkUpdateAvailable ? View.VISIBLE : View.GONE);
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
