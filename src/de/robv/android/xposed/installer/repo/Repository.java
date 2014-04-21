package de.robv.android.xposed.installer.repo;

import java.util.HashMap;
import java.util.Map;

public class Repository {
	public String name;
	public final Map<String, Module> modules = new HashMap<String, Module>();

	/*package*/ Repository() {};
}
