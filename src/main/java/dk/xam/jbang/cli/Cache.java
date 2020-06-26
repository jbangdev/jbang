package dk.xam.jbang.cli;

import dk.xam.jbang.Settings;

import picocli.CommandLine;

@CommandLine.Command(name = "cache", description = "Cache management.", subcommands = { CacheClear.class })
public class Cache {
}

@CommandLine.Command(name = "clear", description = "Clear cache of dependency list and temporary projects.")
class CacheClear extends BaseCommand {

	@Override
	public Integer doCall() {
		info("Clearing cache at " + Settings.getCacheDir());
		// noinspection resource
		Settings.clearCache();
		return CommandLine.ExitCode.SOFTWARE;
	}
}
