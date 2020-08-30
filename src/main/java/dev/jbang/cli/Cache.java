package dev.jbang.cli;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

import dev.jbang.Settings;

import picocli.CommandLine;

@CommandLine.Command(name = "cache", description = "Manage compiled scripts in the local cache.")
public class Cache {
	@CommandLine.Command(name = "clear", description = "Clear cache of dependency list and temporary projects. By default this will clear the JAR, script, stdin and URL caches")
	public Integer clear(
			@CommandLine.Option(names = {
					"--url" }, description = "clear URL cache only", negatable = true) Boolean urls,
			@CommandLine.Option(names = {
					"--jar" }, description = "clear JAR cache only", negatable = true) Boolean jars,
			@CommandLine.Option(names = {
					"--jdk" }, description = "clear JDK cache only", negatable = true) Boolean jdks,
			@CommandLine.Option(names = {
					"--project" }, description = "clear temporary projects cache only", negatable = true) Boolean projects,
			@CommandLine.Option(names = {
					"--script" }, description = "clear script cache only", negatable = true) Boolean scripts,
			@CommandLine.Option(names = {
					"--stdin" }, description = "clear stdin cache only", negatable = true) Boolean stdins,
			@CommandLine.Option(names = { "--all" }, description = "clear all caches") boolean all) {
		EnumSet<Settings.CacheClass> classes = EnumSet.noneOf(Settings.CacheClass.class);

		// if all we add everything
		if (all) {
			classes.addAll(Arrays.asList(Settings.CacheClass.values()));
		} else if (urls == null
				&& jars == null
				&& jdks == null
				&& projects == null
				&& scripts == null
				&& stdins == null) {
			// add the default (safe) set
			classes.add(Settings.CacheClass.urls);
			classes.add(Settings.CacheClass.jars);
			classes.add(Settings.CacheClass.scripts);
			classes.add(Settings.CacheClass.stdins);
		}

		// we only toggle on or off those that are actually present
		toggleCache(urls, Settings.CacheClass.urls, classes);
		toggleCache(jars, Settings.CacheClass.jars, classes);
		toggleCache(jdks, Settings.CacheClass.jdks, classes);
		toggleCache(projects, Settings.CacheClass.projects, classes);
		toggleCache(scripts, Settings.CacheClass.scripts, classes);
		toggleCache(stdins, Settings.CacheClass.stdins, classes);

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
