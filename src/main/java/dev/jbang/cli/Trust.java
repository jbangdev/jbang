package dev.jbang.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.ExitException;
import dev.jbang.Settings;
import dev.jbang.net.TrustedSources;

@CommandDefinition(name = "trust", description = "Manage which domains you trust to run scripts from.", groupCommands = {
		Trust.TrustAdd.class, Trust.TrustList.class,
		Trust.TrustRemove.class }, helpGroup = "Configuration")
public class Trust extends BaseCommand implements CommandHelpProvider {

	@Override
	public Integer doCall() throws IOException {
		return missingSubcommand();
	}

	@Override
	public String prose() {
		return "jbang only runs scripts from remote URLs you trust, and asks the first time it\n"
				+ "meets a new one. These commands manage that list of trusted URL prefixes.";
	}

	@Override
	public List<String> examples() {
		return Arrays.asList(
				"${COMMAND-FULL-NAME} add https://github.com/jbangdev/",
				"${COMMAND-FULL-NAME} list",
				"${COMMAND-FULL-NAME} remove https://github.com/jbangdev/");
	}

	@CommandDefinition(name = "add", description = "Add trust domains.")
	public static class TrustAdd extends BaseCommand implements CommandHelpProvider {

		@Arguments(paramLabel = "rules", arity = "1..*", description = "Rules for trusted sources", required = true)
		List<String> rules;

		@Override
		public Integer doCall() throws IOException {
			TrustedSources.instance().add(rules);
			return ExitException.EXIT_OK;
		}

		@Override
		public String prose() {
			return "Trusts scripts whose URL starts with one of the given prefixes. Keep the prefix\n"
					+ "as narrow as you can, e.g. a user or repository instead of a whole site.";
		}

		@Override
		public List<String> examples() {
			return Arrays.asList(
					"${COMMAND-FULL-NAME} https://github.com/jbangdev/",
					"${COMMAND-FULL-NAME} https://github.com/myorg/scripts/");
		}
	}

	@CommandDefinition(name = "list", description = "Show defined trust domains.")
	public static class TrustList extends BaseCommand {

		@Option(name = "format", description = "Specify output format ('text' or 'json')")
		OutputFormat format;

		@Override
		public Integer doCall() throws IOException {
			int idx = 0;
			PrintStream out = System.out;
			if (format == OutputFormat.json) {
				Gson parser = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
				parser.toJson(TrustedSources.instance().getTrustedSources(), out);
			} else {
				for (String src : TrustedSources.instance().getTrustedSources()) {
					out.println(++idx + " = " + src);
				}
			}
			return ExitException.EXIT_OK;
		}
	}

	@CommandDefinition(name = "remove", description = "Remove trust domains.")
	public static class TrustRemove extends BaseCommand {

		@Arguments(paramLabel = "rules", arity = "1..*", description = "Rules for trusted sources", required = true)
		List<String> rules;

		@Override
		public Integer doCall() throws IOException {
			List<String> newrules = rules.stream()
				.map(this::toDomain)
				.collect(Collectors.toList());
			TrustedSources.instance().remove(newrules, Settings.getTrustedSourcesFile().toFile());
			return ExitException.EXIT_OK;
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
