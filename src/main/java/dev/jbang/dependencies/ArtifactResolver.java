package dev.jbang.dependencies;

import static dev.jbang.util.Util.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import org.jboss.shrinkwrap.resolver.api.Coordinate;
import org.jboss.shrinkwrap.resolver.api.ResolutionException;
import org.jboss.shrinkwrap.resolver.api.maven.*;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

import dev.jbang.Settings;
import dev.jbang.cli.ExitException;

import picocli.CommandLine;

public class ArtifactResolver {

	public static List<ArtifactInfo> resolve(List<String> depIds, List<MavenRepo> customRepos, boolean offline,
			boolean updateCache, boolean loggingEnabled, boolean transitively) {

		ConfigurableMavenResolverSystem resolver = Maven.configureResolver()
														.withMavenCentralRepo(false)
														.workOffline(offline);

		customRepos.forEach(mavenRepo -> mavenRepo.apply(resolver, updateCache));

		System.setProperty("maven.repo.local", Settings.getLocalMavenRepo().toAbsolutePath().toString());

		Map<Boolean, List<MavenCoordinate>> coordList = depIds	.stream()
																.map(DependencyUtil::depIdToArtifact)
																.collect(Collectors.partitioningBy(
																		c -> c.getType().equals(PackagingType.POM)));

		List<MavenCoordinate> coords = coordList.get(false);
		List<MavenCoordinate> pomcoords = coordList.get(true);

		PomEquippedResolveStage pomResolve = null;
		if (!pomcoords.isEmpty()) {

			String beforeDepMgmt = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
					"         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
					"         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
					+
					"    <modelVersion>4.0.0</modelVersion>\n" +
					"\n" +
					"    <groupId>dev.jbang.internal</groupId>\n" +
					"    <artifactId>dependency-pom</artifactId>\n" +
					"    <version>1.0-SNAPSHOT</version>\n" +
					"    <packaging>pom</packaging>\n" +
					"<dependencyManagement>\n" +
					"    <dependencies>\n";
			String afterDepMgmt = "</dependencies>\n" +
					"</dependencyManagement>\n" +
					"</project>";
			StringBuffer buf = new StringBuffer(beforeDepMgmt);
			for (MavenCoordinate pomcoord : pomcoords) {
				buf.append("<dependency>\n" +
						"      <groupId>" + pomcoord.getGroupId() + "</groupId>\n" +
						"      <artifactId>" + pomcoord.getArtifactId() + "</artifactId>\n" +
						"      <version>" + pomcoord.getVersion() + "</version>\n" +
						"             <type>pom</type>\n" +
						"      <scope>import</scope>\n" +
						"    </dependency>\n");
			}
			buf.append(afterDepMgmt);
			Path pompath = null;
			try {
				pompath = Files.createTempFile("jbang", ".xml");
				writeString(pompath, buf.toString());
			} catch (IOException e) {
				throw new ExitException(CommandLine.ExitCode.SOFTWARE,
						"Error trying to generate pom.xml for dependency management");
			}
			if (loggingEnabled) {
				infoMsg("Artifacts used for dependency management:");
				infoMsgFmt("         %s\n", String.join("\n         ",
						pomcoords.stream().map(Coordinate::toCanonicalForm).collect(Collectors.toList())));
			}
			pomResolve = resolver.loadPomFromFile(pompath.toFile());
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
			infoMsgFmt("%s\n", String.join("\n         ", canonicals));
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
							.map(mra -> new ArtifactInfo(mra.getCoordinate(), mra.asFile().toPath()))
							.collect(Collectors.toList());
		} catch (ResolutionException nrr) {
			Throwable cause = nrr.getCause();
			Set<Throwable> causes = new LinkedHashSet<Throwable>();
			StringBuffer buf = new StringBuffer();
			buf.append(nrr.getMessage());
			while (cause != null && !causes.contains(cause)) {
				causes.add(cause);
				buf.append("\n  " + cause.getMessage());
			}

			String repos = customRepos.stream().map(repo -> repo.toString()).collect(Collectors.joining(", "));

			throw new ExitException(1,
					String.format("Could not resolve dependencies from %s\n", repos) + buf.toString(), nrr);
		} catch (RuntimeException e) {
			throw new ExitException(1, "Unknown error occurred while trying to resolve dependencies", e);
		}
	}
}
