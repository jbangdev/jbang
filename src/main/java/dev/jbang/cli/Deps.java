package dev.jbang.cli;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;
import org.eclipse.aether.artifact.Artifact;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.search.ArtifactSearchWidget;
import dev.jbang.source.update.FileUpdateStrategy;
import dev.jbang.source.update.FileUpdaters;

@CommandDefinition(name = "deps", description = "Manage dependencies in jbang files.", groupCommands = {
		Deps.DepsSearch.class,
		Deps.DepsAdd.class }, generateHelp = true, helpGroup = "Editing")
public class Deps extends BaseCommand {

	@Override
	public Integer doCall() throws IOException {
		return missingSubcommand();
	}

	@CommandDefinition(name = "search", description = "Search for artifacts in local and central Maven repositories.", generateHelp = true)
	public static class DepsSearch extends BaseCommand {

		@Option(name = "max", description = "Maximum number of results to return.", defaultValue = "100")
		int max;

		@Option(shortName = 'q', name = "query", description = "Artifact pattern to search for.")
		String query;

		@Option(name = "target", description = "Target where to add the dependency, i.e. app.java or build.jbang")
		String target;

		@Arguments(description = "[query] [target]")
		List<String> args = new ArrayList<>();

		private boolean looksLikeATarget(String a0) {
			return Files.exists(Paths.get(a0));
		}

		private void updateTarget(String a0) throws IOException {
			if (target != null) {
				throw new ExitException(EXIT_INVALID_INPUT, "Cannot provide both target as as parameter and as option");
			} else {
				target = a0;
			}
		}

		private void updateQuery(String a0) {
			if (query != null) {
				throw new ExitException(EXIT_INVALID_INPUT, "Cannot provide both query as as parameter and as option");
			} else {
				query = a0;
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

			Path targetPath = target != null ? Paths.get(target) : null;
			if (targetPath != null && !Files.exists(targetPath)) {
				throw new ExitException(EXIT_INVALID_INPUT, "Target file does not exist: " + targetPath);
			}

			try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
				try {
					Artifact artifact = new ArtifactSearchWidget(terminal).search(query != null ? query : "");
					if (targetPath != null) {
						DepsAdd.updateFile(targetPath, Collections.singletonList(artifactGav(artifact)));
						info("Added " + artifactGav(artifact) + " to " + targetPath);
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

	@CommandDefinition(name = "add", description = "Add dependencies to a jbang file.", generateHelp = true)
	public static class DepsAdd extends BaseCommand {

		@Arguments(paramLabel = "deps target", arity = "2..*", description = "Dependencies to add (groupId:artifactId:version) and target file (.java or build.jbang)")
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
}
