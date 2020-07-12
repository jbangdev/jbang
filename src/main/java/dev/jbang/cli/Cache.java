package dev.jbang.cli;

import dev.jbang.Settings;

import picocli.CommandLine;

@CommandLine.Command(name = "cache", description = "Cache management.")
public class Cache {
	@CommandLine.Command(name = "clear", description = "Clear cache of dependency list and temporary projects.")
	public Integer clear() {
		// info("Clearing cache at " + Settings.getCacheDir());
		// noinspection resource
		Settings.clearCache();
		return CommandLine.ExitCode.SOFTWARE;
	}
}
