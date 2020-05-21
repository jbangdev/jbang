package dk.xam.jbang;

import static dk.xam.jbang.Settings.CP_SEPARATOR;
import static dk.xam.jbang.Util.errorMsg;
import static dk.xam.jbang.Util.infoMsg;
import static dk.xam.jbang.Util.warnMsg;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.PackagingType;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinates;

class DependencyUtil {

	public static final String ALIAS_JBOSS = "jbossorg";
	public static final String ALIAS_REDHAT = "redhat";

	public static final String ALIAS_JCENTER = "jcenter";
	public static final String ALIAS_GOOGLE = "google";
	public static final String ALIAS_MAVEN_CENTRAL = "mavenCentral";
	public static final String ALIAS_JITPACK = "jitpack";

	public static final String REPO_JCENTER = "https://jcenter.bintray.com/";
	public static final String REPO_GOOGLE = "https://maven.google.com/";
	public static final String REPO_JITPACK = "https://jitpack.io/";
	public static final String REPO_REDHAT = "https://maven.repository.redhat.com/ga/";
	public static final String REPO_JBOSS = "https://repository.jboss.org/nexus/content/groups/public/";

	/**
	 * 
	 * @param deps
	 * @param repos
	 * @param loggingEnabled
	 * @return string with resolved classpath
	 */
	public String resolveDependencies(List<String> deps, List<MavenRepo> repos,
			boolean offline, boolean loggingEnabled) {

		// if no dependencies were provided we stop here
		if (deps.isEmpty()) {
			return "";
		}

		if (repos.isEmpty()) {
			repos = new ArrayList<MavenRepo>();
			repos.add(toMavenRepo("jcenter"));
		}

		// Turn any URL dependencies into regular GAV coordinates
		List<String> depIds = deps
									.stream()
									.map(JitPackUtil::ensureGAV)
									.collect(Collectors.toList());
		// And if we encountered URLs let's make sure the JitPack repo is available
		if (!depIds.equals(deps) && !repos.contains(REPO_JITPACK)) {
			repos.add(toMavenRepo(ALIAS_JITPACK));
		}

		String depsHash = String.join(CP_SEPARATOR, depIds);
		Map<String, String> cache = Collections.emptyMap();
		// Use cached classpath from previous run if present if
		// TODO: does not handle spaces in ~/.m2 folder.
		if (Settings.getCacheDependencyFile().isFile()) {
			try {

				cache = Files	.readAllLines(Settings.getCacheDependencyFile().toPath())
								.stream()
								.filter(it -> !it.trim().isEmpty())
								.collect(Collectors.toMap(
										it -> it.split(" ")[0],
										it -> it.split(" ")[1],
										(k1, k2) -> {
											return k2;
										} // in case of duplicates, last one wins
								));
			} catch (IOException e) {
				warnMsg("Could not access cache " + e.getMessage());
			}

			if (cache.containsKey(depsHash)) {
				String cachedCP = cache.get(depsHash);

				// Make sure that local dependencies have not been wiped since resolving them
				// (like by deleting .m2)
				boolean allExists = Arrays.stream(cachedCP.split(CP_SEPARATOR)).allMatch(it -> new File(it).exists());
				if (allExists) {
					return cachedCP;
				} else {
					warnMsg("Detected missing dependencies in cache.");
				}
			}
		}

		if (loggingEnabled) {
			infoMsg("Resolving dependencies...");
		}

		try {
			List<File> artifacts = resolveDependenciesViaAether(depIds, repos, offline, loggingEnabled);
			String classPath = artifacts.stream()
										.map(it -> it.getAbsolutePath())
										.map(it -> it.contains(" ") ? '"' + it + '"' : it)
										.collect(Collectors.joining(CP_SEPARATOR));

			if (loggingEnabled) {
				infoMsg("Dependencies resolved");
			}

			// Add classpath to cache
			try {
				// Open given file in append mode.
				try (BufferedWriter out = new BufferedWriter(new FileWriter(Settings.getCacheDependencyFile(), true))) {
					out.write(depsHash + " " + classPath + "\n");
				}
			} catch (IOException e) {
				errorMsg("Could not write to cache:" + e.getMessage(), e);
			}

			// Print the classpath
			return classPath;
		} catch (DependencyException e) { // Probably a wrapped Nullpointer from
											// 'DefaultRepositorySystem.resolveDependencies()', this however is probably
											// a connection problem.
			errorMsg("Exception: " + e.getMessage());
			throw new ExitException(0,
					"Failed while connecting to the server. Check the connection (http/https, port, proxy, credentials, etc.) of your maven dependency locators.",
					e);
		}
	}

