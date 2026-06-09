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

import org.jspecify.annotations.Nullable;

import dev.jbang.catalog.CatalogUtil;
import dev.jbang.dependencies.ArtifactInfo;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.BuildContext;
import dev.jbang.source.Project;

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

	public static Path generateModuleInfo(BuildContext ctx) throws IOException {
		Project project = ctx.getProject();
		Path targetDir = ctx.getGeneratedSourcesDir();
		Template infoTemplate = TemplateEngine.instance()
			.getTemplate(
					ResourceRef.forResource("classpath:/module-info.qute.java"));

		Path infoPath = null;
		if (infoTemplate == null) {
			// ignore
			Util.warnMsg("Could not locate module-info.java template");
		} else {
			// First get the keys of the root dependencies, ignoring any classifier. A
			// dependency can resolve to a platform-specific artifact that carries the real
			// module while the unclassified artifact is only an empty placeholder: JavaFX
			// is
			// the prime example, where `org.openjfx:javafx-base` resolves to both an empty
			// `javafx-base.jar` (module `javafx.baseEmpty`) and the platform
			// `javafx-base-<os>.jar` (the real `javafx.base` module that holds the
			// classes).
			Set<String> depKeys = project.getMainSourceSet()
				.getDependencies()
				.stream()
				.map(MavenCoordinate::fromString)
				.map(c -> c.getGroupId() + ":" + c.getArtifactId() + ":" + c.getType())
				.collect(Collectors.toSet());
			// Now filter the resolved artifacts down to those root dependencies and get
			// their module names, skipping the empty `*Empty` placeholder modules so we
			// require the real module (e.g. `javafx.base`, not `javafx.baseEmpty`).
			Stream<String> depModNames = ctx.resolveClassPath()
				.getArtifacts()
				.stream()
				.filter(a -> depKeys.contains(a.getCoordinate().getGroupId() + ":"
						+ a.getCoordinate().getArtifactId() + ":" + a.getCoordinate().getType()))
				.map(ArtifactInfo::getModuleName)
				.filter(Objects::nonNull)
				.filter(name -> !name.endsWith("Empty"));
			// And join this list of names with the JDK module names
			List<String> moduleNames = Stream.concat(ModuleUtil.listJdkModules().stream(), depModNames)
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
