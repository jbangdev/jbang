package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.EXIT_OK;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

import org.aesh.command.CommandDefinition;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.option.Option;

@GroupCommandDefinition(name = "cache", description = "Manage compiled scripts in the local cache.", groupCommands = {
		Cache.CacheClear.class }, generateHelp = true, helpGroup = "Caching")
public class Cache extends BaseCommand {

	@Override
	public Integer doCall() {
		return missingSubcommand();
	}

	@CommandDefinition(name = "clear", description = "Clear the various caches used by jbang. By default this will clear the JAR, script, stdin and URL caches.", generateHelp = true)
	public static class CacheClear extends BaseCommand {

		@Option(name = "url", hasValue = false, negatable = true, description = "clear URL cache only")
		Boolean url;

		@Option(name = "jar", hasValue = false, negatable = true, description = "clear JAR cache only")
		Boolean jar;

		@Option(name = "deps", hasValue = false, negatable = true, description = "clear dependency cache only")
		Boolean deps;

		@Option(name = "jdk", hasValue = false, negatable = true, description = "clear JDK cache only")
		Boolean jdk;

		@Option(name = "kotlinc", hasValue = false, negatable = true, description = "clear kotlinc cache only")
		Boolean kotlinc;

		@Option(name = "groovyc", hasValue = false, negatable = true, description = "clear groovyc cache only")
		Boolean groovyc;

		@Option(name = "project", hasValue = false, negatable = true, description = "clear temporary projects cache only")
		Boolean project;

		@Option(name = "script", hasValue = false, negatable = true, description = "clear script cache only")
		Boolean script;

		@Option(name = "stdin", hasValue = false, negatable = true, description = "clear stdin cache only")
		Boolean stdin;

		@Option(name = "all", hasValue = false, description = "clear all caches")
		boolean all;

		@Override
		public Integer doCall() {
			EnumSet<dev.jbang.Cache.CacheClass> classes = EnumSet.noneOf(dev.jbang.Cache.CacheClass.class);

			// if all we add everything
			if (all) {
				classes.addAll(Arrays.asList(dev.jbang.Cache.CacheClass.values()));
			} else if (url == null
					&& jar == null
					&& jdk == null
					&& kotlinc == null
					&& groovyc == null
					&& project == null
					&& script == null
					&& stdin == null
					&& deps == null) {
				// add the default (safe) set
				classes.add(dev.jbang.Cache.CacheClass.urls);
				classes.add(dev.jbang.Cache.CacheClass.jars);
				classes.add(dev.jbang.Cache.CacheClass.scripts);
				classes.add(dev.jbang.Cache.CacheClass.stdins);
				classes.add(dev.jbang.Cache.CacheClass.deps);
			}

			// we only toggle on or off those that are actually present
			toggleCache(url, dev.jbang.Cache.CacheClass.urls, classes);
			toggleCache(jar, dev.jbang.Cache.CacheClass.jars, classes);
			toggleCache(jdk, dev.jbang.Cache.CacheClass.jdks, classes);
			toggleCache(kotlinc, dev.jbang.Cache.CacheClass.kotlincs, classes);
			toggleCache(groovyc, dev.jbang.Cache.CacheClass.groovycs, classes);
			toggleCache(deps, dev.jbang.Cache.CacheClass.deps, classes);
			toggleCache(project, dev.jbang.Cache.CacheClass.projects, classes);
			toggleCache(script, dev.jbang.Cache.CacheClass.scripts, classes);
			toggleCache(stdin, dev.jbang.Cache.CacheClass.stdins, classes);

			dev.jbang.Cache.CacheClass[] ccs = classes.toArray(new dev.jbang.Cache.CacheClass[0]);
			dev.jbang.Cache.clearCache(ccs);
			return EXIT_OK;
		}

		private void toggleCache(Boolean b, dev.jbang.Cache.CacheClass cache,
				EnumSet<dev.jbang.Cache.CacheClass> classes) {
			if (Optional.ofNullable(b).isPresent()) {
				if (b) {
					classes.add(cache);
				} else {
					classes.remove(cache);
				}
			}
		}
	}
}
