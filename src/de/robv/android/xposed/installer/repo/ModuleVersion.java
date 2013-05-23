package de.robv.android.xposed.installer.repo;

import java.util.ArrayList;
import java.util.List;

public class ModuleVersion {
	public String name;
	public int code;
	public String downloadLink;
	public String md5sum;
	public final List<String> changelog = new ArrayList<String>();
	
	/*package*/ ModuleVersion() {}
}
