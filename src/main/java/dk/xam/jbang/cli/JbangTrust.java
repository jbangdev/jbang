package dk.xam.jbang.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import dk.xam.jbang.Settings;

import picocli.CommandLine;

@CommandLine.Command(name = "trust", description = "Manage trust domains.", subcommands = { JbangTrustAdd.class,
		JbangTrustList.class, JbangTrustRemove.class })
public class JbangTrust {
}

@CommandLine.Command(name = "add", description = "Add trust domains.")
class JbangTrustAdd extends JbangBaseCommand {

	@CommandLine.Parameters(index = "0", description = "Rules for trusted sources", arity = "1..*")
	List<String> rules = new ArrayList<>();

	@Override
	public Integer doCall() {
		Settings.getTrustedSources().add(rules, Settings.getTrustedSourcesFile().toFile());
		return CommandLine.ExitCode.SOFTWARE;
	}
}

@CommandLine.Command(name = "list", description = "Show defined trust domains.")
class JbangTrustList extends JbangBaseCommand {

	@Override
	public Integer doCall() {
		int idx = 0;
		for (String src : Settings.getTrustedSources().trustedSources) {
			spec.commandLine().getOut().println(++idx + " = " + src);
		}
		return CommandLine.ExitCode.SOFTWARE;
	}
}

@CommandLine.Command(name = "remove", description = "Remove trust domains.")
class JbangTrustRemove extends JbangBaseCommand {

	@CommandLine.Parameters(index = "0", description = "Rules for trusted sources", arity = "1..*")
	List<String> rules = new ArrayList<>();

	@Override
	public Integer doCall() {
		List<String> newrules = rules	.stream()
										.map(src -> toDomain(src))
										.collect(Collectors.toList());
		Settings.getTrustedSources().remove(newrules, Settings.getTrustedSourcesFile().toFile());
		return CommandLine.ExitCode.SOFTWARE;
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