package de.robv.android.xposed.installer;

import java.util.Comparator;
import java.util.Set;

import android.app.ListFragment;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TextView;

public class ModulesFragment extends ListFragment {
	private Set<String> enabledModules;
	private String installedXposedVersion;
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
        
		installedXposedVersion = InstallerFragment.getJarInstalledVersion(null);
		
        ModuleAdapter modules = new ModuleAdapter(getActivity());
        enabledModules = PackageChangeReceiver.getEnabledModules(getActivity());
        
		PackageManager pm = getActivity().getPackageManager();
		for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
			if (app.metaData == null || !app.metaData.containsKey("xposedmodule"))
				continue;
			
			String minVersion = app.metaData.getString("xposedminversion");
			modules.add(new XposedModule(app.packageName, pm.getApplicationLabel(app).toString(), pm.getApplicationIcon(app), minVersion));
		}
		
		modules.sort(new Comparator<XposedModule>() {
			@Override
			public int compare(XposedModule lhs, XposedModule rhs) {
				return lhs.appName.compareTo(rhs.appName);
			}
		});
        
        setListAdapter(modules);
        setEmptyText(getActivity().getString(R.string.no_xposed_modules_found));
	}
    
    private class ModuleAdapter extends ArrayAdapter<XposedModule> {
    	private final LayoutInflater mInflater;
    	
    	
		public ModuleAdapter(Context context) {
			super(context, android.R.layout.simple_list_item_1);
			mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			 	View view;
	            if (convertView == null) {
	                view = mInflater.inflate(R.layout.list_item_module, parent, false);
	            } else {
	                view = convertView;
	            }

	            final XposedModule item = getItem(position);
	            
	            ((ImageView)view.findViewById(R.id.icon)).setImageDrawable(item.icon);
	            ((TextView)view.findViewById(R.id.text)).setText(item.appName);
	            
	            CheckBox checkbox = (CheckBox)view.findViewById(R.id.checkbox);
	            checkbox.setChecked(enabledModules.contains(item.packageName));
	            TextView warningText = (TextView) view.findViewById(R.id.warning);

	            if (item.minVersion != null && installedXposedVersion != null
	            		&& PackageChangeReceiver.compareVersions(item.minVersion, installedXposedVersion) > 0) {
	            	checkbox.setEnabled(false);
	            	warningText.setText(String.format(getString(R.string.warning_xposed_min_version), 
	            			PackageChangeReceiver.trimVersion(item.minVersion)));
	            	warningText.setVisibility(View.VISIBLE);
	            } else {
	            	checkbox.setEnabled(true);
	            	warningText.setVisibility(View.GONE);
	            	checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	            		@Override
	            		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	            			if (isChecked)
	            				enabledModules.add(item.packageName);
	            			else
	            				enabledModules.remove(item.packageName);
	            			
	            			PackageChangeReceiver.setEnabledModules(getContext(), enabledModules);
	            			PackageChangeReceiver.updateModulesList(getContext(), enabledModules);
	            		}
	            	});
	            }
	            
	            

	            return view;
		}
    	
    }
    
    private static class XposedModule {
    	String packageName;
    	String appName;
    	Drawable icon;
    	String minVersion;
    	
    	public XposedModule(String packageName, String appName, Drawable icon, String minVersion) {
    		this.packageName = packageName;
    		this.appName = appName;
    		this.icon = icon;
    		this.minVersion = minVersion;
    	}
    }
}
