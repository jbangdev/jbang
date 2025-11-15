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

import org.eclipse.aether.artifact.Artifact;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.search.ArtifactSearchWidget;
import dev.jbang.source.update.FileUpdateStrategy;
import dev.jbang.source.update.FileUpdaters;

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

	static public void updateFile(Path file, List<String> dependencies) throws IOException {
		// Validate dependencies
		for (String dep : dependencies) {
			if (!DependencyUtil.looksLikeAGav(dep)) {
				throw new ExitException(EXIT_INVALID_INPUT, "Invalid dependency format: " + dep);
			}
		}
		if (!Files.exists(file)) {
			throw new ExitException(EXIT_INVALID_INPUT, "File does not exist: " + file);
		}

		FileUpdateStrategy strategy = FileUpdaters.forFile(file);
		strategy.updateFile(file, dependencies);
	}
}
