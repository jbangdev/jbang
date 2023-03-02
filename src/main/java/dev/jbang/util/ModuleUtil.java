package dev.jbang.util;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;

import javax.annotation.Nullable;

import dev.jbang.catalog.CatalogUtil;
import dev.jbang.source.Project;

/**
 * WARNING: This file MUST be compiled with Java 9+
 */
public class ModuleUtil {
	public static boolean isModule(Path file) {
		try {
			URL url = new URL("jar:" + file.toUri().toURL() + "!/module-info.class");
			try (InputStream s = url.openStream()) {
				return true;
			}
		} catch (Exception ex) {
		}
		try {
			// TODO This is a very specific test, we should do better
			URL url = new URL("jar:" + file.toUri().toURL() + "!/META-INF/versions/9/module-info.class");
			try (InputStream s = url.openStream()) {
				return true;
			}
		} catch (Exception ex) {
			return false;
		}
	}

	public static String getModuleName(Path file) {
		if (JavaUtil.getCurrentMajorJavaVersion() >= 9) {
			return ModuleUtil9.getModuleName(file);
		} else {
			return null;
		}
	}

	@Nullable
	public static String getModuleName(Project project) {
		String modName = project.getModuleName().orElse(null);
		if (modName != null && modName.isEmpty()) {
			modName = project.getGav().orElse(CatalogUtil.nameFromRef(project.getResourceRef().getOriginalResource()));
		}
		return modName;
	}

	@Nullable
	public static String getModuleMain(Project project) {
		if (project.getModuleName().isPresent() && project.getMainClass() != null) {
			return getModuleName(project) + "/" + project.getMainClass();
		} else {
			return null;
		}
	}
}
