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
	// Required by `javafx.web`; removed from the JDK in 26 and now shipped as
	// `org.openjfx:jdk-jsobject`.
	static final String JDK_JSOBJECT_MODULE = "jdk.jsobject";

	// Proof-of-concept flag, enabled with `-Djbang.hybrid.module.resolve=true`.
	// When set, jbang stops special-casing JavaFX and instead promotes every
	// resolved
	// dependency that is a real (non-automatic) module onto the `--module-path`,
	// leaving plain jars on the class-path. See
	// https://github.com/jbangdev/jbang/pull/2511
	static final String HYBRID_MODULE_RESOLVE_PROPERTY = "jbang.hybrid.module.resolve";

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
		if (Boolean.getBoolean(HYBRID_MODULE_RESOLVE_PROPERTY) && supportsModules(jdk)) {
			return getHybridModuleArguments();
		}
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

				pathElements.forEach((k, v) -> {
					if (v != null && v.name() != null
							&& (v.name().startsWith(JAVAFX_PREFIX) || v.name().equals(JDK_JSOBJECT_MODULE))) {
						// JavaFX jars belong on the module-path. So does `jdk.jsobject`, which is
						// required by `javafx.web`: until JDK 25 it was part of the JDK, but JDK 26
						// removed it and openjfx now ships it as the separate
						// `org.openjfx:jdk-jsobject`
						// artifact. Left on the class-path the boot layer cannot find it and fails with
						// `Module jdk.jsobject not found, required by javafx.web`.
						// See https://github.com/jbangdev/jbang/issues/559
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

	/**
	 * Proof-of-concept "hybrid" module resolution (see
	 * https://github.com/jbangdev/jbang/pull/2511), enabled with
	 * {@code -Djbang.hybrid.module.resolve=true}.
	 * <p>
	 * Instead of special-casing JavaFX, this treats the script as a class-path
	 * application but moves every resolved dependency that is a real
	 * (non-automatic) module onto the {@code --module-path}, plus every module they
	 * {@code requires} transitively, and adds those modules as roots.
	 * <p>
	 * Automatic and plain jars that nobody requires are left on the class-path:
	 * their name is derived from the file name and may not be a valid Java
	 * identifier (e.g. {@code fastparse_2.13-2.3.3.jar} -> {@code fastparse.2.13}),
	 * which would abort boot-layer initialization if forced onto the module-path.
	 * An automatic module that <em>is</em> required by a promoted module is
	 * promoted too, since a valid {@code requires} clause guarantees it has a
	 * usable name.
	 */
	private List<String> getHybridModuleArguments() {
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
