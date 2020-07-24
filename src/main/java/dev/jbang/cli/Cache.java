package dev.jbang.cli;

import java.util.Arrays;
import java.util.HashSet;

import dev.jbang.Settings;

import picocli.CommandLine;

@CommandLine.Command(name = "cache", description = "Cache management.")
public class Cache {
	@CommandLine.Command(name = "clear", description = "Clear cache of dependency list and temporary projects. By default this will clear the URL and JAR caches")
	public Integer clear(
			@CommandLine.Option(names = { "--url" }, description = "clear URL cache only") boolean urls,
			@CommandLine.Option(names = { "--jar" }, description = "clear JAR cache only") boolean jars,
			@CommandLine.Option(names = { "--jdk" }, description = "clear JDK cache only") boolean jdks,
			@CommandLine.Option(names = { "--all" }, description = "clear all caches") boolean all) {
		HashSet<Settings.CacheClass> classes = new HashSet<>();
		if (urls)
			classes.add(Settings.CacheClass.urls);
		if (jars)
			classes.add(Settings.CacheClass.jars);
		if (jdks)
			classes.add(Settings.CacheClass.jdks);
		if (all)
			classes.addAll(Arrays.asList(Settings.CacheClass.values()));
		if (classes.isEmpty()) {
			classes.add(Settings.CacheClass.urls);
			classes.add(Settings.CacheClass.jars);
		}
		Settings.CacheClass[] ccs = classes.toArray(new Settings.CacheClass[0]);
		Settings.clearCache(ccs);
		return CommandLine.ExitCode.SOFTWARE;
	}
}
