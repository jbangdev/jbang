package dev.jbang.resources.resolvers;

import static dev.jbang.util.Util.goodTrustURL;
import static dev.jbang.util.Util.swizzleURL;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.cli.ExitException;
import dev.jbang.net.TrustedSources;
import dev.jbang.resources.ResourceNotFoundException;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;
import dev.jbang.util.NetUtil;
import dev.jbang.util.Util;

/**
 * A <code>ResourceResolver</code> that, when given a resource string which
 * looks like a http, https or file URL, will try to download the referenced
 * document to the cache and return a reference to that file.
 */
public class RemoteResourceResolver implements ResourceResolver {
	private final boolean alwaysTrust;

	public RemoteResourceResolver(boolean alwaysTrust) {
		this.alwaysTrust = alwaysTrust;
	}

	@NonNull
	@Override
	public String description() {
		return String.format("%s remote resource", alwaysTrust ? "Trusted" : "Non-trusted");
	}

	@Override
	public ResourceRef resolve(String resource, boolean trusted) {
		ResourceRef result = null;

		if (resource.startsWith("http://") || resource.startsWith("https://") || resource.startsWith("file:/")) {
			result = new RemoteResourceRef(resource,
					ref -> {
						try {
							return alwaysTrust || trusted ? fetchFromURL(ref) : fetchScriptFromUntrustedURL(ref);
						} catch (IOException | URISyntaxException e) {
							throw new ResourceNotFoundException(resource, "Could not download " + ref, e);
						}
					}, this);
		}

		return result;
	}

	public static Path fetchScriptFromUntrustedURL(String scriptURL) throws IOException, URISyntaxException {
		java.net.URI uri = new java.net.URI(scriptURL);

		String swizzledUrl = swizzleURL(scriptURL);

		if (!NetUtil.isFileCached(swizzledUrl) && !TrustedSources.instance().isURLTrusted(uri)) {
			String question = scriptURL + " is not from a trusted source thus not running it automatically.\n" +
					"\n" +
					"If you trust the url to be safe to run you can do one of the following";

			String trustUrl = goodTrustURL(scriptURL);
			String trustOrgUrl = orgURL(trustUrl);
			List<String> options = new ArrayList<>();
			options.add(
					"Trust once: Add no trust, only allow access to this URL for the duration of this run");
			options.add("Trust limited url in future: " + trustUrl);
			if (trustOrgUrl != null) {
				options.add("Trust organization url in future: " + trustOrgUrl);
			}

			int result = Util.askInput(question, 30, 0, options.toArray(new String[] {}));
			TrustedSources ts = TrustedSources.instance();
			if (result == 1) {
				ts.addTemporary(trustUrl);
			} else if (result == 2) {
				ts.add(trustUrl);
			} else if (result == 3) {
				ts.add(trustOrgUrl);
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

		return Util.swizzleContent(swizzledUrl, NetUtil.downloadAndCacheFile(swizzledUrl));
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

	public static Path fetchFromURL(String scriptURL) throws IOException {
		String url = swizzleURL(scriptURL);
		return NetUtil.downloadAndCacheFile(url);
	}

	public static class RemoteResourceRef extends FileResourceResolver.FileResourceRef {

		public RemoteResourceRef(@NonNull String resource, @NonNull Function<String, Path> obtainer,
				@Nullable ResourceResolver resolver) {
			super(resource, obtainer, resolver);
		}

		@Override
		public @Nullable ResourceRef resolve(String resource, boolean trusted) {
			try {
				String sibRef = Util.isURL(resource) ? resource
						: new URI(Util.swizzleURL(originalResource)).resolve(resource).toString();
				return resolver.resolve(sibRef, trusted);
			} catch (URISyntaxException e) {
				throw new ResourceNotFoundException(resource, "Syntax error when trying to find " + resource, e);
			}
		}
	}
}
