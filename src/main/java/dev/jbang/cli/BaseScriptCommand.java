package dev.jbang.cli;

import static dev.jbang.Util.swizzleURL;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import dev.jbang.AliasUtil;
import dev.jbang.ConsoleInput;
import dev.jbang.DependencyUtil;
import dev.jbang.ExitException;
import dev.jbang.GistCollection;
import dev.jbang.Script;
import dev.jbang.ScriptResource;
import dev.jbang.Settings;
import dev.jbang.TrustedSources;
import dev.jbang.Util;

import picocli.CommandLine;

public abstract class BaseScriptCommand extends BaseCommand {

	@CommandLine.Option(names = { "-o",
			"--offline" }, description = "Work offline. Fail-fast if dependencies are missing.")
	boolean offline;

	@CommandLine.Option(names = {
			"--insecure" }, description = "Enable insecure trust of all SSL certificates.", defaultValue = "false")
	boolean insecure;

	@CommandLine.Parameters(index = "0", arity = "1", description = "A file with java code or if named .jsh will be run with jshell")
	String scriptOrFile;

	@CommandLine.Parameters(index = "1..*", arity = "0..*", description = "Parameters to pass on to the script")
	List<String> userParams = new ArrayList<>();

	protected Script script;

	protected void enableInsecure() {
		try {
			// Create a trust manager that does not validate certificate chains
			TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				public void checkClientTrusted(X509Certificate[] certs, String authType) {
				}

				public void checkServerTrusted(X509Certificate[] certs, String authType) {
				}
			}
			};

			// Install the all-trusting trust manager
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

			// Create all-trusting host name verifier
			HostnameVerifier allHostsValid = new HostnameVerifier() {
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			};

