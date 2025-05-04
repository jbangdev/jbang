package dev.jbang.util;

import java.lang.module.FindException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WARNING: This file MUST be compiled with Java 9+
 */
public class ModuleUtil9 {
	public static String getModuleName(Path file) {
		try {
			Set<ModuleReference> refs = ModuleFinder.of(file).findAll();
			if (!refs.isEmpty()) {
				return refs.iterator().next().descriptor().name();
			}
		} catch (FindException ex) {
			// Ignore
		}
		return null;
	}

	public static List<String> listJdkModules() {
		ModuleLayer ml = ModuleLayer.boot();
		return ml.modules()
			.stream()
			.filter(m -> m.isNamed() && m.getAnnotation(Deprecated.class) == null
					&& (m.getName().startsWith("java.") || m.getName().startsWith("jdk."))
					&& (!m.getName().equals("jdk.naming.ldap")))
			.map(m -> m.getName())
			.collect(Collectors.toList());
	}
}
