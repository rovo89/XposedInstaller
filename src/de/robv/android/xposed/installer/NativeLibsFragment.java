package de.robv.android.xposed.installer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.xmlpull.v1.XmlPullParser;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.text.Html;
import android.util.Log;
import android.util.Xml;
import de.robv.android.xposed.library.ui.ListPreferenceFixedSummary;
import de.robv.android.xposed.library.ui.SeparatorPreference;
import de.robv.android.xposed.library.ui.TextViewPreference;

public class NativeLibsFragment extends PreferenceFragment {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Set<String> enabledModules = PackageChangeReceiver.getEnabledModules(getActivity());
		SortedMap<String, Set<LibReplacement>> map = new TreeMap<String, Set<LibReplacement>>();

		// read libraries
		PackageManager pm = getActivity().getPackageManager();
        for (String module : enabledModules) {
        	ApplicationInfo app;
        	JarFile jf = null;
        	InputStream is = null;
			try {
				app = pm.getApplicationInfo(module, 0);
				
				jf = new JarFile(app.sourceDir);
				JarEntry entry = jf.getJarEntry("assets/nativelibs.xml");
				if (entry == null)
					continue;
				is = jf.getInputStream(entry);
				XmlPullParser parser = Xml.newPullParser();
				parser.setInput(is, null);
				
				parser.nextTag();
				parser.require(XmlPullParser.START_TAG, null, "libs");
				while (parser.nextTag() == XmlPullParser.START_TAG) {
					parser.require(XmlPullParser.START_TAG, null, "lib");
					
					String libName = parser.getAttributeValue(null, "name");
					String asset = parser.getAttributeValue(null, "asset");
					String text = parser.nextText().trim();
					
					Set<LibReplacement> libs = map.get(libName);
					if (libs == null) {
						libs = new TreeSet<LibReplacement>();
						map.put(libName, libs);
					}
					libs.add(new LibReplacement(module, app.loadLabel(pm).toString(), asset, text));
				}
			} catch (Exception e) {
				Log.e("XposedInstaller", "", e);
				continue;
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException ignored) {}
					is = null;
				}
				if (jf != null) {
					try {
						jf.close();
					} catch (IOException ignored) {}
					jf = null;
				}
					
			}
        }
        
        // check if all selected replacements still exist
        SharedPreferences pref = getPreferenceManager().getSharedPreferences();
        SharedPreferences.Editor prefEditor = pref.edit();
        for (Map.Entry<String,?> existingSetting : pref.getAll().entrySet()) {
        	String key = existingSetting.getKey();
        	if (!key.startsWith("nativelib_"))
        		continue;

        	String selectedReplacement = String.valueOf(existingSetting.getValue());
        	String libName = key.split("_", 2)[1];
        	if (map.containsKey(libName)) {
        		boolean found = false;
        		for (LibReplacement replacement : map.get(libName)) {
        			if (selectedReplacement.equals(replacement.packageName + "!" + replacement.asset)) {
        				found = true;
        				break;
        			}
        		}
        		if (found)
        			continue;
        	}
        	prefEditor.remove("nativelib_" + libName);
        	prefEditor.remove("nativelibtest_" + libName);
        }
        prefEditor.commit();
		
		// create preference screen
		PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());
		this.setPreferenceScreen(screen);
		if (map.isEmpty()) {
			TextViewPreference nolibs = new TextViewPreference(getActivity());
			nolibs.setTitle("No enabled modules with native libraries");
			nolibs.getTextView().setTextSize(20);
			nolibs.setSelectable(false);
			screen.addPreference(nolibs);
		} else {
			for (Map.Entry<String, Set<LibReplacement>> entry : map.entrySet()) {
				String libName = entry.getKey();
				ListPreference p = new ListPreferenceFixedSummary(getActivity()) {
					@Override
					public boolean shouldDisableDependents() {
						String value = getValue();
						return value == null || value.equals("");
					}
					
					@Override
					public void setValue(String value) {
						boolean wasBlocking = shouldDisableDependents();
						
						super.setValue(value);
						
						boolean isBlocking = shouldDisableDependents();
						if (wasBlocking != isBlocking)
							notifyDependencyChange(isBlocking);
					}
				};
				p.setTitle(libName);
				int num = entry.getValue().size() + 1;
				CharSequence[] listEntries = new CharSequence[num];
				String[] listValues = new String[num];
				listEntries[0] = getActivity().getString(R.string.lib_default);
				listValues[0] = "";
				int i = 1;
				for (LibReplacement rep : entry.getValue()) {
					listEntries[i] = Html.fromHtml("<small><em>" + rep.appName + "</em></small><br>" + rep.text);
					listValues[i] = rep.packageName + "!" + rep.asset;
					i++;
				}
				p.setEntries(listEntries);
				p.setEntryValues(listValues);
				p.setKey("nativelib_" + libName);
				p.setDialogTitle(libName);
				p.setSummary("%s");
				p.setOnPreferenceChangeListener(updateLibsListener);
				p.setDefaultValue("");
				screen.addPreference(p);
				
				CheckBoxPreference cb = new CheckBoxPreference(getActivity());
				cb.setTitle(R.string.lib_replace_title);
				cb.setSummaryOn(R.string.lib_replace_testonly);
				cb.setSummaryOff(R.string.lib_replace_always);
				cb.setKey("nativelibtest_" + libName);
				cb.setOnPreferenceChangeListener(updateLibsListener);
				cb.setDefaultValue(false);
				screen.addPreference(cb);
				cb.setDependency("nativelib_" + libName);
				
				screen.addPreference(new SeparatorPreference(getActivity()));
			}
		}
	}
	
	private OnPreferenceChangeListener updateLibsListener = new OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			PackageChangeReceiver.updateNativeLibs(getActivity(), PackageChangeReceiver.getEnabledModules(getActivity()));
			return true;
		}
	};

    private static class LibReplacement implements Comparable<LibReplacement> {
    	final String packageName;
    	final String appName;
    	final String asset;
    	final String text;
		public LibReplacement(String packageName, String appName, String asset, String text) {
			this.packageName = packageName;
			this.appName = appName;
			this.asset = asset;
			this.text = text;
		}
		
		@Override
		public int compareTo(LibReplacement another) {
			int comp;
			comp = this.packageName.compareTo(another.packageName);
			if (comp != 0)
				return comp;
			
			comp = this.text.compareTo(another.text);
			if (comp != 0)
				return comp;
			
			comp = this.asset.compareTo(another.asset);
			if (comp != 0)
				return comp;
			return 0;
		}
    }
    
}
