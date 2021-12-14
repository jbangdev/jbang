package dev.jbang.source.resolvers;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;
import dev.jbang.util.Util;

/**
 * A <code>ResourceResolver</code> that, when given a resource string which
 * looks like a path to a file on the local file system not ending in one of the
 * known source extensions, will try to create a copy in the cache with a proper
 * file name and return a reference to that file.
 */
public class RenamingScriptResourceResolver implements ResourceResolver {

	@Override
	public ResourceRef resolve(String resource) {
		ResourceRef result = null;

		// map script argument to script file
		File probe = null;
		try {
			probe = Util.getCwd().resolve(resource).normalize().toFile();
		} catch (InvalidPathException e) {
			// Ignore
		}

		try {
			if (probe != null && probe.canRead()) {
				String ext = "." + Util.extension(probe.getName());
				if (!ext.equals(".jar") && !Util.EXTENSIONS.contains(ext)) {
					if (probe.isDirectory()) {
						File defaultApp = new File(probe, "main.java");
						if (defaultApp.exists()) {
							Util.verboseMsg("Directory where main.java exists. Running main.java.");
							probe = defaultApp;
						} else {
							throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Cannot run " + probe
									+ " as it is a directory and no default application (i.e. `main.java`) found.");
						}
					}
					String original = Util.readString(probe.toPath());
					// TODO: move temp handling somewhere central
					String urlHash = Util.getStableID(original);

					if (original.startsWith("#!")) { // strip bash !# if exists
						original = original.substring(original.indexOf("\n"));
					}

					File tempFile = Settings.getCacheDir(Cache.CacheClass.scripts)
											.resolve(urlHash)
											.resolve(Util.unkebabify(probe.getName()))
											.toFile();
					tempFile.getParentFile().mkdirs();
					Util.writeString(tempFile.toPath().toAbsolutePath(), original);
					result = ResourceRef.forCachedResource(resource, tempFile);
				}
			}
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_UNEXPECTED_STATE, "Could not download " + resource, e);
		}

		return result;
	}
}
