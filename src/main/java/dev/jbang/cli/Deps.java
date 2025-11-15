package dev.jbang.cli;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.aether.artifact.Artifact;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.search.ArtifactSearchWidget;
import dev.jbang.util.Util;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "deps", description = "Manage dependencies in jbang files.", subcommands = { DepsAdd.class,
		DepsSearch.class })
public class Deps extends BaseCommand {

	@Override
	public Integer doCall() throws IOException {
		// This is a parent command, subcommands handle the actual work
		return EXIT_OK;
	}
}

@Command(name = "search", description = "Search for artifacts.")
class DepsSearch extends BaseCommand {

	@CommandLine.Option(names = "--max", description = "Maximum number of results to return.", defaultValue = "100")
	int max;

	@CommandLine.Option(names = { "--query",
			"-q" }, description = "Artifact pattern to search for. If no pattern is provided, the user will be prompted to enter a pattern.", arity = "0..1")
	Optional<String> artifactPattern;

	@CommandLine.Parameters(description = "Target file (.java or build.jbang)", arity = "0..1")
	Optional<Path> target;

	boolean useWidget = true;

	@Override
	public Integer doCall() throws IOException {

		if (target.isPresent() && !Files.exists(target.get())) {
			throw new ExitException(EXIT_INVALID_INPUT, "Target file does not exist: " + target.get());
		}

		try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
			try {
				Artifact artifact = new ArtifactSearchWidget(terminal).search();
				if (target.isPresent()) {
					DepsAdd.updateFile(target.get(), Collections.singletonList(artifactGav(artifact)));
					info("Added " + artifactGav(artifact) + " to " + target.get());
				} else {
					System.out.printf("%s:%s:%s%n", artifact.getGroupId(), artifact.getArtifactId(),
							artifact.getVersion());
				}
				return EXIT_OK;
			} catch (IOError e) {
				return EXIT_INVALID_INPUT;
			}
		}
	}

	private static String artifactGav(Artifact artifact) {
		return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
	}

}

@Command(name = "add", description = "Add dependencies to a jbang file.")
class DepsAdd extends BaseCommand {

	boolean isDirective(String line) {
		String trimmed = line.trim();
		return trimmed.matches("//[A-Z]+") || trimmed.isEmpty();
	}

	@CommandLine.Parameters(description = "Dependencies to add (groupId:artifactId:version) and target file (.java or build.jbang)")
	List<String> parameters = new ArrayList<>();

	@Override
	public Integer doCall() throws IOException {
		if (parameters.size() < 2) {
			throw new ExitException(EXIT_INVALID_INPUT, "At least one dependency and target file are required");
		}

		// Last parameter is the target file
		Path targetFile = Paths.get(parameters.get(parameters.size() - 1));
		List<String> dependencies = parameters.subList(0, parameters.size() - 1);

		if (!Files.exists(targetFile)) {
			throw new ExitException(EXIT_INVALID_INPUT, "Target file does not exist: " + targetFile);
		}

		updateFile(targetFile, dependencies);

		info("Added dependencies to " + targetFile);
		return EXIT_OK;
	}

	private static void updateJavaFile(Path file, List<String> newDeps) throws IOException {
		String content = Util.readFileContent(file);
		List<String> lines = new ArrayList<>();
		boolean depsAdded = false;
		int lastDepsIndex = -1;

		// Find the last existing DEPS line
		for (int i = 0; i < content.split("\n").length; i++) {
			String line = content.split("\n")[i];
			lines.add(line);

			if (line.trim().startsWith("//DEPS ")) {
				lastDepsIndex = i;
			}
		}

		// Insert new dependencies after the last DEPS line, or after the /// line if no
		// DEPS exist
		int insertIndex;
		if (lastDepsIndex >= 0) {
			// Insert after the last existing DEPS line
			insertIndex = lastDepsIndex + 1;
		} else if (!lines.isEmpty() && lines.get(0).trim().startsWith("///")) {
			// Insert after the first line (/// env /bang line)
			insertIndex = 1;
		} else {
			// Insert at the beginning
			insertIndex = 0;
		}

		for (String dep : newDeps) {
			if (!isDependencyPresent(content, dep)) {
				// Remove any existing dependency with same groupId:artifactId but different
				// version
				removeExistingDependencyWithSameGroupArtifact(lines, dep);
				lines.add(insertIndex, "//DEPS " + dep);
				content += "\n//DEPS " + dep; // Update content for subsequent checks
				depsAdded = true;
				insertIndex++; // Move insertion point for next dependency
			}
		}

		if (depsAdded) {
			String newContent = String.join("\n", lines);
			Files.write(file, newContent.getBytes());
		}
	}

	static public void updateFile(Path file, List<String> dependencies) throws IOException {
		// Validate dependencies
		for (String dep : dependencies) {
			if (!DependencyUtil.looksLikeAGav(dep)) {
				throw new ExitException(EXIT_INVALID_INPUT, "Invalid dependency format: " + dep);
			}
		}
		String fileName = file.getFileName().toString();
		if (!Files.exists(file)) {
			throw new ExitException(EXIT_INVALID_INPUT, "File does not exist: " + file);
		}
		if (fileName.endsWith(".java")) {
			updateJavaFile(file, dependencies);
		} else if (fileName.endsWith(".jbang")) {
			updateBuildJbangFile(file, dependencies);
		}
	}

	static public void updateBuildJbangFile(Path file, List<String> newDeps) throws IOException {
		String content = Util.readFileContent(file);
		List<String> lines = new ArrayList<>();
		boolean depsAdded = false;

		for (String line : content.split("\n")) {
			lines.add(line);
		}

		// Add new dependencies at the end
		for (String dep : newDeps) {
			if (!isDependencyPresent(content, dep)) {
				// Remove any existing dependency with same groupId:artifactId but different
				// version
				removeExistingDependencyWithSameGroupArtifact(lines, dep);
				lines.add("DEPS " + dep);
				content += "\nDEPS " + dep; // Update content for subsequent checks
				depsAdded = true;
			}
		}

		if (depsAdded) {
			String newContent = String.join("\n", lines);
			Files.write(file, newContent.getBytes());
		}
	}

	private static void removeExistingDependencyWithSameGroupArtifact(List<String> lines, String dependency) {
		// Extract groupId:artifactId from the dependency
		MavenCoordinate coord = MavenCoordinate.fromString(dependency);
		if (coord == null) {
			return;
		}

		String groupId = coord.getGroupId();
		String artifactId = coord.getArtifactId();
		String groupArtifact = groupId + ":" + artifactId;

		// Remove any existing dependency with same groupId:artifactId but different
		// version
		lines.removeIf(line -> {
			String trimmed = line.trim();
			return (trimmed.startsWith("//DEPS ") || trimmed.startsWith("DEPS ")) &&
					trimmed.contains(groupArtifact) &&
					!trimmed.contains(dependency);
		});
	}

	static public boolean isDependencyPresent(String content, String dependency) {
		// Check for exact dependency match (including version)
		// Match both //DEPS (for .java files) and DEPS (for .jbang files)
		Pattern pattern = Pattern.compile("^(//)?DEPS\\s+" + Pattern.quote(dependency) + "\\s*$",
				Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(content);
		return matcher.find();
	}
}
