package dev.jbang.dependencies;

import static dev.jbang.Settings.CP_SEPARATOR;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.languages.java.jpms.LocationManager;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsRequest;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsResult;

import dev.jbang.net.JdkProvider;
import dev.jbang.util.Util;

public class ModularClassPath {
	static final String JAVAFX_PREFIX = "javafx";

	private final List<ArtifactInfo> artifacts;

	private List<String> classPaths;
	private List<String> modulePaths;
	private Optional<Boolean> javafx = Optional.empty();

	public ModularClassPath(List<ArtifactInfo> artifacts) {
		this.artifacts = artifacts;
	}

	public List<String> getClassPaths() {
		if (classPaths == null) {
			classPaths = getArtifactPaths(artifacts.stream());
		}
		return classPaths;
	}

	public String getClassPath() {
		return String.join(CP_SEPARATOR, getClassPaths());
	}

	public List<String> getModulePaths() {
		if (modulePaths == null) {
			modulePaths = getArtifactPaths(artifacts.stream().filter(ArtifactInfo::isModule));
		}

		return modulePaths;
	}

	private List<String> getArtifactPaths(Stream<ArtifactInfo> artifacts) {
		return artifacts
						.map(it -> it.getFile().toAbsolutePath().toString())
						.map(it -> it.contains(" ") ? '"' + it + '"' : it)
						.distinct()
						.collect(Collectors.toList());
	}

	public String getModulePath() {
		return String.join(CP_SEPARATOR, getModulePaths());
	}

	public String getManifestPath() {
		return artifacts.stream()
						.map(it -> it.getFile().toAbsolutePath().toUri())
						.map(URI::getPath)
						.distinct()
						.collect(Collectors.joining(" "));
	}

	boolean hasJavaFX() {
		if (!javafx.isPresent()) {
			javafx = Optional.of(
					getClassPath().contains("org/openjfx/javafx-") || getClassPath().contains("org\\openjfx\\javafx-"));
		}
		return javafx.get();
	}

	public List<String> getAutoDectectedModuleArguments(@Nonnull JdkProvider.Jdk jdk) {
		if (hasJavaFX() && supportsModules(jdk)) {
			List<String> commandArguments = new ArrayList<>();

			List<File> fileList = artifacts	.stream()
											.map(ai -> ai.getFile().toFile())
											.collect(Collectors.toList());

			ResolvePathsRequest<File> result = ResolvePathsRequest	.ofFiles(fileList)
																	.setModuleDescriptor(
																			JavaModuleDescriptor.newModule("bogus")
																								.build());

			LocationManager lm = new LocationManager();

			try {
				ResolvePathsResult<File> resolvePathsResult = lm.resolvePaths(result);

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
				if (!Util.isBlankString(modules)) {
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

	protected boolean supportsModules(JdkProvider.Jdk jdk) {
		return jdk.getMajorVersion() >= 9;
	}

	public List<ArtifactInfo> getArtifacts() {
		return artifacts;
	}

	/**
	 * Determines if all artifacts actually exist and are up-to-date
	 */
	public boolean isValid() {
		return artifacts.stream().allMatch(ArtifactInfo::isUpToDate);
	}

	@Override
	public String toString() {
		return artifacts.stream().map(ArtifactInfo::toString).collect(Collectors.joining(", "));
	}
}
