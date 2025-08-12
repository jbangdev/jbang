package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.EXIT_OK;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

import picocli.CommandLine;

@CommandLine.Command(name = "cache", description = "Manage compiled scripts in the local cache.")
public class Cache {

	@CommandLine.Mixin
	HelpMixin helpMixin;

	@CommandLine.Command(name = "clear", description = "Clear the various caches used by jbang. By default this will clear the JAR, script, stdin and URL caches. To clear other caches list them explicitly i.e. '--project' for temporary projects.")
	public Integer clear(
			@CommandLine.Option(names = {
					"--url" }, description = "clear URL cache only", negatable = true) Boolean urls,
			@CommandLine.Option(names = {
					"--jar" }, description = "clear JAR cache only", negatable = true) Boolean jars,
			@CommandLine.Option(names = {
					"--deps" }, description = "clear dependency cache only", negatable = true) Boolean deps,
			@CommandLine.Option(names = {
					"--jdk" }, description = "clear JDK cache only", negatable = true) Boolean jdks,
			@CommandLine.Option(names = {
					"--kotlinc" }, description = "clear kotlinc cache only", negatable = true) Boolean kotlincs,
			@CommandLine.Option(names = {
					"--groovyc" }, description = "clear groovyc cache only", negatable = true) Boolean groovys,
			@CommandLine.Option(names = {
					"--project" }, description = "clear temporary projects cache only", negatable = true) Boolean projects,
			@CommandLine.Option(names = {
					"--script" }, description = "clear script cache only", negatable = true) Boolean scripts,
			@CommandLine.Option(names = {
					"--stdin" }, description = "clear stdin cache only", negatable = true) Boolean stdins,
			@CommandLine.Option(names = { "--all" }, description = "clear all caches") boolean all) {
		EnumSet<dev.jbang.Cache.CacheClass> classes = EnumSet.noneOf(dev.jbang.Cache.CacheClass.class);

		// if all we add everything
		if (all) {
			classes.addAll(Arrays.asList(dev.jbang.Cache.CacheClass.values()));
		} else if (urls == null
				&& jars == null
				&& jdks == null
				&& kotlincs == null
				&& groovys == null
				&& projects == null
				&& scripts == null
				&& stdins == null
				&& deps == null) {
			// add the default (safe) set
			classes.add(dev.jbang.Cache.CacheClass.urls);
			classes.add(dev.jbang.Cache.CacheClass.jars);
			classes.add(dev.jbang.Cache.CacheClass.scripts);
			classes.add(dev.jbang.Cache.CacheClass.stdins);
			classes.add(dev.jbang.Cache.CacheClass.deps);
		}

		// we only toggle on or off those that are actually present
		toggleCache(urls, dev.jbang.Cache.CacheClass.urls, classes);
		toggleCache(jars, dev.jbang.Cache.CacheClass.jars, classes);
		toggleCache(jdks, dev.jbang.Cache.CacheClass.jdks, classes);
		toggleCache(kotlincs, dev.jbang.Cache.CacheClass.kotlincs, classes);
		toggleCache(kotlincs, dev.jbang.Cache.CacheClass.groovycs, classes);
		toggleCache(deps, dev.jbang.Cache.CacheClass.deps, classes);
		toggleCache(projects, dev.jbang.Cache.CacheClass.projects, classes);
		toggleCache(scripts, dev.jbang.Cache.CacheClass.scripts, classes);
		toggleCache(stdins, dev.jbang.Cache.CacheClass.stdins, classes);

		dev.jbang.Cache.CacheClass[] ccs = classes.toArray(new dev.jbang.Cache.CacheClass[0]);
		dev.jbang.Cache.clearCache(ccs);
		return EXIT_OK;
	}

	private void toggleCache(Boolean b, dev.jbang.Cache.CacheClass cache, EnumSet<dev.jbang.Cache.CacheClass> classes) {
		if (Optional.ofNullable(b).isPresent()) {
			if (b) {
				classes.add(cache);
			} else {
				classes.remove(cache);
			}
		}
	}
}