	public List<File> resolveDependenciesViaAether(List<String> depIds, List<MavenRepo> customRepos,
			boolean offline, boolean loggingEnabled) {

		ConfigurableMavenResolverSystem resolver = Maven.configureResolver()
														.withMavenCentralRepo(false)
														.workOffline(offline);

		customRepos.stream().forEach(mavenRepo -> {
			mavenRepo.apply(resolver);
		});

		System.setProperty("maven.repo.local", Settings.getLocalMavenRepo().toPath().toAbsolutePath().toString());

		return depIds.stream().flatMap(it -> {

			if (loggingEnabled)
				System.err.print(String.format("[jbang]     Resolving %s...", it));

			List<File> artifacts;
			try {
				artifacts = resolver.resolve(depIdToArtifact(it).toCanonicalForm())
									.withTransitivity()
									.asList(File.class); // , RUNTIME);
			} catch (RuntimeException e) {
				throw new ExitException(1, "Could not resolve dependency", e);
			}

			if (loggingEnabled)
				System.err.println("Done");

			return artifacts.stream();
		}).collect(Collectors.toList());
	}

	public String decodeEnv(String value) {
		if (value.startsWith("{{") && value.endsWith("}}")) {
			String envKey = value.substring(2, value.length() - 2);
			String envValue = System.getenv(envKey);
			if (null == envValue) {
				throw new IllegalStateException(String.format(
						"Could not resolve environment variable {{%s}} in maven repository credentials", envKey));
			}
			return envValue;
		} else {
			return value;
		}
	}

	public MavenCoordinate depIdToArtifact(String depId) {

		Pattern gavPattern = Pattern.compile(
				"^(?<groupid>[^:]*):(?<artifactid>[^:]*):(?<version>[^:@]*)(:(?<classifier>[^@]*))?(@(?<type>.*))?$");
		Matcher gav = gavPattern.matcher(depId);
		gav.find();

		if (!gav.matches()) {
			throw new IllegalStateException(String.format(
					"[ERROR] Invalid dependency locator: '%s'.  Expected format is groupId:artifactId:version[:classifier][@type]",
					depId));
		}

		String groupId = gav.group("groupid");
		String artifactId = gav.group("artifactid");
		String version = formatVersion(gav.group("version"));
		String classifier = gav.group("classifier");
		String type = Optional.ofNullable(gav.group("type")).orElse("jar");

		// String groupId, String artifactId, String classifier, String extension,
		// String version
		// String groupId, String artifactId, String version, String scope, String type,
		// String classifier, ArtifactHandler artifactHandler

		// shrinkwrap format: groupId:artifactId:[packagingType:[classifier]]:version

		return MavenCoordinates.createCoordinate(groupId, artifactId, version, PackagingType.of(type), classifier);
	}

	public String formatVersion(String version) {
		// replace + with open version range for maven
		if (version.endsWith("+")) {
			return "[" + removeLastCharOptional(version) + ",)";
		} else {
			return version;
		}
	}

	public static String removeLastCharOptional(String s) {
		return Optional	.ofNullable(s)
						.filter(str -> str.length() != 0)
						.map(str -> str.substring(0, str.length() - 1))
						.orElse(s);
	}

	static public MavenRepo toMavenRepo(String repoReference) {
		String[] split = repoReference.split("=");
		String reporef = null;
		String repoid = null;

		if (split.length == 1) {
			reporef = split[0];
		} else if (split.length == 2) {
			repoid = split[0];
			reporef = split[1];
		} else {
			throw new IllegalStateException("Invalid Maven repository reference: " + repoReference);
		}

		if (ALIAS_JCENTER.equalsIgnoreCase(reporef)) {
			return new MavenRepo(Optional.ofNullable(repoid).orElse(ALIAS_JCENTER), REPO_JCENTER);
		} else if (ALIAS_GOOGLE.equalsIgnoreCase(reporef)) {
			return new MavenRepo(Optional.ofNullable(repoid).orElse(ALIAS_GOOGLE), REPO_GOOGLE);
		} else if (ALIAS_REDHAT.equalsIgnoreCase(reporef)) {
			return new MavenRepo(Optional.ofNullable(repoid).orElse(ALIAS_REDHAT), REPO_REDHAT);
		} else if (ALIAS_JBOSS.equalsIgnoreCase(reporef)) {
			return new MavenRepo(Optional.ofNullable(repoid).orElse(ALIAS_JBOSS), REPO_JBOSS);
		} else if (ALIAS_MAVEN_CENTRAL.equalsIgnoreCase(reporef)) {
			return new MavenRepo("", "") {
				@Override
				public void apply(ConfigurableMavenResolverSystem resolver) {
					resolver.withMavenCentralRepo(true);
				}
			};
		} else if (ALIAS_JITPACK.equalsIgnoreCase(reporef)) {
			return new MavenRepo(Optional.ofNullable(repoid).orElse(ALIAS_JITPACK), REPO_JITPACK);
		} else {
			return new MavenRepo(repoid, reporef);
		}
	}
}