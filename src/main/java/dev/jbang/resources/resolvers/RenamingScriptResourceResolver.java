package dev.jbang.resources.resolvers;

import static dev.jbang.util.Util.MAIN_JAVA;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.NonNull;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;
import dev.jbang.source.Project;
import dev.jbang.source.Source;
import dev.jbang.util.Util;

/**
 * A <code>ResourceResolver</code> that, when given a resource string which
 * looks like a path to a file on the local file system not ending in one of the
 * known source extensions, will try to create a copy in the cache with a proper
 * file name and return a reference to that file.
 */
public class RenamingScriptResourceResolver implements ResourceResolver {
	private Source.Type forceType;

	public RenamingScriptResourceResolver(Source.Type forceType) {
		this.forceType = forceType;
	}

	@NonNull
	@Override
	public String description() {
		return "Renaming resolver";
	}

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
				List<String> knownExtensions = forceType != null ? Collections.singletonList(forceType.extension)
						: Source.Type.extensions();
				String ext = Util.extension(probe.getName());
				if (!ext.equals("jar")
						&& !knownExtensions.contains(ext)
						&& (!Util.isPreview() || !Project.BuildFile.fileNames().contains(probe.getName()))) {
					if (probe.isDirectory()) {
						File defaultApp = new File(probe, MAIN_JAVA);
						File buildFile = new File(probe, Project.BuildFile.jbang.fileName);
						if (Util.isPreview() && buildFile.exists()) {
							Util.verboseMsg("Directory where build.jbang exists. Running build.jbang.");
							return ResourceRef.forFile(buildFile.toPath());
						} else if (defaultApp.exists()) {
							Util.verboseMsg("Directory where main.java exists. Running main.java.");
							probe = defaultApp;
						} else {
							String msg;
							if (Util.isPreview()) {
								msg = "Cannot run " + probe
										+ " as it is a directory and no build file (i.e. `build.jbang`) or default application (i.e. `main.java`) was found.";
							} else {
								msg = "Cannot run " + probe
										+ " as it is a directory and no default application (i.e. `main.java`) was found.";
							}
							throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, msg);
						}
					}
					String original = Util.readString(probe.toPath());
					// TODO: move temp handling somewhere central
					String urlHash = Util.getStableID(original);

					if (original.startsWith("#!")) { // strip bash !# if exists
						original = original.substring(original.indexOf("\n"));
					}

					String name;
					if (forceType != null) {
						// Remove existing extension and add the forced extension
						String baseName = Util.sourceBase(probe.getName());
						if (forceType == Source.Type.java) {
							baseName = Util.toJavaIdentifier(baseName);
						}
						name = baseName + "." + forceType.extension;
					} else {
						name = probe.getName();
					}
					Path tempFile = Settings.getCacheDir(Cache.CacheClass.scripts)
						.resolve(urlHash)
						.resolve(Util.unkebabify(name));
					tempFile.getParent().toFile().mkdirs();
					Util.writeString(tempFile.toAbsolutePath(), original);
					result = ResourceRef.forResolvedResource(resource, tempFile);
				}
			}
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_UNEXPECTED_STATE, "Could not download " + resource, e);
		}

		return result;
	}
}
