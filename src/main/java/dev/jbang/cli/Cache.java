package dev.jbang.cli;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

import dev.jbang.Settings;

import picocli.CommandLine;

@CommandLine.Command(name = "cache", description = "Cache management.")
public class Cache {
	@CommandLine.Command(name = "clear", description = "Clear cache of dependency list and temporary projects. By default this will clear the URL and JAR caches")
	public Integer clear(
			@CommandLine.Option(names = {
					"--url" }, description = "clear URL cache only", negatable = true) Boolean urls,
			@CommandLine.Option(names = {
					"--jar" }, description = "clear JAR cache only", negatable = true) Boolean jars,
			@CommandLine.Option(names = {
					"--jdk" }, description = "clear JDK cache only", negatable = true) Boolean jdks,
			@CommandLine.Option(names = { "--all" }, description = "clear all caches") boolean all) {
		EnumSet<Settings.CacheClass> classes = EnumSet.noneOf(Settings.CacheClass.class);

		// add the default (safe) set
		classes.add(Settings.CacheClass.urls);
		classes.add(Settings.CacheClass.jars);
		// if all we add everything
		if (all) {
			classes.addAll(Arrays.asList(Settings.CacheClass.values()));
		}

		// we only toggle on or off those that are acutally present

		toggleCache(urls, Settings.CacheClass.urls, classes);
		toggleCache(jars, Settings.CacheClass.jars, classes);
		toggleCache(jdks, Settings.CacheClass.jdks, classes);

		Settings.CacheClass[] ccs = classes.toArray(new Settings.CacheClass[0]);
		Settings.clearCache(ccs);
		return CommandLine.ExitCode.OK;
	}

	private void toggleCache(Boolean b, Settings.CacheClass cache, EnumSet<Settings.CacheClass> classes) {
		if (Optional.ofNullable(b).isPresent()) {
			if (b.booleanValue()) {
				classes.add(cache);
			} else {
				classes.remove(cache);
			}
		}
	}
}
