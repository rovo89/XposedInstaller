package de.robv.android.xposed.installer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import de.robv.android.xposed.installer.util.ModuleUtil;

public class WelcomeActivity extends Activity {
	private ModuleUtil moduleUtil;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		moduleUtil = ModuleUtil.getInstance();

		setContentView(R.layout.activity_welcome);
		
		WelcomeAdapter items = new WelcomeAdapter(this);
		// TODO add proper description texts and load them from resources, add icons, make it more fancy, ... 
		items.add(new WelcomeItem(getString(R.string.tabInstall), "Here you can install the framework"));
		items.add(new WelcomeItem(getString(R.string.tabModules), "Activate modules here"));
		items.add(new WelcomeItem(getString(R.string.tabDownload), "Download new modules"));
		
		ListView lv = (ListView) findViewById(R.id.welcome_list);
		lv.setAdapter(items);
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
				frameworkUpdateAvailable = moduleUtil.hasFrameworkUpdate();
				moduleUpdateAvailable = moduleUtil.hasModuleUpdates();
		    }

		    view.findViewById(R.id.txtFrameworkUpdateAvailable).setVisibility(frameworkUpdateAvailable ? View.VISIBLE : View.GONE);
		    view.findViewById(R.id.txtUpdateAvailable).setVisibility(moduleUpdateAvailable ? View.VISIBLE : View.GONE);

		    return view;
		}
	}

	class WelcomeItem {
		public final String title;
		public final String description;
		
		protected WelcomeItem(String title, String description) {
	        this.title = title;
	        this.description = description;
        }
		
		@Override
		public String toString() {
		    return title;
		}
	}
}
