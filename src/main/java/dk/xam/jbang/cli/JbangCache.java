package dk.xam.jbang.cli;

import dk.xam.jbang.Settings;

import picocli.CommandLine;

@CommandLine.Command(name = "cache", description = "Cache management.", subcommands = { JbangCacheClear.class })
public class JbangCache {
}

@CommandLine.Command(name = "clear", description = "Clear cache of dependency list and temporary projects.")
class JbangCacheClear extends JbangBaseCommand {

	@Override
	public Integer doCall() {
		info("Clearing cache at " + Settings.getCacheDir());
		// noinspection resource
		Settings.clearCache();
		return CommandLine.ExitCode.SOFTWARE;
	}
}
