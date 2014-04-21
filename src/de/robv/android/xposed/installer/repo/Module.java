package de.robv.android.xposed.installer.repo;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.util.Pair;

public class Module {
	public final Repository repository;
	public String packageName;
	public String name;
	public String summary;
	public String description;
	public boolean descriptionIsHtml = false;
	public String author;
	public String support;
	public final List<Pair<String, String>> moreInfo = new LinkedList<Pair<String,String>>();
	public final List<ModuleVersion> versions = new ArrayList<ModuleVersion>();
	public final List<String> screenshots = new ArrayList<String>();
	public long created = -1;
	public long updated = -1;

	/*package*/ Module(Repository repository) {
		this.repository = repository;
	}
}
