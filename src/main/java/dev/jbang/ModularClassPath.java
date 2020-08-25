package dev.jbang;

import static dev.jbang.Settings.CP_SEPARATOR;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.codehaus.plexus.languages.java.jpms.*;

public class ModularClassPath {

	static final String JAVAFX_PREFIX = "javafx";

	private String classPath;
	private final List<ArtifactInfo> artifacts;
	private Optional<Boolean> javafx = Optional.empty();

	public ModularClassPath(List<ArtifactInfo> artifacts) {
		this.artifacts = artifacts;
	}

	public String getClassPath() {
		if (classPath == null) {
			classPath = artifacts	.stream()
									.map(it -> it.asFile().getAbsolutePath())
									.map(it -> it.contains(" ") ? '"' + it + '"' : it)
									.distinct()
									.collect(Collectors.joining(CP_SEPARATOR));
		}

		return classPath;
	}

	boolean hasJavaFX() {
		if (!javafx.isPresent()) {
			javafx = Optional.of(
					getClassPath().contains("org/openjfx/javafx-") || getClassPath().contains("org\\openjfx\\javafx-"));
		}
		return javafx.get().booleanValue();
	}

	List<String> getAutoDectectedModuleArguments(String requestedVersion) {
		if (hasJavaFX() && supportsModules(requestedVersion)) {
			List<String> commandArguments = new ArrayList<>();

			List<File> fileList = artifacts	.stream()
											.map(it -> it.asFile())
											.collect(Collectors.toList());

			ResolvePathsRequest<File> result = ResolvePathsRequest	.ofFiles(fileList)
																	.setModuleDescriptor(
																			JavaModuleDescriptor.newModule("bogus")
																								.build());

			LocationManager lm = new LocationManager();

			try {
				ResolvePathsResult<File> resolvePathsResult = lm.resolvePaths(result);

				Map<File, ModuleNameSource> modulepathElements = resolvePathsResult.getModulepathElements();
				List<String> modulePaths = new ArrayList<>();
				Map<String, JavaModuleDescriptor> pathElements = new HashMap<>();

				resolvePathsResult	.getModulepathElements()
									.keySet()
									.forEach(file -> modulePaths.add(file.getPath()));

				resolvePathsResult.getPathElements().forEach((key, value) -> pathElements.put(key.getPath(), value));

				pathElements.forEach((k, v) -> {
					if (v != null && v.name() != null && v.name().startsWith(JAVAFX_PREFIX)) {
						// only JavaFX jars are required in the module-path
						modulePaths.add(k);
					} else {
						// classpathElements.add(k);
					}
				});

				if (!modulePaths.isEmpty()) {
					commandArguments.add("--module-path");
					String modulePath = String.join(File.pathSeparator, modulePaths);
					commandArguments.add(modulePath);
				}

				String modules = pathElements	.values()
												.stream()
												.filter(Objects::nonNull)
												.map(JavaModuleDescriptor::name)
												.filter(Objects::nonNull)
												.filter(module -> module.startsWith(JAVAFX_PREFIX)
														&& !module.endsWith("Empty"))
												.collect(Collectors.joining(","));
				if (!modules.trim().isEmpty()) {
					commandArguments.add("--add-modules");
					commandArguments.add(modules);
				}

			} catch (IOException io) {
				Util.errorMsg("Error processing javafx modules", io);
				return Collections.emptyList();
			}
			return commandArguments;
		} else {
			return Collections.emptyList();
		}
	}

	protected boolean supportsModules(String requestedVersion) {
		return JavaUtil.javaVersion(requestedVersion) >= 9;
	}

	public List<ArtifactInfo> getArtifacts() {
		return artifacts;
	}
}
