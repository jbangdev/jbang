package dk.xam.jbang.cli;

import picocli.CommandLine;

@CommandLine.Command(name = "version", description = "Display version info.")
public class Version extends BaseCommand {

	@Override
	public Integer doCall() {
		spec.commandLine().getOut().println(dk.xam.jbang.BuildConfig.VERSION);
		return CommandLine.ExitCode.SOFTWARE;
	}
}
