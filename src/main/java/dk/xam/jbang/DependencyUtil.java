package dk.xam.jbang;

import com.jcabi.aether.Aether;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static dk.xam.jbang.Settings.CP_SEPARATOR;
import static dk.xam.jbang.Settings.DEP_LOOKUP_CACHE_FILE;
import static dk.xam.jbang.Util.*;
import static org.sonatype.aether.util.artifact.JavaScopes.RUNTIME;

class DependencyUtil {

	/**
	 * 
	 * @param depIds
	 * @param customRepos
	 * @param loggingEnabled
	 * @return string with resolved classpath
	 */
	public String resolveDependencies(List<String> depIds, List<MavenRepo> customRepos, boolean loggingEnabled) {

		// if no dependencies were provided we stop here
		if (depIds.isEmpty()) {
			return "";
		}

		String depsHash = String.join(CP_SEPARATOR, depIds);
		Map<String, String> cache = Collections.emptyMap();
		// Use cached classpath from previous run if present if
		// TODO: does not handle spaces in ~/.m2 folder.
		if (DEP_LOOKUP_CACHE_FILE.isFile()) {
			try {

				cache = Files.readAllLines(DEP_LOOKUP_CACHE_FILE.toPath()).stream()
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
			List<Artifact> artifacts = resolveDependenciesViaAether(depIds, customRepos, loggingEnabled);
			String classPath = artifacts.stream().map(it -> it.getFile().getAbsolutePath())
					.collect(Collectors.joining(CP_SEPARATOR));

			if (loggingEnabled) {
				infoMsg("Dependencies resolved");
			}

			// Add classpath to cache
			try {
				// Open given file in append mode.
				try(BufferedWriter out = new BufferedWriter(new FileWriter(DEP_LOOKUP_CACHE_FILE, true))) {
					out.write(depsHash + " " + classPath + "\n");
				};
			} catch (IOException e) {
				errorMsg("Could not write to cache:" + e.getMessage());
			}

			// Print the classpath
			return classPath;
		} catch (DependencyException e) { // Probably a wrapped Nullpointer from
											// 'DefaultRepositorySystem.resolveDependencies()', this however is probably
											// a connection problem.
			errorMsg(
					"Failed while connecting to the server. Check the connection (http/https, port, proxy, credentials, etc.) of your maven dependency locators. If you suspect this is a bug, you can create an issue on https://github.com/maxandersen/jbang");
			errorMsg("Exception: " + e.getMessage());
			quit(1);
		}

		return null;
	}

	public List<Artifact> resolveDependenciesViaAether(List<String> depIds, List<MavenRepo> customRepos,
			boolean loggingEnabled) {
		RemoteRepository jcenter = new RemoteRepository("jcenter", "default", "https://jcenter.bintray.com/");
		List<RemoteRepository> remoteRepos = customRepos.stream().map(mavenRepo -> {
			RemoteRepository rr = new RemoteRepository(mavenRepo.getId(), "default", mavenRepo.getUrl());
			if (mavenRepo.getUser() != null && mavenRepo.getPassword() != null) {
				rr.setAuthentication(
						new Authentication(decodeEnv(mavenRepo.getUser()), decodeEnv(mavenRepo.getPassword())));
			}
			return rr;
		}).collect(Collectors.toList());

		remoteRepos.add(jcenter);

		Aether aether = new Aether(remoteRepos, Settings.getLocalMavenRepo());
		return depIds.stream().flatMap(it -> {
			if (loggingEnabled)
				System.err.print(String.format("[jbang]     Resolving %s...", it));

			List<Artifact> artifacts;
			try {
				artifacts = aether.resolve(depIdToArtifact(it), RUNTIME);
			} catch (DependencyResolutionException e) {
				throw new DependencyException(e);
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

	public Artifact depIdToArtifact(String depId) {

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

		return new DefaultArtifact(groupId, artifactId, classifier, type, version);
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
		return Optional.ofNullable(s).filter(str -> str.length() != 0).map(str -> str.substring(0, str.length() - 1))
				.orElse(s);
	}

}