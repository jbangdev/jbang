package dev.jbang.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.aesh.command.HelpEntry;
import org.aesh.command.HelpSectionProvider;

import dev.jbang.catalog.Catalog;
import dev.jbang.util.Util;

public class ExternalCommandsProvider implements HelpSectionProvider {

	@Override
	public String getHeader() {
		return "\n"
				+ "  ${COMMAND-NAME} init hello.java [args...]\n"
				+ "        (to initialize a script)\n"
				+ "  or  ${COMMAND-NAME} edit --open=code --live hello.java\n"
				+ "        (to edit a script in IDE with live updates)\n"
				+ "  or  ${COMMAND-NAME} hello.java [args...]\n"
				+ "        (to run a .java file)\n"
				+ "  or  ${COMMAND-NAME} gavsearch@jbangdev [args...]\n"
				+ "        (to run an alias from a catalog)\n"
				+ "  or  ${COMMAND-NAME} group-id:artifact-id:version [args...]\n"
				+ "        (to run a .jar file found with a GAV id)\n"
				+ "\n"
				+ " note: run is the default command. To get help about run use ${COMMAND-NAME} run --help";
	}

	@Override
	public String getFooter() {
		return "\n   JBang ❤️  Commonhaus Foundation, MIT License\n"
				+ "🏡 https://www.commonhaus.org/community\n"
				+ "🚀 https://jbang.dev";
	}

	@Override
	public Map<String, List<HelpEntry>> getAdditionalSections() {
		Map<String, List<HelpEntry>> sections = new LinkedHashMap<>();

		Map<String, String> commands = findExternalCommands();
		if (!commands.isEmpty()) {
			List<HelpEntry> entries = commands.entrySet()
				.stream()
				.map(e -> new HelpEntry(e.getKey(), e.getValue()))
				.collect(Collectors.toList());
			sections.put("External", entries);
		}

		return sections;
	}

	private static Map<String, String> findExternalCommands() {
		Map<String, String> result = new TreeMap<>();
		try {
			Catalog cat = Catalog.getMerged(true, false);
			for (String name : cat.aliases.keySet()) {
				if (name.startsWith("jbang-")) {
					result.put(name.substring(6), cat.aliases.get(name).description);
				}
			}
		} catch (Exception ex) {
			Util.verboseMsg("Error trying to list aliases", ex);
		}
		try {
			List<Path> paths = getPluginPaths();
			List<Path> cmds = findCommandsWith(paths, p -> p.getFileName().toString().startsWith("jbang-"));
			for (Path p : cmds) {
				String name = Util.base(p.getFileName().toString()).substring(6);
				result.putIfAbsent(name, null);
			}
		} catch (Exception ex) {
			Util.verboseMsg("Error trying to list jbang-commands", ex);
		}
		return result;
	}

	private static List<Path> getPluginPaths() {
		return Arrays.stream(System.getenv().getOrDefault("PATH", "").split(File.pathSeparator))
			.filter(Util::isValidPath)
			.map(Paths::get)
			.filter(Files::isDirectory)
			.collect(Collectors.toList());
	}

	private static List<Path> findCommandsWith(List<Path> pathElems, java.util.function.Predicate<Path> accept) {
		return pathElems.stream()
			.filter(Files::isDirectory)
			.flatMap(dir -> listFiles(dir).filter(Util::isExecutable).filter(accept))
			.collect(Collectors.toList());
	}

	private static Stream<Path> listFiles(Path dir) {
		try {
			return Files.list(dir);
		} catch (IOException e) {
			return Stream.empty();
		}
	}
}
