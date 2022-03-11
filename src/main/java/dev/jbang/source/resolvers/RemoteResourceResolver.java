package dev.jbang.source.resolvers;

import static dev.jbang.util.Util.goodTrustURL;
import static dev.jbang.util.Util.swizzleURL;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.net.TrustedSources;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;
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

			String swizzledUrl = swizzleURL(scriptURL);

			if (!Util.isFileCached(swizzledUrl) && !TrustedSources.instance().isURLTrusted(uri)) {
				String question = scriptURL + " is not from a trusted source thus not running it automatically.\n" +
						"\n" +
						"If you trust the url to be safe to run you can do one of the following";

				String trustUrl = goodTrustURL(scriptURL);
				String trustOrgUrl = orgURL(trustUrl);
				List<String> options = new ArrayList<>();
				options.add(
						"Trust once: Add no trust, just download this time (can be run multiple times while cached)");
				options.add("Trust limited url in future: " + trustUrl);
				if (trustOrgUrl != null) {
					options.add("Trust organization url in future: " + trustOrgUrl);
				}

				int result = Util.askInput(question, 30, 0, options.toArray(new String[] {}));
				TrustedSources ts = TrustedSources.instance();
				if (result == 2) {
					ts.add(trustUrl, Settings.getTrustedSourcesFile().toFile());
				} else if (result == 3) {
					ts.add(trustOrgUrl, Settings.getTrustedSourcesFile().toFile());
				} else if (result <= 0) {
					String exmsg = scriptURL
							+ " is not from a trusted source and user did not confirm trust thus aborting.\n" +
							"If you trust the url to be safe to run are here a few suggestions:\n" +
							"Limited trust:\n     jbang trust add " + trustUrl + "\n";
					if (trustOrgUrl != null) {
						exmsg += "Organization trust:\n     jbang trust add " + trustOrgUrl + "\n";
					}
					exmsg += "Trust all subdomains:\n    jbang trust add *." + uri.getAuthority() + "\n" +
							"Trust all sources (WARNING! disables url protection):\n    jbang trust add *" +
							"\n" +
							"\nFor more control edit ~/.jbang/trusted-sources.json" + "\n";
					throw new ExitException(10, exmsg);
				}
			}

			Path path = Util.swizzleContent(swizzledUrl, Util.downloadAndCacheFile(swizzledUrl));

			return ResourceRef.forCachedResource(scriptURL, path.toFile());
		} catch (IOException | URISyntaxException e) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Could not download " + scriptURL, e);
		}
	}

	private static String orgURL(String trustUrl) {
		String url = trustUrl;
		url = url.replaceFirst("^https://github.com/(.*)/(.*)/$",
				"https://github.com/$1/");

		url = url.replaceFirst("^https://gitlab.com/(.*)/(.*)/$",
				"https://gitlab.com/$1/");

		url = url.replaceFirst("^https://bitbucket.org/(.*)/(.*)/$",
				"https://bitbucket.org/$1/");
		return trustUrl.equals(url) ? null : url;
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
