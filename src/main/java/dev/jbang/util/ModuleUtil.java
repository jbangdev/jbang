package dev.jbang.util;

import java.nio.file.Path;

import dev.jbang.source.Project;

/**
 * WARNING: This file MUST be compiled with Java 9+
 */
public class ModuleUtil {
	public static String getModuleName(Path file) {
		if (JavaUtil.getCurrentMajorJavaVersion() >= 9) {
			return ModuleUtil9.getModuleName(file);
		} else {
			return null;
		}
	}

	public static String getModuleName(Project project) {
		String modName = project.getModuleName().orElse(null);
		if (modName == null || modName.isEmpty()) {
			modName = project.getGav().orElse("jbangapp");
		}
		return modName;
	}
}
