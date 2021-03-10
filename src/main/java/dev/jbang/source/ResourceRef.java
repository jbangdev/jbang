package dev.jbang.source;

import static dev.jbang.util.Util.swizzleURL;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.net.TrustedSources;
import dev.jbang.util.ConsoleInput;
import dev.jbang.util.Util;

public class ResourceRef implements Comparable<ResourceRef> {
	// original requested resource
	private final String originalResource;
	// cache folder it is stored inside
	private final File file;

	private ResourceRef(String ref, File file) {
		this.originalResource = ref;
		this.file = file;
	}

	public boolean isURL() {
		return originalResource != null && Util.isURL(originalResource);
	}

	public boolean isClasspath() {
		return originalResource != null && Util.isClassPathRef(originalResource);
	}

	public File getFile() {
		return file;
	}

	public String getOriginalResource() {
		return originalResource;
	}

	public ResourceRef asSibling(String siblingResource) {
		String sr;
		try {
			if (Util.isURL(siblingResource)) {
				sr = new URI(siblingResource).toString();
			} else if (isURL()) {
				sr = new URI(originalResource).resolve(siblingResource).toString();
			} else if (Util.isClassPathRef(siblingResource)) {
				sr = siblingResource;
			} else if (isClasspath()) {
				sr = Paths.get(originalResource.substring(11)).resolveSibling(siblingResource).toString();
				sr = "classpath:" + sr;
			} else {
				sr = Paths.get(originalResource).resolveSibling(siblingResource).toString();
			}
			ResourceRef result = forTrustedResource(sr);
			if (result == null) {
				throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Could not find " + siblingResource);
			}
			return result;
		} catch (URISyntaxException e) {
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR, e);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		ResourceRef that = (ResourceRef) o;
		return Objects.equals(originalResource, that.originalResource) &&
				Objects.equals(file, that.file);
	}

	@Override
	public int hashCode() {
		return Objects.hash(originalResource, file);
	}

	@Override
	public int compareTo(ResourceRef o) {
		if (o == null) {
			return 1;
		}
		return toString().compareTo(o.toString());
	}

	@Override
	public String toString() {
		if (originalResource != null && file != null) {
			if (originalResource.equals(file.getPath())) {
				return originalResource;
			} else {
				return originalResource + " (cached as: " + file.getPath() + ")";
			}
		} else {
			String res = "";
			if (originalResource != null) {
				res += originalResource;
			}
			if (file != null) {
				res += file.getPath();
			}
			return res;
		}
	}

	public static ResourceRef forFile(File file) {
		return new ResourceRef(null, file);
	}

	public static ResourceRef forNamedFile(String scriptResource, File file) {
		return new ResourceRef(scriptResource, file);
	}

	public static ResourceRef forCachedResource(String scriptResource, File cachedResource) {
		return new ResourceRef(scriptResource, cachedResource);
	}

	public static ResourceRef forScriptResource(String scriptResource) {
		ResourceRef result = forScript(scriptResource);
		if (result == null) {
			result = forResource(scriptResource, ResourceRef::fetchScriptFromUntrustedURL);
		}
		return result;
	}

	public static ResourceRef forResource(String scriptResource) {
		return forResource(scriptResource, ResourceRef::fetchFromURL);
	}

	public static ResourceRef forTrustedResource(String scriptResource) {
		return forResource(scriptResource, ResourceRef::fetchFromURL);
	}

	private static ResourceRef forScript(String scriptResource) {
		ResourceRef result = null;

		// map script argument to script file
		File probe = Util.getCwd().resolve(scriptResource).normalize().toFile();

		try {
			if (probe.canRead()) {
				if (probe.canRead() && !probe.getName().endsWith(".jar") && !probe.getName().endsWith(".java")
						&& !probe.getName().endsWith(".jsh")) {
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
					result = forCachedResource(scriptResource, tempFile);
				}
			} else {
				// support stdin
				if (scriptResource.equals("-") || scriptResource.equals("/dev/stdin")) {
					String scriptText = new BufferedReader(
							new InputStreamReader(System.in, StandardCharsets.UTF_8))
																						.lines()
																						.collect(Collectors.joining(
																								System.lineSeparator()));

					String urlHash = Util.getStableID(scriptText);
					File cache = Settings.getCacheDir(Cache.CacheClass.stdins).resolve(urlHash).toFile();
					cache.mkdirs();
					File scriptFile = new File(cache, urlHash + ".jsh");
					Util.writeString(scriptFile.toPath(), scriptText);
					result = forCachedResource(scriptResource, scriptFile);
				}
			}
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_UNEXPECTED_STATE, "Could not download " + scriptResource, e);
		}

