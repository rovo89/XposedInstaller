package de.robv.android.xposed.installer;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import android.animation.Animator;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.RepoParser;
import de.robv.android.xposed.installer.repo.Repository;
import de.robv.android.xposed.installer.util.AnimatorUtil;

public class DownloadFragment extends Fragment {

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
		final DownloadsAdapter adapter = new DownloadsAdapter(getActivity());
		try {
			InputStream is = getResources().getAssets().open("repo.xml");
			RepoParser parser = new RepoParser(is);
			Repository repo = parser.parse();

			HashMap<String, ModuleRef> refs = new HashMap<String, ModuleRef>();
			for (Module mod : repo.modules.values()) {
				ModuleRef existing = refs.get(mod.packageName);
				if (existing != null)
					existing.addModule(mod);
				else
					refs.put(mod.packageName, new ModuleRef(mod));
			}
			adapter.addAll(refs.values());
			adapter.sort(null);
		} catch (Exception e) {
			Log.e(RepoParser.TAG, "error while parsing the test repository", e);
		}
		lv.setAdapter(adapter);
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				ModuleRef ref = adapter.getItem(position);
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

	private class DownloadsAdapter extends ArrayAdapter<ModuleRef> {
		public DownloadsAdapter(Context context) {
			super(context, R.layout.list_item_module, R.id.text);
		}
	}

	private class ModuleRef implements Comparable<ModuleRef> {
		public final String packageName;
		private final List<Module> modules = new ArrayList<Module>(1);

		public ModuleRef(Module module) {
			packageName = module.packageName;
			modules.add(module);
		}

		public void addModule(Module module) {
			if (!packageName.equals(module.packageName)) {
				throw new IllegalArgumentException("Cannot add module with package "
						+ module.packageName + ", existing package is " + packageName);
			}

			modules.add(module);
			// TODO: add logic to sort modules by preferred repository
		}

		/** Returns the module from the preferred repository */
		public Module getModule() {
			return modules.get(0);
		}

		public List<Module> getAllModules() {
			return Collections.unmodifiableList(modules);
		}

		@Override
		public String toString() {
			return modules.get(0).name;
		}

		@Override
		public int compareTo(ModuleRef another) {
			Module thisModule = modules.get(0);
			Module otherModule = another.modules.get(0);

			int order = thisModule.name.compareTo(otherModule.name);
			if (order != 0)
				return order;

			order = packageName.compareTo(another.packageName);
			return order;
		}
	}

	@Override
	public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
		return AnimatorUtil.createSlideAnimation(this, nextAnim);
	}
}
