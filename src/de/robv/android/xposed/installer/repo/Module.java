package de.robv.android.xposed.installer.repo;

import java.util.ArrayList;
import java.util.List;

public class Module {
	public String packageName;
	public String name;
	public String description;
	public String author;
	public String contact;
	public ModuleVersion latestVersion;
	public final List<ModuleVersion> versions = new ArrayList<ModuleVersion>();
	public final List<String> screenshots = new ArrayList<String>();
	
	/*package*/ Module() {}
}
