package de.robv.android.xposed.installer.repo;

import java.util.ArrayList;
import java.util.List;

public class Module {
	public final Repository repository;
	public String packageName;
	public String name;
	public String summary;
	public String description;
	public boolean descriptionIsHtml = false;
	public String author;
	public String contact;
	public final List<ModuleVersion> versions = new ArrayList<ModuleVersion>();
	public final List<String> screenshots = new ArrayList<String>();
	public long created;
	public long updated;
	
	/*package*/ Module(Repository repository) {
		this.repository = repository;
	}
}
