package dev.jbang.source.resolvers;

import static dev.jbang.util.Util.getMainClass;
import static dev.jbang.util.Util.hasMainMethod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Collectors;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;
import dev.jbang.util.Util;

/**
 * A <code>ResourceResolver</code> that, when given "-" as a resource string,
 * will try to create a copy in the cache with a proper file name and return a
 * reference to that file.
 */
public class LiteralScriptResourceResolver implements ResourceResolver {
	@Override
	public ResourceRef resolve(String resource) {
		ResourceRef result = null;

		try {
			// support stdin
			if (ResourceRef.isStdin(resource)) {
				String scriptText = new BufferedReader(
						new InputStreamReader(System.in, StandardCharsets.UTF_8))
								.lines()
								.collect(Collectors.joining(
										System.lineSeparator()));

				result = stringToResourceRef(resource, scriptText);
			}
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_UNEXPECTED_STATE, "Could not cache script from stdin", e);
		}

		return result;
	}

	@Override
	public String description() {
		return "Literal stdin";
	}

	public static ResourceRef stringToResourceRef(String resource, String scriptText) throws IOException {
		ResourceRef result;
		String urlHash = Util.getStableID(scriptText);
		Path cache = Settings.getCacheDir(Cache.CacheClass.stdins).resolve(urlHash);
		cache.toFile().mkdirs();
		String basename = urlHash;
		String suffix = ".jsh";
		if (hasMainMethod(scriptText)) {
			suffix = ".java";
			basename = getMainClass(scriptText).orElse(basename);
		}
		Path scriptFile = cache.resolve(basename + suffix);
		Util.writeString(scriptFile, scriptText);
		result = ResourceRef.forCachedResource(resource, scriptFile);
		return result;
	}
}