			// Install the all-trusting host verifier
			HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
		} catch (NoSuchAlgorithmException | KeyManagementException ex) {
			throw new RuntimeException(ex);
		}
	}

	public static Script prepareScript(String scriptResource) throws IOException {
		return prepareScript(scriptResource, null, null, null, null);
	}

	public static Script prepareScript(String scriptResource, List<String> arguments) throws IOException {
		return prepareScript(scriptResource, arguments, null, null, null);
	}

	public static Script prepareScript(String scriptResource, List<String> arguments, Map<String, String> properties,
			List<String> dependencies, List<String> classpaths)
			throws IOException {
		ScriptResource scriptFile = getScriptFile(scriptResource);

		if (scriptFile == null) {
			// Not found as such, so let's check the aliases
			AliasUtil.Alias alias = AliasUtil.getAlias(null, scriptResource, arguments, properties);
			if (alias != null) {
				scriptFile = getScriptFile(alias.resolve(null));
				arguments = alias.arguments;
				properties = alias.properties;
				if (scriptFile == null) {
					throw new IllegalArgumentException(
							"Alias " + scriptResource + " from " + alias.aliases.catalogFile + " failed to resolve "
									+ alias.scriptRef);
				}
			}
		}

		// Support URLs as script files
		/*
		 * if(scriptResource.startsWith("http://")||scriptResource.startsWith("https://"
		 * )) { scriptFile = fetchFromURL(scriptResource)
		 *
		 * includeContext = URI(scriptResource.run { substring(lastIndexOf('/') + 1) })
		 * }
		 *
		 * // Support for support process substitution and direct script arguments
		 * if(scriptFile==null&&!scriptResource.endsWith(".kts")&&!scriptResource.
		 * endsWith(".kt")) { val scriptText = if (File(scriptResource).canRead()) {
		 * File(scriptResource).readText().trim() } else { // the last resort is to
		 * assume the input to be a java program scriptResource.trim() }
		 *
		 * scriptFile = createTmpScript(scriptText) }
		 */
		// just proceed if the script file is a regular file at this point
		if (scriptFile == null || !scriptFile.getFile().canRead()) {
			throw new ExitException(EXIT_INVALID_INPUT, "Could not read script argument " + scriptResource);
		}

		// note script file must be not null at this point

		Script s = null;
		try {
			s = new Script(scriptFile, arguments, properties);
			s.setOriginal(scriptResource);
			s.setAdditionalDependencies(dependencies);
			s.setAdditionalClasspaths(classpaths);

			List<Path> resolvedSourcePaths = new ArrayList<>();
			if (scriptFile.getResolvedSourcePaths() != null) {
				resolvedSourcePaths = scriptFile.getResolvedSourcePaths();
			}
			Set<String> visited = new HashSet<>();
			LinkedList<String> sources = new LinkedList<>();
			sources.addAll(Util.collectSources(s.getScript()));
			while (!sources.isEmpty()) {
				String source = sources.poll();
				if (!visited.add(source))
					continue;
				Path path = s.getScriptResource().fetchIfNeeded(source);
				resolvedSourcePaths.add(path);
				String sourceContent = new String(Files.readAllBytes(path), Charset.defaultCharset());
				sources.addAll(Util.collectSources(sourceContent));
			}

			s.setResolvedSources(resolvedSourcePaths);
		} catch (FileNotFoundException e) {
			throw new ExitException(1, e);
		}
		return s;
	}

	private static ScriptResource getScriptFile(String scriptResource) throws IOException {
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
			result = new ScriptResource(scriptResource, null, probe);
		} else {
			String original = Util.readString(probe.toPath());
			// TODO: move temp handling somewhere central
			String urlHash = Util.getStableID(original);

			if (original.startsWith("#!")) { // strip bash !# if exists
				original = original.substring(original.indexOf("\n"));
			}

			File tempFile = Settings.getCacheDir(Settings.CacheClass.scripts)
									.resolve(urlHash)
									.resolve(unkebabify(probe.getName()))
									.toFile();
			tempFile.getParentFile().mkdirs();
			Util.writeString(tempFile.toPath().toAbsolutePath(), original);
			scriptFile = tempFile;
			result = new ScriptResource(scriptResource, tempFile.getParentFile(), tempFile);
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
			result = new ScriptResource(scriptResource, cache, scriptFile);

		} else if (scriptResource.startsWith("http://") || scriptResource.startsWith("https://")
				|| scriptResource.startsWith("file:/")) {
			// support url's as script files
			result = fetchFromURL(scriptResource);
		} else if (DependencyUtil.looksLikeAGav(scriptResource.toString())) {
			// todo honor offline
			String gav = scriptResource.toString();
			String s = new DependencyUtil().resolveDependencies(Arrays.asList(gav),
					Collections.emptyList(), false, !Util.isQuiet(), false).getClassPath();
			result = new ScriptResource(scriptResource, null, new File(s));
		}

		return result;
	}

	/**
	 * @param name script name
	 * @return camel case of kebab string if name does not end with .java or .jsh
	 */
	static String unkebabify(String name) {
		if (name.endsWith(".sh")) {
			name = name.substring(0, name.length() - 3);
		}
		if (!(name.endsWith(".java") || name.endsWith(".jsh"))) {
			name = Util.kebab2camel(name) + ".java";
		}
		return name;
	}

	private static ScriptResource fetchFromURL(String scriptURL) {
		try {
			java.net.URI uri = new java.net.URI(scriptURL);

			if (!Settings.getTrustedSources().isURLTrusted(uri)) {
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

			if (Util.isGistURL(scriptURL)) {
				GistCollection gistCollection = Util.extractFileFromGistWithSources(scriptURL);
				File urlCache = Util.getUrlCache(gistCollection.getMainURL()).toFile();
				Path path = Util.getPathToFile(gistCollection.getMainURL(), urlCache);
				ScriptResource scriptResource = new ScriptResource(gistCollection.getMainURL(), urlCache,
						path.toFile());
				// It's disabled so //SOURCES does not become inconsistent. TODO
				// scriptResource.setResolvedSourcePaths(gistCollection.getResolvedSourcePathsAsList());
				return scriptResource;
			} else {
				scriptURL = swizzleURL(scriptURL);
				File urlCache = Util.getUrlCache(scriptURL).toFile();
				Path path = Util.downloadFileSwizzled(scriptURL, urlCache);
				return new ScriptResource(scriptURL, urlCache, path.toFile());
			}
		} catch (IOException | URISyntaxException e) {
			throw new ExitException(2, "Could not download " + scriptURL, e);
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

}
