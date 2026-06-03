package dev.jbang.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import dev.jbang.dependencies.ArtifactResolver;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.docs.DocsRenderer;
import dev.jbang.docs.DocsType;
import dev.jbang.docs.JavaSourceParser;
import dev.jbang.docs.JavadocHtmlParser;
import dev.jbang.docs.JavapExtractor;
import dev.jbang.util.Util;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "docs", description = "Display documentation from Java sources, JARs, or Maven artifacts.")
public class DocsCommand extends BaseCommand {

	@CommandLine.Mixin
	ScriptMixin scriptMixin;

	@CommandLine.Mixin
	DependencyInfoMixin dependencyInfoMixin;

	@CommandLine.Option(names = { "--json" }, description = "Print JSON instead of Markdown.")
	boolean json;

	@CommandLine.Option(names = { "--type",
			"--types" }, description = "Filter output to matching type names (simple or fully-qualified). Repeatable.")
	List<String> types;

	@Override
	public Integer doCall() throws IOException {
		scriptMixin.validate();
		String target = scriptMixin.scriptOrFile;

		Path targetPath = Paths.get(target);
		if (Files.exists(targetPath)) {
			return handleLocalTarget(targetPath);
		} else if (looksLikeCoordinate(target)) {
			return handleRemoteTarget(target);
		} else {
			throw new ExitException(EXIT_INVALID_INPUT, "docs target not found: " + target);
		}
	}

	private Integer handleLocalTarget(Path path) throws IOException {
		if (Files.isDirectory(path)) {
			return handleDirectory(path);
		}
		String name = path.getFileName().toString();
		if (name.endsWith(".java")) {
			return handleJavaSource(path);
		} else if (name.endsWith(".jar")) {
			return handleJar(path);
		} else if (name.endsWith(".jsh") || name.endsWith(".groovy") || name.endsWith(".kt")
				|| name.endsWith(".md")) {
			throw new ExitException(EXIT_INVALID_INPUT,
					"Documentation not supported for " + getExtension(name) + " files");
		}
		throw new ExitException(EXIT_INVALID_INPUT, "docs target not recognized: " + path);
	}

	private Integer handleDirectory(Path dir) throws IOException {
		try (Stream<Path> walk = Files.walk(dir)) {
			List<Path> javaFiles = walk.filter(p -> p.getFileName().toString().endsWith(".java"))
				.sorted()
				.collect(Collectors.toList());
			if (javaFiles.isEmpty()) {
				throw new ExitException(EXIT_INVALID_INPUT, "No .java files found in directory: " + dir);
			}
			List<DocsType> allTypes = new java.util.ArrayList<>();
			for (Path jf : javaFiles) {
				String content = new String(Files.readAllBytes(jf), java.nio.charset.StandardCharsets.UTF_8);
				allTypes.addAll(JavaSourceParser.parse(content));
			}
			printOutput(dir.getFileName().toString(), null, filterTypes(allTypes), "source");
			return EXIT_OK;
		}
	}

	private Integer handleJavaSource(Path path) throws IOException {
		String content = new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
		List<DocsType> parsed = JavaSourceParser.parse(content);
		printOutput(path.getFileName().toString(), null, filterTypes(parsed), "source");
		return EXIT_OK;
	}

	Integer handleJar(Path jarPath) throws IOException {
		String stem = jarPath.getFileName().toString().replaceFirst("\\.jar$", "");
		Path parent = jarPath.getParent() != null ? jarPath.getParent() : Paths.get(".");

		// Sidecar stub
		for (String ext : new String[] { "-docs.json", "-docs.md", "-docs.adoc" }) {
			Path candidate = parent.resolve(stem + ext);
			if (Files.exists(candidate)) {
				Util.verboseMsg("Found docs sidecar: " + candidate + " (not yet supported)");
			}
		}

		// Javadoc JAR lookup
		List<DocsType> docTypes;
		String generatedFrom;
		Path javadocJar = findJavadocJar(jarPath);
		if (javadocJar != null) {
			docTypes = JavadocHtmlParser.parse(javadocJar);
			generatedFrom = "javadoc";
		} else {
			docTypes = JavapExtractor.extract(jarPath);
			generatedFrom = "javap";
		}

		docTypes = filterTypes(docTypes);
		printOutput(jarPath.getFileName().toString(), null, docTypes, generatedFrom);
		return EXIT_OK;
	}

