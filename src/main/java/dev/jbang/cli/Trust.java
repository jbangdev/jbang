package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.EXIT_OK;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

import dev.jbang.Settings;

import picocli.CommandLine;

@CommandLine.Command(name = "trust", description = "Manage which domains you trust to run scripts from.")
public class Trust {

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	@CommandLine.Command(name = "add", description = "Add trust domains.")
	public Integer add(
			@CommandLine.Parameters(index = "0", description = "Rules for trusted sources", arity = "1..*") List<String> rules) {
		Settings.getTrustedSources().add(rules, Settings.getTrustedSourcesFile().toFile());
		return EXIT_OK;
	}

	@CommandLine.Command(name = "list", description = "Show defined trust domains.")
	public Integer list() {
		int idx = 0;
		PrintStream out = System.out;
		for (String src : Settings.getTrustedSources().trustedSources) {
			out.println(++idx + " = " + src);
		}
		return EXIT_OK;
	}

	@CommandLine.Command(name = "remove", description = "Remove trust domains.")
	public Integer remove(
			@CommandLine.Parameters(index = "0", description = "Rules for trusted sources", arity = "1..*") List<String> rules) {
		List<String> newrules = rules	.stream()
										.map(src -> toDomain(src))
										.collect(Collectors.toList());
		Settings.getTrustedSources().remove(newrules, Settings.getTrustedSourcesFile().toFile());
		return EXIT_OK;
	}

	private String toDomain(String src) {
		String[] sources = Settings.getTrustedSources().trustedSources;
		try {
			int idx = Integer.parseInt(src) - 1;
			if (idx >= 0 && idx < sources.length) {
				return sources[idx];
			}
		} catch (NumberFormatException e) {
			// Ignore
		}
		return src;
	}
}