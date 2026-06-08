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

	/**
	 * Builds the {@code --module-path}/{@code --add-modules} arguments for a JavaFX
	 * application, using a "hybrid" resolution (see
	 * https://github.com/jbangdev/jbang/pull/2511).
	 * <p>
	 * This only kicks in when JavaFX is detected on the class-path. A plain
	 * class-path application (e.g. Quarkus) must <em>stay</em> on the class-path:
	 * promoting its jars to the module-path turns them into named modules, which
	 * lose the relaxed reflective access of the unnamed module that many frameworks
	 * rely on (e.g. {@code jboss-logging} building a logger proxy for a type that
	 * is still on the class-path). {@code //MODULE} scripts are not handled here
	 * either - the command generators put their whole class-path on the module-path
	 * via {@code -p} and launch with {@code -m}.
	 * <p>
	 * Rather than special-casing JavaFX module names, the resolution moves every
	 * resolved dependency that is a real (non-automatic) module onto the
	 * {@code --module-path}, plus every module they {@code requires} transitively,
	 * and adds those modules as roots.
	 * <p>
	 * Automatic and plain jars that nobody requires are left on the class-path:
	 * their name is derived from the file name and may not be a valid Java
	 * identifier (e.g. {@code fastparse_2.13-2.3.3.jar} -> {@code fastparse.2.13}),
	 * which would abort boot-layer initialization if forced onto the module-path.
	 * An automatic module that <em>is</em> required by a promoted module is
	 * promoted too, since a valid {@code requires} clause guarantees it has a
	 * usable name (this is how {@code jdk.jsobject}, required by {@code javafx.web}
	 * and shipped separately since JDK 26, lands on the module-path).
	 */
	public List<String> getAutoDectectedModuleArguments(@NonNull Jdk jdk) {
		if (!hasJavaFX() || !supportsModules(jdk)) {
			return Collections.emptyList();
		}
		List<File> fileList = artifacts.stream()
			.map(ai -> ai.getFile().toFile())
			.collect(Collectors.toList());

		ResolvePathsRequest<File> request = ResolvePathsRequest.ofFiles(fileList)
			.setModuleDescriptor(
					JavaModuleDescriptor.newModule("bogus")
						.build());

		try {
			ResolvePathsResult<File> resolvePathsResult = new LocationManager().resolvePaths(request);

			List<String> modulePaths = new ArrayList<>();
			List<String> rootModules = new ArrayList<>();

			// Anything plexus already classified as a module-path element.
			resolvePathsResult.getModulepathElements()
				.keySet()
				.forEach(file -> modulePaths.add(file.getPath()));

			// Index every resolved module (real and automatic) by name so we can follow
			// `requires` edges below.
			Map<String, String> moduleNameToPath = new HashMap<>();
			Map<String, JavaModuleDescriptor> moduleNameToDescriptor = new HashMap<>();
			resolvePathsResult.getPathElements().forEach((file, descriptor) -> {
				if (descriptor != null && descriptor.name() != null) {
					moduleNameToPath.put(descriptor.name(), file.getPath());
					moduleNameToDescriptor.put(descriptor.name(), descriptor);
				}
			});

			// Seed with the real (non-automatic) modules, then transitively pull in every
			// module they `requires`. This also promotes an *automatic* module when it is
			// required by a promoted module: such a module necessarily has a valid name
			// (otherwise it could not appear in a `requires` clause), so it is safe on the
			// module-path. Automatic and plain jars that nobody requires stay on the
			// class-path, where their file-name-derived (possibly invalid) module name does
			// no harm.
			Set<String> promoted = new HashSet<>();
			Deque<String> toVisit = moduleNameToDescriptor.entrySet()
				.stream()
				.filter(e -> !e.getValue().isAutomatic())
				.map(Map.Entry::getKey)
				.collect(Collectors.toCollection(ArrayDeque::new));
			while (!toVisit.isEmpty()) {
				String name = toVisit.poll();
				if (!promoted.add(name)) {
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

			promoted.forEach(name -> {
				modulePaths.add(moduleNameToPath.get(name));
				rootModules.add(name);
			});

			if (modulePaths.isEmpty()) {
				return Collections.emptyList();
			}

			List<String> commandArguments = new ArrayList<>();
			commandArguments.add("--module-path");
			commandArguments.add(modulePaths.stream()
				.distinct()
				.collect(Collectors.joining(File.pathSeparator)));
			if (!rootModules.isEmpty()) {
				commandArguments.add("--add-modules");
				commandArguments.add(rootModules.stream().distinct().collect(Collectors.joining(",")));
			}
			return commandArguments;
		} catch (IOException io) {
			Util.errorMsg("Error resolving hybrid module-path", io);
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
