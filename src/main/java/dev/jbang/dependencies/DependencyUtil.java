package dev.jbang.dependencies;

import static dev.jbang.Settings.CP_SEPARATOR;
import static dev.jbang.util.Util.errorMsg;
import static dev.jbang.util.Util.infoHeader;
import static dev.jbang.util.Util.infoMsg;
import static dev.jbang.util.Util.infoMsgFmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jboss.shrinkwrap.resolver.api.Coordinate;
import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenFormatStage;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.MavenStrategyStage;
import org.jboss.shrinkwrap.resolver.api.maven.PackagingType;
import org.jboss.shrinkwrap.resolver.api.maven.PomEquippedResolveStage;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinates;

import dev.jbang.Settings;
import dev.jbang.cli.ExitException;

public class DependencyUtil {

	public static final String ALIAS_JITPACK = "jitpack";
	public static final String REPO_JITPACK = "https://jitpack.io/";

	private static final Map<String, String> aliasToRepos;

	static {
		aliasToRepos = new HashMap<>();
		aliasToRepos.put("mavencentral", "https://repo1.maven.org/maven2/");
		aliasToRepos.put("jbossorg", "https://repository.jboss.org/nexus/content/groups/public/");
		aliasToRepos.put("redhat", "https://maven.repository.redhat.com/ga/");
		aliasToRepos.put("jcenter", "https://jcenter.bintray.com/");
		aliasToRepos.put("google", "https://maven.google.com/");
		aliasToRepos.put(ALIAS_JITPACK, REPO_JITPACK);
		aliasToRepos.put("sponge", "https://repo.spongepowered.org/maven");
	}

	public static final Pattern fullGavPattern = Pattern.compile(
			"^(?<groupid>[^:]*):(?<artifactid>[^:]*):(?<version>[^:@]*)(:(?<classifier>[^@]*))?(@(?<type>.*))?$");

	public static final Pattern gavPattern = Pattern.compile(
			"^(?<groupid>[^:]*):(?<artifactid>[^:]*)(:(?<version>[^:@]*))?(:(?<classifier>[^@]*))?(@(?<type>.*))?$");

	private DependencyUtil() {
	}

	public static ModularClassPath resolveDependencies(List<String> deps, List<MavenRepo> repos,
			boolean offline, boolean updateCache, boolean loggingEnabled) {
		return resolveDependencies(deps, repos, offline, updateCache, loggingEnabled, true);
	}

	/**
	 *
	 * @param deps
	 * @param repos
	 * @param loggingEnabled
	 * @return string with resolved classpath
	 */
	public static ModularClassPath resolveDependencies(List<String> deps, List<MavenRepo> repos,
			boolean offline, boolean updateCache, boolean loggingEnabled, boolean transitivity) {

		// if no dependencies were provided we stop here
		if (deps.isEmpty()) {
			return new ModularClassPath(Collections.emptyList());
		}

		if (repos.isEmpty()) {
			repos = new ArrayList<>();
			repos.add(toMavenRepo("mavencentral"));
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
		if (!updateCache) {
			cachedDeps = DependencyCache.findDependenciesByHash(depsHash);
			if (cachedDeps != null) {
				return new ModularClassPath(cachedDeps);
			}
		}

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

			DependencyCache.cache(depsHash, classPath.getArtifacts());

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

	public static List<ArtifactInfo> resolveDependenciesViaAether(List<String> depIds, List<MavenRepo> customRepos,
			boolean offline, boolean loggingEnabled, boolean transitively) {

		ConfigurableMavenResolverSystem resolver = Maven.configureResolver()
														.withMavenCentralRepo(false)
														.workOffline(offline);

		customRepos.stream().forEach(mavenRepo -> mavenRepo.apply(resolver));

		System.setProperty("maven.repo.local", Settings.getLocalMavenRepo().toPath().toAbsolutePath().toString());

		List<MavenCoordinate> coords = depIds	.stream()
												.map(DependencyUtil::depIdToArtifact)
												.collect(Collectors.toList());

		PomEquippedResolveStage pomResolve = null;
		if (!coords.isEmpty()) {
			MavenCoordinate mc = coords.get(0);
			if (PackagingType.POM.equals(mc.getType())) {
				if (loggingEnabled) {
					infoMsg("Loading " + mc);
				}
				System.setProperty("jbang-allowpom", "true"); // big hack to trick shrinkwrap in actually get pom
																// location
				MavenStrategyStage resolve = resolver.resolve(mc.toCanonicalForm());
				pomResolve = resolver.loadPomFromFile(resolve.withoutTransitivity().asSingleFile());
				System.getProperties().remove("jbang-allowpom");
				coords.remove(0);
			}
		}

		Optional<MavenCoordinate> pom = coords.stream().filter(c -> c.getType().equals(PackagingType.POM)).findFirst();
		if (pom.isPresent()) {
			// proactively avoiding that we break users in future
			// when we support more than one BOM POM
			throw new ExitException(1, "POM imports as found in " + pom.get().toCanonicalForm()
					+ " is only supported as the first import.");
		}

		List<String> canonicals = coords.stream().map(Coordinate::toCanonicalForm).collect(Collectors.toList());

		if (loggingEnabled) {
			infoHeader();
			infoMsgFmt("    Resolving %s...", String.join(", ", canonicals));
		}

		try {
			MavenStrategyStage resolve;
			if (pomResolve != null) {
				resolve = pomResolve.resolve(canonicals);
			} else {
				resolve = resolver.resolve(canonicals);
			}

			MavenFormatStage stage = transitively ? resolve.withTransitivity() : resolve.withoutTransitivity();
			List<MavenResolvedArtifact> artifacts = stage.asList(MavenResolvedArtifact.class); // , RUNTIME);

			if (loggingEnabled)
				infoMsgFmt("Done\n");

			return artifacts.stream()
							.map(mra -> new ArtifactInfo(mra.getCoordinate(), mra.asFile()))
							.collect(Collectors.toList());
		} catch (RuntimeException e) {
			throw new ExitException(1, "Could not resolve dependency", e);
		}
	}

	public static String decodeEnv(String value) {
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
		Matcher gav = fullGavPattern.matcher(candidate);
		gav.find();
		return (gav.matches());
	}

	public static MavenCoordinate depIdToArtifact(String depId) {

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

	public static String formatVersion(String version) {
		// replace + with open version range for maven
		if (version != null && version.endsWith("+")) {
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

	public static String gavWithVersion(String gav) {
		if (gav.replaceAll("[^:]", "").length() == 1) {
			gav += ":999-SNAPSHOT";
		}
		return gav;
	}

	public static MavenRepo toMavenRepo(String repoReference) {
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
		} else {
			return new MavenRepo(repoid, reporef);
		}
	}
}