package de.robv.android.xposed.installer.repo;


public class ModuleVersion {
	public final Module module;
	public String name;
	public int code;
	public String downloadLink;
	public String md5sum;
	public String changelog;
	public boolean changelogIsHtml = false;
	
	/*package*/ ModuleVersion(Module module) {
		this.module = module;
	}
}
