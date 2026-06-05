package dev.jbang.dependencies;

import static dev.jbang.Settings.CP_SEPARATOR;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.languages.java.jpms.LocationManager;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsRequest;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsResult;
import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.Jdk;
import dev.jbang.util.Util;

public class ModularClassPath {
	static final String JAVAFX_PREFIX = "javafx";

	private final List<ArtifactInfo> artifacts;

	private List<String> classPaths;
	private Optional<Boolean> javafx = Optional.empty();

	public ModularClassPath(List<ArtifactInfo> artifacts) {
		this.artifacts = artifacts;
	}

	public List<String> getClassPaths() {
		if (classPaths == null) {
			classPaths = artifacts
				.stream()
				.map(it -> it.getFile().toAbsolutePath().toString())
				.distinct()
				.collect(Collectors.toList());
		}
		return classPaths;
	}

	public String getClassPath() {
		return String.join(CP_SEPARATOR, getClassPaths());
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

	public List<String> getAutoDectectedModuleArguments(@NonNull Jdk jdk) {
		if (hasJavaFX() && supportsModules(jdk)) {
			List<String> commandArguments = new ArrayList<>();

			List<File> fileList = artifacts.stream()
				.map(ai -> ai.getFile().toFile())
				.collect(Collectors.toList());

			ResolvePathsRequest<File> result = ResolvePathsRequest.ofFiles(fileList)
				.setModuleDescriptor(
						JavaModuleDescriptor.newModule("bogus")
							.build());

			LocationManager lm = new LocationManager();

			try {
				ResolvePathsResult<File> resolvePathsResult = lm.resolvePaths(result);

				List<String> modulePaths = new ArrayList<>();
				Map<String, JavaModuleDescriptor> pathElements = new HashMap<>();

				resolvePathsResult.getModulepathElements()
					.keySet()
					.forEach(file -> modulePaths.add(file.getPath()));

				resolvePathsResult.getPathElements().forEach((key, value) -> pathElements.put(key.getPath(), value));

				// Index the resolved modules by name so we can follow `requires` edges.
				Map<String, String> moduleNameToPath = new HashMap<>();
				Map<String, JavaModuleDescriptor> moduleNameToDescriptor = new HashMap<>();
				pathElements.forEach((path, descriptor) -> {
					if (descriptor != null && descriptor.name() != null) {
						moduleNameToPath.put(descriptor.name(), path);
						moduleNameToDescriptor.put(descriptor.name(), descriptor);
					}
				});

				// Start from the JavaFX modules and transitively collect everything they
				// require. This way modules like `jdk.jsobject` (required by `javafx.web`)
				// also end up on the module-path instead of the class-path, where the boot
				// layer would not find them. See https://github.com/jbangdev/jbang/issues/559
				Set<String> requiredModules = new HashSet<>();
				Deque<String> toVisit = new ArrayDeque<>();
				moduleNameToDescriptor.keySet()
					.stream()
					.filter(name -> name.startsWith(JAVAFX_PREFIX))
					.forEach(toVisit::add);
				while (!toVisit.isEmpty()) {
					String name = toVisit.poll();
					if (!requiredModules.add(name)) {
						continue;
					}
					JavaModuleDescriptor descriptor = moduleNameToDescriptor.get(name);
					if (descriptor != null) {
						descriptor.requires()
							.stream()
							.map(JavaModuleDescriptor.JavaRequires::name)
							.filter(moduleNameToDescriptor::containsKey)
							.forEach(toVisit::add);
					}
				}

				// Put the JavaFX modules and their required modules on the module-path.
				requiredModules.stream()
					.map(moduleNameToPath::get)
					.filter(Objects::nonNull)
					.forEach(modulePaths::add);

				if (!modulePaths.isEmpty()) {
					commandArguments.add("--module-path");
					String modulePath = modulePaths.stream()
						.distinct()
						.collect(Collectors.joining(File.pathSeparator));
					commandArguments.add(modulePath);
				}

				String modules = pathElements.values()
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

	protected boolean supportsModules(Jdk jdk) {
		return jdk.majorVersion() >= 9;
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
