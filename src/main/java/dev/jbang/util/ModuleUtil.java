package dev.jbang.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import dev.jbang.catalog.CatalogUtil;
import dev.jbang.dependencies.ArtifactInfo;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.source.Project;
import dev.jbang.source.ResourceRef;

import io.quarkus.qute.Template;

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

	public static Path generateModuleInfo(Project project, Path targetDir) throws IOException {
		Template infoTemplate = TemplateEngine	.instance()
												.getTemplate(
														ResourceRef.forResource("classpath:/module-info.qute.java"));

		Path infoPath = null;
		if (infoTemplate == null) {
			// ignore
			Util.warnMsg("Could not locate module-info.java template");
		} else {
			// First get the list of root dependencies as proper maven coordinates
			Set<MavenCoordinate> deps = project	.getMainSourceSet()
												.getDependencies()
												.stream()
												.map(MavenCoordinate::fromString)
												.collect(Collectors.toSet());
			// Now filter out the resolved artifacts that are root dependencies
			// and get their names
			Stream<String> depModNames = project.resolveClassPath()
												.getArtifacts()
												.stream()
												.filter(a -> deps.contains(a.getCoordinate()))
												.map(ArtifactInfo::getModuleName)
												.filter(Objects::nonNull);
			// And join this list of names with the JDK module names
			List<String> moduleNames = Stream	.concat(ModuleUtil.listJdkModules().stream(), depModNames)
												.collect(Collectors.toList());
			// Finally create a module-info file with the name of the module
			// and the list of required modules using the names we just listed
			String modName = ModuleUtil.getModuleName(project);
			String infoFile = infoTemplate
											.data("moduleName", modName)
											.data("packageName", project.getMainSource().getJavaPackage().get())
											.data("dependencies", moduleNames)
											.render();

			infoPath = targetDir.resolve("module-info.java");
			Files.createDirectories(infoPath.getParent());
			Util.writeString(infoPath, infoFile);
		}

		return infoPath;
	}

	private static List<String> listJdkModules() {
		if (JavaUtil.getCurrentMajorJavaVersion() >= 9) {
			return ModuleUtil9.listJdkModules();
		} else {
			return null;
		}
	}
}
