package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.EXIT_OK;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.Settings;
import dev.jbang.net.TrustedSources;

import picocli.CommandLine;

@CommandLine.Command(name = "trust", description = "Manage which domains you trust to run scripts from.")
public class Trust {

	@CommandLine.Mixin
	HelpMixin helpMixin;

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	@CommandLine.Command(name = "add", description = "Add trust domains.")
	public Integer add(
			@CommandLine.Parameters(index = "0", description = "Rules for trusted sources", arity = "1..*") List<String> rules) {
		TrustedSources.instance().add(rules);
		return EXIT_OK;
	}

	@CommandLine.Command(name = "list", description = "Show defined trust domains.")
	public Integer list(
			@CommandLine.Option(names = {
					"--format" }, description = "Specify output format ('text' or 'json')") FormatMixin.Format format) {
		int idx = 0;
		PrintStream out = System.out;
		if (format == FormatMixin.Format.json) {
			Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			parser.toJson(TrustedSources.instance().getTrustedSources(), out);
		} else {
			for (String src : TrustedSources.instance().getTrustedSources()) {
				out.println(++idx + " = " + src);
			}
		}
		return EXIT_OK;
	}

	@CommandLine.Command(name = "remove", description = "Remove trust domains.")
	public Integer remove(
			@CommandLine.Parameters(index = "0", description = "Rules for trusted sources", arity = "1..*") List<String> rules) {
		List<String> newrules = rules.stream()
			.map(this::toDomain)
			.collect(Collectors.toList());
		TrustedSources.instance().remove(newrules, Settings.getTrustedSourcesFile().toFile());
		return EXIT_OK;
	}

	private String toDomain(String src) {
		String[] sources = TrustedSources.instance().getTrustedSources();
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