		return result;
	}

	private static ResourceRef forResource(String scriptResource, Function<String, ResourceRef> urlFetcher) {
		ResourceRef result = null;

		// map script argument to script file
		File probe = Util.getCwd().resolve(scriptResource).normalize().toFile();

		if (probe.canRead()) {
			result = forNamedFile(scriptResource, probe);
		} else if (scriptResource.startsWith("http://") || scriptResource.startsWith("https://")
				|| scriptResource.startsWith("file:/")) {
			// support url's as script files
			result = urlFetcher.apply(scriptResource);
		} else if (scriptResource.startsWith("classpath:/")) {
			result = getClasspathResource(scriptResource);
		} else if (DependencyUtil.looksLikeAGav(scriptResource)) {
			// todo honor offline
			String gav = scriptResource;
			String s = new DependencyUtil().resolveDependencies(Collections.singletonList(gav),
					Collections.emptyList(), Util.isOffline(), Util.isFresh(), !Util.isQuiet(), false).getClassPath();
			result = forCachedResource(scriptResource, new File(s));
		}

		return result;
	}

	private static ResourceRef fetchScriptFromUntrustedURL(String scriptURL) {
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
						"Limited trust:\n    jbang trust add " + options[1] + "\n" +
						"Trust all subdomains:\n    jbang trust add " + options[2] + "\n" +
						"Trust all sources (WARNING! disables url protection):\n    jbang trust add " + options[3]
						+ "\n" +
						"\nFor more control edit ~/.jbang/trusted-sources.json" + "\n";

				String question = scriptURL + " is not from a trusted source thus not running it automatically.\n\n" +
						"If you trust the url to be safe to run you can do one of the following:\n" +
						"0) Trust once: Add no trust, just run this time\n" +
						"1) Trust this url in future:\n    jbang trust add " + options[1] + "\n" +
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

			return forCachedResource(scriptURL, path.toFile());
		} catch (IOException | URISyntaxException e) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Could not download " + scriptURL, e);
		}
	}

	private static ResourceRef fetchFromURL(String scriptURL) {
		try {
			scriptURL = swizzleURL(scriptURL);
			Path path = Util.downloadAndCacheFile(scriptURL);
			return forCachedResource(scriptURL, path.toFile());
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Could not download " + scriptURL, e);
		}
	}

	private static String goodTrustURL(String url) {
		String originalUrl = url;

		url = url.replaceFirst("^https://gist.github.com/(.*)?/(.*)$",
				"https://gist.github.com/$1/");

		url = url.replaceFirst("^https://github.com/(.*)/blob/(.*)$",
				"https://github.com/$1/");

		url = url.replaceFirst("^https://gitlab.com/(.*)/-/blob/(.*)$",
				"https://gitlab.com/$1/");

		url = url.replaceFirst("^https://bitbucket.org/(.*)/src/(.*)$",
				"https://bitbucket.org/$1/");

		url = url.replaceFirst("^https://twitter.com/(.*)/status/(.*)$",
				"https://twitter.com/$1/");

		if (url.equals(originalUrl)) {
			java.net.URI u = null;
			try {
				u = new java.net.URI(url);
			} catch (URISyntaxException e) {
				return url;
			}
			url = u.getScheme() + "://" + u.getAuthority();
		}

		return url;
	}

	private static ResourceRef getClasspathResource(String cpResource) {
		String ref = cpResource.substring(11);
		Util.verboseMsg("Duplicating classpath resource " + ref);
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (cl == null) {
			cl = ResourceRef.class.getClassLoader();
		}
		URL url = cl.getResource(ref);
		if (url == null) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Resource not found on class path: " + ref);
		}

		try {
			File f = new File(url.toURI());
			if (f.canRead()) {
				return forCachedResource(cpResource, f);
			}
		} catch (URISyntaxException | IllegalArgumentException e) {
			// Ignore
		}

		// We couldn't read the file directly from the class path so let's make a copy
		try (InputStream is = url.openStream()) {
			Path to = Util.getUrlCache(cpResource);
			Files.createDirectories(to.getParent());
			Files.copy(is, to, StandardCopyOption.REPLACE_EXISTING);
			return forCachedResource(cpResource, to.toFile());
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR,
					"Resource could not be copied from class path: " + ref, e);
		}
	}
}