	private Path findJavadocJar(Path jarPath) {
		String stem = jarPath.getFileName().toString().replaceFirst("\\.jar$", "");
		Path parent = jarPath.getParent() != null ? jarPath.getParent() : Paths.get(".");
		for (String suffix : new String[] { "-javadoc.jar", "-javadocs.jar" }) {
			Path candidate = parent.resolve(stem + suffix);
			if (Files.exists(candidate))
				return candidate;
		}
		return null;
	}

	Integer handleRemoteTarget(String coordinate) throws IOException {
		MavenCoordinate coord;
		try {
			coord = MavenCoordinate.fromString(coordinate);
		} catch (IllegalStateException e) {
			throw new ExitException(EXIT_INVALID_INPUT,
					"docs requires Maven coordinates as group:artifact or group:artifact:version");
		}

		// Ensure a version is set; fall back to RELEASE so the resolver picks latest
		String version = coord.getVersion();
		if (version == null || version.isEmpty()) {
			version = "RELEASE";
		}
		String gav = coord.getGroupId() + ":" + coord.getArtifactId() + ":" + version;

		List<MavenRepo> repositories = new ArrayList<>();
		if (dependencyInfoMixin.getRepositories() != null) {
			for (String repo : dependencyInfoMixin.getRepositories()) {
				repositories.add(DependencyUtil.toMavenRepo(repo));
			}
		}
		// Always include Maven Central
		repositories.add(DependencyUtil.toMavenRepo("central"));

		try (ArtifactResolver resolver = ArtifactResolver.Builder.create()
			.repositories(repositories)
			.withUserSettings(true)
			.build()) {

			// Resolve the main artifact first to pin the exact version
			Artifact mainArtifact;
			try {
				mainArtifact = resolver.resolveArtifact(gav);
			} catch (ArtifactResolutionException e) {
				throw new ExitException(EXIT_GENERIC_ERROR,
						"Could not resolve artifact: " + gav + ". " + e.getMessage());
			}

			// Now resolve the javadoc classifier JAR
			Path javadocJar;
			try {
				javadocJar = resolver.resolveJavadocJar(mainArtifact);
			} catch (ArtifactResolutionException e) {
				throw new ExitException(EXIT_GENERIC_ERROR,
						"No javadoc JAR available for " + mainArtifact.getGroupId() + ":"
								+ mainArtifact.getArtifactId() + ":" + mainArtifact.getVersion()
								+ ". The artifact may not publish a -javadoc.jar.");
			}

			List<DocsType> docTypes = JavadocHtmlParser.parse(javadocJar);
			docTypes = filterTypes(docTypes);
			String artifactLabel = mainArtifact.getGroupId() + ":" + mainArtifact.getArtifactId() + ":"
					+ mainArtifact.getVersion();
			printOutput(coord.getArtifactId(), artifactLabel, docTypes, "javadoc");
		}
		return EXIT_OK;
	}

	private List<DocsType> filterTypes(List<DocsType> allTypes) {
		if (types == null || types.isEmpty())
			return allTypes;
		return allTypes.stream()
			.filter(t -> types.stream()
				.anyMatch(f -> t.getName().equals(f) || t.getQualifiedName().equals(f)))
			.collect(Collectors.toList());
	}

	private void printOutput(String title, String artifact, List<DocsType> docsTypes, String source) {
		if (json) {
			System.out.println(DocsRenderer.toJson(title, artifact, docsTypes, source));
		} else {
			System.out.println(DocsRenderer.toMarkdown(title, artifact, docsTypes));
		}
	}

	private static boolean looksLikeCoordinate(String target) {
		if (target.startsWith("http://") || target.startsWith("https://")) {
			return false;
		}
		String[] parts = target.split(":");
		if (parts.length < 2 || parts.length > 3) {
			return false;
		}
		for (String part : parts) {
			if (part.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static String getExtension(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot >= 0 ? filename.substring(dot) : "";
	}
}
