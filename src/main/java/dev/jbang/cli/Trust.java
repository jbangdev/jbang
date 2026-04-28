package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.EXIT_OK;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.Settings;
import dev.jbang.net.TrustedSources;

@GroupCommandDefinition(name = "trust", description = "Manage which domains you trust to run scripts from.", groupCommands = {
		Trust.TrustAdd.class, Trust.TrustList.class,
		Trust.TrustRemove.class }, generateHelp = true, helpGroup = "Configuration", defaultValueProvider = JBangDefaultValueProvider.class)
public class Trust extends BaseCommand {

	@Override
	public Integer doCall() throws IOException {
		return missingSubcommand();
	}

	@CommandDefinition(name = "add", description = "Add trust domains.", generateHelp = true, defaultValueProvider = JBangDefaultValueProvider.class)
	public static class TrustAdd extends BaseCommand {

		@Arguments(description = "Rules for trusted sources", required = true)
		List<String> rules;

		@Override
		public Integer doCall() throws IOException {
			TrustedSources.instance().add(rules);
			return EXIT_OK;
		}
	}

	@CommandDefinition(name = "list", description = "Show defined trust domains.", generateHelp = true, defaultValueProvider = JBangDefaultValueProvider.class)
	public static class TrustList extends BaseCommand {

		@Option(name = "format", description = "Specify output format ('text' or 'json')")
		String format;

		@Override
		public Integer doCall() throws IOException {
			validateFormat(format);
			int idx = 0;
			PrintStream out = System.out;
			if ("json".equals(format)) {
				Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
				parser.toJson(TrustedSources.instance().getTrustedSources(), out);
			} else {
				for (String src : TrustedSources.instance().getTrustedSources()) {
					out.println(++idx + " = " + src);
				}
			}
			return EXIT_OK;
		}
	}

	@CommandDefinition(name = "remove", description = "Remove trust domains.", generateHelp = true, defaultValueProvider = JBangDefaultValueProvider.class)
	public static class TrustRemove extends BaseCommand {

		@Arguments(description = "Rules for trusted sources", required = true)
		List<String> rules;

		@Override
		public Integer doCall() throws IOException {
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
}
