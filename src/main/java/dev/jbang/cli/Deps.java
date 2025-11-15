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

@Command(name = "search", header = "Search for artifacts in local and central Maven repositories.", description = {
		"",
		"  ${COMMAND-FULL-NAME}",
		"        (to search for artifacts interactively)",
		"  or  ${COMMAND-FULL-NAME} jash",
		"        (to search for 'jash' interactively)",
		"  or  ${COMMAND-FULL-NAME} myapp.java",
		"        (to search for dependencies and add them to myapp.java)",
		"  or  ${COMMAND-FULL-NAME} jash myapp.java",
		"        (to search initially for 'jash' and add dependency to myapp.java)",
		"",
		" note: JBang will detect if the arguments are a file or query, but if you want to be explicit you can use the --query and --target options.",
		"",
		"" })
class DepsSearch extends BaseCommand {

	@CommandLine.Option(names = "--max", description = "Maximum number of results to return.", defaultValue = "100")
	int max;

	@CommandLine.Option(names = { "--query",
			"-q" }, description = "Artifact pattern to search for.", arity = "0..1")
	Optional<String> query;

	@CommandLine.Option(names = {
			"--target" }, description = "Target where to add the dependency, i.e. app.java or build.jbang", arity = "0..1")
	Optional<Path> target;

	@CommandLine.Parameters(arity = "0..2", paramLabel = "[query] [target]", description = "Artifact pattern to search for and target where to add the dependency, i.e. app.java or build.jbang. Query and target can both be optional")
	List<String> args = new ArrayList<>();

	private boolean looksLikeATarget(String a0) {
		return Files.exists(Paths.get(a0));
	}

	private void updateTarget(String a0) throws IOException {
		if (target.isPresent()) {
			throw new IllegalArgumentException("Cannot provide both target as as parameter and as option");
		} else {
			target = Optional.of(Paths.get(a0));
		}
	}

	private void updateQuery(String a0) {
		if (query.isPresent()) {
			throw new IllegalArgumentException("Cannot provide both query as as parameter and as option");
		} else {
			query = Optional.of(a0);
		}
	}

	@Override
	public Integer doCall() throws IOException {

		if (args.size() == 1) { // either query or target
			String a0 = args.get(0);
			if (looksLikeATarget(a0)) {
				updateTarget(a0);
			} else {
				updateQuery(a0);
			}
		} else if (args.size() == 2) { // both query and target
			updateQuery(args.get(0));
			updateTarget(args.get(1));
		}

		if (target.isPresent() && !Files.exists(target.get())) {
			throw new ExitException(EXIT_INVALID_INPUT, "Target file does not exist: " + target.get());
		}

		try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
			try {
				Artifact artifact = new ArtifactSearchWidget(terminal).search(query.orElse(""));
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
