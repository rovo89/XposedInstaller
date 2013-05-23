package de.robv.android.xposed.installer;

import java.io.InputStream;

import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import de.robv.android.xposed.installer.repo.RepoParser;
import de.robv.android.xposed.installer.repo.Repository;

public class DownloadFragment extends Fragment {
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		try {
			InputStream is = getResources().getAssets().open("repo.xml");
			RepoParser parser = new RepoParser(is);
			Repository repo = parser.parse();
			System.out.println(repo); // set breakpoint here
        } catch (Exception e) {
        	Log.e(RepoParser.TAG, "error while parsing the test repository", e);
        }
		
	}
}
