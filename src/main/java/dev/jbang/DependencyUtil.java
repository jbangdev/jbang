package dev.jbang;

import static dev.jbang.Settings.CP_SEPARATOR;
import static dev.jbang.Util.errorMsg;
import static dev.jbang.Util.infoHeader;
import static dev.jbang.Util.infoMsg;
import static dev.jbang.Util.infoMsgFmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenFormatStage;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.MavenStrategyStage;
import org.jboss.shrinkwrap.resolver.api.maven.PackagingType;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinates;

public class DependencyUtil {

	public static final String ALIAS_JITPACK = "jitpack";
	public static final String REPO_JITPACK = "https://jitpack.io/";

	private static final Map<String, String> aliasToRepos;

	static {
		aliasToRepos = new HashMap<>();
		aliasToRepos.put("jbossorg", "https://repository.jboss.org/nexus/content/groups/public/");
		aliasToRepos.put("redhat", "https://maven.repository.redhat.com/ga/");
		aliasToRepos.put("jcenter", "https://jcenter.bintray.com/");
		aliasToRepos.put("google", "https://maven.google.com/");
		aliasToRepos.put(ALIAS_JITPACK, REPO_JITPACK);
		aliasToRepos.put("sponge", "https://repo.spongepowered.org/maven");
	}

	public static final Pattern gavPattern = Pattern.compile(
			"^(?<groupid>[^:]*):(?<artifactid>[^:]*):(?<version>[^:@]*)(:(?<classifier>[^@]*))?(@(?<type>.*))?$");

	public ModularClassPath resolveDependencies(List<String> deps, List<MavenRepo> repos,
			boolean offline, boolean loggingEnabled) {
		return resolveDependencies(deps, repos, offline, loggingEnabled, true);
	}

	/**
	 *
	 * @param deps
	 * @param repos
	 * @param loggingEnabled
	 * @return string with resolved classpath
	 */
	public ModularClassPath resolveDependencies(List<String> deps, List<MavenRepo> repos,
			boolean offline, boolean loggingEnabled, boolean transitivity) {

		// if no dependencies were provided we stop here
		if (deps.isEmpty()) {
			return new ModularClassPath(Collections.emptyList());
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
		if (!depIds.equals(deps) && !repos.stream().anyMatch(r -> REPO_JITPACK.equals(r.getUrl()))) {
			repos.add(toMavenRepo(ALIAS_JITPACK));
		}

		String depsHash = String.join(CP_SEPARATOR, depIds);
		if (!transitivity) { // the cached key need to be different for non-transivity
			depsHash = "notransitivity-" + depsHash;
		}

		List<ArtifactInfo> cachedDeps = null;

		cachedDeps = Settings.findDependenciesInCache(depsHash);

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

			Settings.cacheDependencies(depsHash, classPath.getArtifacts());

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

		String repo = aliasToRepos.get(reporef.toLowerCase());
		if (repo != null) {
			return new MavenRepo(Optional.ofNullable(repoid).orElse(reporef.toLowerCase()), repo);
		} else if ("mavenCentral".equalsIgnoreCase(reporef)) {
			return new MavenRepo("", "") {
				@Override
				public void apply(ConfigurableMavenResolverSystem resolver) {
					resolver.withMavenCentralRepo(true);
				}
			};
		} else {
			return new MavenRepo(repoid, reporef);
		}
	}
}