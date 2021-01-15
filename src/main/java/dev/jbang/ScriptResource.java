package dev.jbang;

import static dev.jbang.Util.swizzleURL;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import dev.jbang.cli.BaseCommand;

public class ScriptResource implements Comparable<ScriptResource> {
	// original requested resource
	private final String originalResource;
	// cache folder it is stored inside
	private File file;

	private ScriptResource(String scriptURL, File file) {
		this.originalResource = scriptURL;
		this.file = file;
	}

	public boolean isURL() {
		return originalResource != null && Util.isURL(originalResource);
	}

	public File getFile() {
		return file;
	}

	public String getOriginalResource() {
		return originalResource;
	}

	public ScriptResource asSibling(String siblingResource) throws URISyntaxException {
		String sr;
		if (Util.isURL(siblingResource) || isURL()) {
			sr = new URI(originalResource).resolve(siblingResource).toString();
		} else {
			sr = Paths.get(originalResource).resolveSibling(siblingResource).toString();
		}
		return forTrustedResource(sr);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		ScriptResource that = (ScriptResource) o;
		return Objects.equals(originalResource, that.originalResource) &&
				Objects.equals(file, that.file);
	}

	@Override
	public int hashCode() {
		return Objects.hash(originalResource, file);
	}

	@Override
	public int compareTo(ScriptResource o) {
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

	public static ScriptResource forFile(File file) {
		return new ScriptResource(null, file);
	}

	public static ScriptResource forNamedFile(String scriptResource, File file) {
		return new ScriptResource(scriptResource, file);
	}

	public static ScriptResource forCachedResource(String scriptResource, File cachedResource) {
		return new ScriptResource(scriptResource, cachedResource);
	}

	public static ScriptResource forResource(String scriptResource) {
		try {
			return forResource(scriptResource, false);
		} catch (IOException | URISyntaxException e) {
			throw new ExitException(2, "Could not download " + scriptResource, e);
		}
	}

	public static ScriptResource forTrustedResource(String scriptResource) {
		try {
			return forResource(scriptResource, true);
		} catch (IOException | URISyntaxException e) {
			throw new ExitException(2, "Could not download " + scriptResource, e);
		}
	}

	private static ScriptResource forResource(String scriptResource, boolean knownTrusted)
			throws IOException, URISyntaxException {
		ScriptResource result = null;

		File scriptFile;
		// we need to keep track of the scripts dir or the working dir in case of stdin
		// script to correctly resolve includes
		// var includeContext = new File(".").toURI();

		// map script argument to script file
		File probe = new File(scriptResource);

		if (!probe.canRead()) {
			// not a file so let's keep the script-file undefined here
			scriptFile = null;
		} else if (probe.getName().endsWith(".jar") || probe.getName().endsWith(".java")
				|| probe.getName().endsWith(".jsh")) {
			scriptFile = probe;
			result = forNamedFile(scriptResource, probe);
		} else {
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

			File tempFile = Settings.getCacheDir(Settings.CacheClass.scripts)
									.resolve(urlHash)
									.resolve(Util.unkebabify(probe.getName()))
									.toFile();
			tempFile.getParentFile().mkdirs();
			Util.writeString(tempFile.toPath().toAbsolutePath(), original);
			scriptFile = tempFile;
			result = forCachedResource(scriptResource, tempFile);
			// if we can "just" read from script resource create tmp file
			// i.e. script input is process substitution file handle
			// not FileInputStream(this).bufferedReader().use{ readText()} does not work nor
			// does this.readText
			// includeContext = this.absoluteFile.parentFile.toURI()
			// createTmpScript(FileInputStream(this).bufferedReader().readText())
		}

		// support stdin
		if (scriptResource.equals("-") || scriptResource.equals("/dev/stdin")) {
			String scriptText = new BufferedReader(
					new InputStreamReader(System.in, StandardCharsets.UTF_8))
																				.lines()
																				.collect(Collectors.joining(
																						System.lineSeparator()));

			String urlHash = Util.getStableID(scriptText);
			File cache = Settings.getCacheDir(Settings.CacheClass.stdins).resolve(urlHash).toFile();
			cache.mkdirs();
			scriptFile = new File(cache, urlHash + ".jsh");
			Util.writeString(scriptFile.toPath(), scriptText);
			result = forCachedResource(scriptResource, scriptFile);

		} else if (scriptResource.startsWith("http://") || scriptResource.startsWith("https://")
				|| scriptResource.startsWith("file:/")) {
			// support url's as script files
			result = fetchFromURL(scriptResource, knownTrusted);
		} else if (DependencyUtil.looksLikeAGav(scriptResource.toString())) {
			// todo honor offline
			String gav = scriptResource.toString();
			String s = new DependencyUtil().resolveDependencies(Arrays.asList(gav),
					Collections.emptyList(), false, !Util.isQuiet(), false).getClassPath();
			result = forCachedResource(scriptResource, new File(s));
		}

		return result;
	}

	private static ScriptResource fetchFromURL(String scriptURL, boolean knownTrusted)
			throws IOException, URISyntaxException {
		java.net.URI uri = new java.net.URI(scriptURL);

		if (!knownTrusted && !Settings.getTrustedSources().isURLTrusted(uri)) {
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
				TrustedSources ts = Settings.getTrustedSources();
				if (result == 0) {
					abort = false;
				} else if (result == 1) {
					ts.add(options[result], Settings.getTrustedSourcesFile().toFile());
					abort = false;
				}
			} catch (NumberFormatException ef) {
				Util.errorMsg("Could not parse answer as a number. Aborting");
			}

			if (abort)
				throw new ExitException(10, exmsg);
		}

		scriptURL = swizzleURL(scriptURL);
		File urlCache = Util.getUrlCache(scriptURL).toFile();
		Path path = Util.downloadFileSwizzled(scriptURL, urlCache);

		return forCachedResource(scriptURL, path.toFile());
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
}
