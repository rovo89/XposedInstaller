package de.robv.android.xposed.installer.repo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleGroup implements Comparable<ModuleGroup> {
	public final String packageName;
	private final List<Module> modules = new ArrayList<Module>(1);

	public ModuleGroup(Module module) {
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
	public int compareTo(ModuleGroup another) {
		Module thisModule = modules.get(0);
		Module otherModule = another.modules.get(0);

		int order = thisModule.name.compareTo(otherModule.name);
		if (order != 0)
			return order;

		order = packageName.compareTo(another.packageName);
		return order;
	}
}
