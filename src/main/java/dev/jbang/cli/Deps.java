package dev.jbang.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.VersionScheme;
import org.jline.consoleui.elements.InputValue;
import org.jline.consoleui.elements.ListChoice;
import org.jline.consoleui.elements.PageSizeType;
import org.jline.consoleui.elements.PromptableElementIF;
import org.jline.consoleui.elements.items.ListItemIF;
import org.jline.consoleui.elements.items.impl.ListItem;
import org.jline.consoleui.prompt.ConsolePrompt;
import org.jline.consoleui.prompt.ListResult;
import org.jline.consoleui.prompt.PromptResultItemIF;
import org.jline.consoleui.prompt.builder.PromptBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.MavenCoordinate;
import dev.jbang.search.ArtifactSearch;
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

		if (useWidget) {
			try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
				new ArtifactSearchWidget(terminal).search();
			} catch (IOException e) {
				throw new ExitException(EXIT_INVALID_INPUT, "Error searching for artifacts: " + e.getMessage());
			}
		} else {

			try (Terminal terminal = TerminalBuilder.builder()
				// .systemOutput(SystemOutput.SysErr)
				// .system(true)
				.build()) {
				while (true) {
					ConsolePrompt.UiConfig cfg = new ConsolePrompt.UiConfig();
					cfg.setCancellableFirstPrompt(true);
					ConsolePrompt prompt = new ConsolePrompt(null, terminal, cfg);
					Map<String, PromptResultItemIF> result = prompt.prompt(this::nextQuestion);
					if (result.isEmpty()) {
						break;
					}
					String selectedArtifact = getSelectedId(result, "item");
					String artifactAction = getSelectedId(result, "action");
					if ("add".equals(artifactAction)) {
						target.ifPresent(t -> {
							try {
								DepsAdd.updateFile(t, Collections.singletonList(selectedArtifact));
							} catch (IOException e) {
								throw new ExitException(EXIT_INVALID_INPUT,
										"Error adding dependency to " + t + ": " + e.getMessage());
							}
						});
					}
					System.out.println(artifactPattern + "->" + selectedArtifact + "->" + artifactAction);

					String finalAction = selectFinalAction(prompt);
					if (!"again".equals(finalAction)) {
						break;
					}
					artifactPattern = null;
				}
			}
		}
		return EXIT_OK;
	}

	static class ArtifactResult {
		final String ga;

		public ArtifactResult(String ga) {
			this.ga = ga;
		}

		VersionScheme versionScheme = new GenericVersionScheme();

		TreeSet<Artifact> artifacts = new TreeSet<>(Comparator.comparing(a -> {
			try {
				return versionScheme.parseVersion(a.getVersion());
			} catch (Exception e) {
				throw new RuntimeException("Failed to parse version", e);
			}
		}));

		public String getLatestGav() {
			Artifact artifact = artifacts.last();
			return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
		}
	}

	public ArtifactResult[] search(String artifactPattern) {
		try {
			return search(artifactPattern, max, ArtifactSearch.Backends.rest_csc);
		} catch (IOException e) {
			throw new ExitException(EXIT_INVALID_INPUT, "Error searching for artifacts: " + e.getMessage());
		}
	}

	public ArtifactResult[] search(String artifactPattern, int count, ArtifactSearch.Backends backend)
			throws IOException {
		Map<String, ArtifactResult> artifacts = new HashMap<>();
		int max = count <= 0 || count > 200 ? 200 : count;
		ArtifactSearch s = ArtifactSearch.getBackend(backend);
		ArtifactSearch.SearchResult result = s.findArtifacts(artifactPattern, max);
		while (result != null) {
			for (Artifact artifact : result.artifacts) {
				artifacts.computeIfAbsent(artifact.getGroupId() + ":" + artifact.getArtifactId(),
						k -> new ArtifactResult(k)).artifacts
					.add(artifact);
			}
			result = count <= 0 ? s.findNextArtifacts(result) : null;
		}

		return artifacts.values().toArray(new ArtifactResult[0]);
	}

	private static String artifactGav(Artifact artifact) {
		return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
	}

	List<PromptableElementIF> nextQuestion(Map<String, PromptResultItemIF> results) {
		String pattern;
		if (!artifactPattern.isPresent()) {
			if (!results.containsKey("input")) {
				return Collections.singletonList(stringElement("Search for:"));
			}
			pattern = results.get("input").getResult();
		} else {
			pattern = artifactPattern.get();
		}

		if (!results.containsKey("item")) {
			ArtifactResult[] artifactNames = search(pattern);
			return Collections.singletonList(selectElement("Select artifact:", artifactNames, false));
		}

		if (!results.containsKey("action")) {
			return Collections.singletonList(selectArtifactActionElement());
		} else if ("version".equals(getSelectedId(results, "action"))) {
			results.remove("action");
			pattern = getSelectedId(results, "item");
			ArtifactResult[] artifactNames = search(pattern);
			return Collections.singletonList(selectElement("Select version:", artifactNames, true));
		}

		return null;
	}

	InputValue stringElement(String message) {
		return new InputValue("input", message);
	}

	ListChoice selectElement(String message, ArtifactResult[] items, boolean showVersions) {
		List<ListItemIF> itemList = null;

		if (showVersions) {
			itemList = Arrays.stream(items)
				.flatMap(it -> it.artifacts.descendingSet()
					.stream()
					.map(artifact -> new ListItem(artifactGav(artifact), artifactGav(artifact))))
				.collect(Collectors.toList());

		} else {
			itemList = Arrays.stream(items)
				.map(it -> new ListItem(it.getLatestGav(), it.getLatestGav()))
				.collect(Collectors.toList());
		}
		return new ListChoice(message, "item", 10, PageSizeType.ABSOLUTE, itemList);
	}

	ListChoice selectElement(String message, String[] items) {
		List<ListItemIF> itemList = Arrays.stream(items)
			.map(it -> new ListItem(it, it))
			.collect(Collectors.toList());
		return new ListChoice(message, "item", 10, PageSizeType.ABSOLUTE, itemList);
	}

	ListChoice selectArtifactActionElement() {
		List<ListItemIF> itemList = new ArrayList<>();

		target.ifPresent(t -> itemList.add(new ListItem("Add to " + t, "add")));
		itemList.add(new ListItem("Select different version", "version"));
		itemList.add(new ListItem("Quit", "quit"));
		return new ListChoice("What to do:", "action", 10, PageSizeType.ABSOLUTE, itemList);
	}

	String selectFinalAction(ConsolePrompt prompt) throws IOException {
		PromptBuilder promptBuilder = prompt.getPromptBuilder();
		promptBuilder
			.createListPrompt()
			.name("action")
			.message("Next step:")
			.newItem("quit")
			.text("Quit")
			.add()
			.newItem("again")
			.text("Search again")
			.add()
			.addPrompt();
		Map<String, PromptResultItemIF> result = prompt.prompt(promptBuilder.build());
		return getSelectedId(result, "action");
	}

	private static String getSelectedId(
			Map<String, PromptResultItemIF> result, String itemName) {
		return ((ListResult) result.get(itemName)).getSelectedId();
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

		// Insert new dependencies after the last DEPS line
		int insertIndex = lastDepsIndex + 1;

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
