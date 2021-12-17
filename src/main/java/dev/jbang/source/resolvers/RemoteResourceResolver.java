package dev.jbang.source.resolvers;

import static dev.jbang.util.Util.goodTrustURL;
import static dev.jbang.util.Util.swizzleURL;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.net.TrustedSources;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;
import dev.jbang.util.ConsoleInput;
import dev.jbang.util.Util;

/**
 * A <code>ResourceResolver</code> that, when given a resource string which
 * looks like a http, https or file URL, will try to download the referenced
 * document to the cache and return a reference to that file.
 */
public class RemoteResourceResolver implements ResourceResolver {
	private final Function<String, ResourceRef> urlFetcher;

	public RemoteResourceResolver(Function<String, ResourceRef> urlFetcher) {
		this.urlFetcher = urlFetcher;
	}

	@Override
	public ResourceRef resolve(String resource) {
		ResourceRef result = null;

		if (resource.startsWith("http://") || resource.startsWith("https://") || resource.startsWith("file:/")) {
			// support url's as script files
			result = urlFetcher.apply(resource);
		}

		return result;
	}

	public static ResourceRef fetchScriptFromUntrustedURL(String scriptURL) {
		try {
			java.net.URI uri = new java.net.URI(scriptURL);

			if (!TrustedSources.instance().isURLTrusted(uri)) {
				String[] options = new String[] {
						null,
						goodTrustURL(scriptURL),
						"*." + uri.getAuthority(),
						"*"
				};
				String exmsg = scriptURL
						+ " is not from a trusted source and user did not confirm trust thus aborting.\n" +
						"If you trust the url to be safe to run are here a few suggestions:\n" +
						"Limited trust:\n     jbang trust add " + options[1] + "\n" +
						"Trust all subdomains:\n    jbang trust add " + options[2] + "\n" +
						"Trust all sources (WARNING! disables url protection):\n    jbang trust add " + options[3]
						+ "\n" +
						"\nFor more control edit ~/.jbang/trusted-sources.json" + "\n";

				String question = scriptURL + " is not from a trusted source thus not running it automatically.\n\n"
						+
						"If you trust the url to be safe to run you can do one of the following:\n" +
						"0) Trust once: Add no trust, just run this time\n" +
						"1) Trust " +
						"limited url in future:\n    jbang trust add " + options[1] + "\n" +
						"\n\nAny other response will result in exit.\n";

				ConsoleInput con = new ConsoleInput(
						1,
						10,
						TimeUnit.SECONDS);
				Util.infoMsg(question);
				Util.infoMsg("Type in your choice (0 or 1) and hit enter. Times out after 10 seconds.");
				String input = con.readLine();

				boolean abort = true;
				try {
					int result = Integer.parseInt(input);
					TrustedSources ts = TrustedSources.instance();
					if (result == 0) {
						abort = false;
					} else if (result == 1) {
						ts.add(options[1], Settings.getTrustedSourcesFile().toFile());
						abort = false;
					}
				} catch (NumberFormatException ef) {
					Util.errorMsg("Could not parse answer as a number. Aborting");
				}

				if (abort)
					throw new ExitException(10, exmsg);
			}

			scriptURL = swizzleURL(scriptURL);
			Path path = Util.swizzleContent(scriptURL, Util.downloadAndCacheFile(scriptURL));

			return ResourceRef.forCachedResource(scriptURL, path.toFile());
		} catch (IOException | URISyntaxException e) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Could not download " + scriptURL, e);
		}
	}

	public static ResourceRef fetchFromURL(String scriptURL) {
		try {
			String url = swizzleURL(scriptURL);
			Path path = Util.downloadAndCacheFile(url);
			return ResourceRef.forCachedResource(scriptURL, path.toFile());
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Could not download " + scriptURL, e);
		}
	}
}
