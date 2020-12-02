package dev.jbang;

import static dev.jbang.Settings.CP_SEPARATOR;
import static dev.jbang.Util.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jboss.shrinkwrap.resolver.api.maven.*;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinates;

public class DependencyUtil {

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

	public static final Pattern gavPattern = Pattern.compile(
			"^(?<groupid>[^:]*):(?<artifactid>[^:]*):(?<version>[^:@]*)(:(?<classifier>[^@]*))?(@(?<type>.*))?$");

	public ModularClassPath resolveDependencies(List<String> deps, List<MavenRepo> repos,
			boolean offline, boolean loggingEnabled) {
		return resolveDependencies(deps, repos, offline, loggingEnabled, true);
	}

	/**
	 *
	 * @param deps           dependencies; is modified with resolved deps (eg,
	 *                       jitpack)
	 * @param repos          repositories, is also modified with added repos (eg,
	 *                       also jitpack)
	 * @param offline
	 * @param loggingEnabled
	 * @param transitivity
	 * @return string with resolved classpath
	 */
	public ModularClassPath resolveDependencies(List<String> deps, List<MavenRepo> repos,
			boolean offline, boolean loggingEnabled, boolean transitivity) {

		// if no dependencies were provided we stop here
		if (deps.isEmpty()) {
			return new ModularClassPath(Collections.emptyList());
		}

		if (repos.isEmpty()) {
			repos.add(toMavenRepo("jcenter"));
		}

		// Turn any URL dependencies into regular GAV coordinates
		List<String> depIds = deps
									.stream()
									.map(JitPackUtil::ensureGAV)
									.collect(Collectors.toList());
		// And if we encountered URLs let's make sure the JitPack repo is available
		if (!depIds.equals(deps) && !repos.stream().anyMatch(r -> REPO_JITPACK.equals(r.getUrl()))) {
			repos.add(toMavenRepo(ALIAS_JITPACK));
		}

		// Put those resolved depIds back into deps (so the caller gets them)
		// TODO: refactor, extract class method with intermediary repos/deps
		deps.clear();
		deps.addAll(depIds);

		String depsHash = String.join(CP_SEPARATOR, depIds);

		List<ArtifactInfo> cachedDeps = null;

		if (transitivity) {
			cachedDeps = Settings.findDependenciesInCache(depsHash);
		}

		if (cachedDeps != null)
			return new ModularClassPath(cachedDeps);

		if (loggingEnabled) {
			infoMsg("Resolving dependencies...");
		}

		try {
			List<ArtifactInfo> artifacts = resolveDependenciesViaAether(depIds, repos, offline, loggingEnabled,
					transitivity);

			ModularClassPath classPath = new ModularClassPath(artifacts);

			if (loggingEnabled) {
				infoMsg("Dependencies resolved");
			}

			if (transitivity) { // only cache when doing transitive lookups
				Settings.cacheDependencies(depsHash, classPath.getArtifacts());
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

	public List<ArtifactInfo> resolveDependenciesViaAether(List<String> depIds, List<MavenRepo> customRepos,
			boolean offline, boolean loggingEnabled, boolean transitively) {

		ConfigurableMavenResolverSystem resolver = Maven.configureResolver()
														.withMavenCentralRepo(false)
														.workOffline(offline);

		customRepos.stream().forEach(mavenRepo -> {
			mavenRepo.apply(resolver);
		});

		System.setProperty("maven.repo.local", Settings.getLocalMavenRepo().toPath().toAbsolutePath().toString());

		return depIds.stream().flatMap(it -> {

			if (loggingEnabled) {
				infoHeader();
				infoMsgFmt("    Resolving %s...", it);
			}

			List<MavenResolvedArtifact> artifacts;
			try {
				MavenStrategyStage resolve = resolver.resolve(depIdToArtifact(it).toCanonicalForm());
				MavenFormatStage stage = null;

				if (transitively) {
					stage = resolve.withTransitivity();
				} else {
					stage = resolve.withoutTransitivity();
				}
				artifacts = stage.asList(MavenResolvedArtifact.class); // , RUNTIME);
			} catch (RuntimeException e) {
				throw new ExitException(1, "Could not resolve dependency", e);
			}

			if (loggingEnabled)
				infoMsgFmt("Done\n");

			return artifacts.stream().map(xx -> new ArtifactInfo(xx.getCoordinate(), xx.asFile()));
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

	public static boolean looksLikeAGav(String candidate) {
		Matcher gav = gavPattern.matcher(candidate);
		gav.find();
		return (gav.matches());
	}

	public MavenCoordinate depIdToArtifact(String depId) {

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
