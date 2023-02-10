package dev.jbang.util;

import java.lang.module.FindException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.util.Set;

/**
 * WARNING: This file MUST be compiled with Java 9+
 */
public class ModuleUtil {
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
}
