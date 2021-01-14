package dev.jbang.cli;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import dev.jbang.AliasUtil;
import dev.jbang.ExitException;
import dev.jbang.FileRef;
import dev.jbang.Script;
import dev.jbang.ScriptResource;
import dev.jbang.Source;
import dev.jbang.Util;

import picocli.CommandLine;

public abstract class BaseScriptCommand extends BaseCommand {

	@CommandLine.Option(names = { "-o",
			"--offline" }, description = "Work offline. Fail-fast if dependencies are missing.")
	boolean offline;

	@CommandLine.Option(names = {
			"--insecure" }, description = "Enable insecure trust of all SSL certificates.", defaultValue = "false")
	boolean insecure;

	@CommandLine.Option(names = { "--jsh" }, description = "Force input to be interpreted with jsh/jshell")
	boolean forcejsh = false;

	@CommandLine.Parameters(index = "0", arity = "1", description = "A file with java code or if named .jsh will be run with jshell")
	String scriptOrFile;

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
			List<String> dependencies, List<String> classpaths) throws IOException {
		return prepareScript(scriptResource, arguments, properties, dependencies, classpaths, false, false);
	}

	public static Script prepareScript(String scriptResource, List<String> arguments, Map<String, String> properties,
			List<String> dependencies, List<String> classpaths, boolean fresh, boolean forcejsh)
			throws IOException {
		ScriptResource scriptFile = ScriptResource.forResource(scriptResource);

		AliasUtil.Alias alias = null;
		if (scriptFile == null) {
			// Not found as such, so let's check the aliases
			alias = AliasUtil.getAlias(null, scriptResource, arguments, properties);
			if (alias != null) {
				scriptFile = ScriptResource.forResource(alias.resolve(null));
				arguments = alias.arguments;
				properties = alias.properties;
				if (scriptFile == null) {
					throw new IllegalArgumentException(
							"Alias " + scriptResource + " from " + alias.catalog.catalogFile + " failed to resolve "
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
			s.setForcejsh(forcejsh);
			s.setOriginal(scriptResource);
			s.setAlias(alias);
			s.setAdditionalDependencies(dependencies);
			s.setAdditionalClasspaths(classpaths);
			s.setResolvedSources(resolvesourceRecursively(s, fresh));
		} catch (FileNotFoundException e) {
			throw new ExitException(1, e);
		}
		return s;
	}

	public static List<Source> resolvesourceRecursively(Script script, boolean fresh) throws IOException {
		List<Source> resolvedSourcePaths = new ArrayList<>();
		// It's important to know which sources we already visited,
		// because many files can declare the same source.
		Set<String> visited = new HashSet<>();
		// List of array: [0] original source [1] source
		List<String[]> sources = new ArrayList<>();
		// Collect sources from the entry point (main file)
		List<FileRef> fileRefs = script.collectSources();
		for (FileRef fileRef : fileRefs) {
			sources.add(new String[] { script.getScriptResource().getOriginalResource(), fileRef.getRef() });
		}

		while (!sources.isEmpty()) {
			String[] tmp = sources.remove(0);
			String originalSource = tmp[0];
			String destinationSource = tmp[1];
			destinationSource = Util.swizzleURL(destinationSource); // base path for new sources
			Path path = fetchIfNeeded(destinationSource, originalSource, fresh);
			if (!visited.add(path.toString()))
				continue;
			String sourceContent = new String(Files.readAllBytes(path), Charset.defaultCharset());
			// TODO would we not be better of with Script ref here rather than Source?
			Source source = new Source(path, Util.getSourcePackage(sourceContent));
			resolvedSourcePaths.add(source);

			String refSource;

			// If source is a URL then it must be the new base path
			if (Util.isURL(destinationSource)) {
				refSource = destinationSource;
			} else if (Util.isURL(originalSource)) {
				refSource = originalSource;
			} else { // it's file, so always use the Path that was resolved.
				refSource = path.toString();
			}

			List<String> newSources = Util.collectSources(refSource, path, sourceContent);

			for (String newSource : newSources) {
				sources.add(new String[] { refSource, newSource });
			}
		}
		return resolvedSourcePaths;
	}

	private static Path fetchIfNeeded(String resource, String originalResource, boolean fresh) {
		if (Util.isURL(resource) || Util.isURL(originalResource)) {
			try {
				URI thingToFetch = null;
				if (Util.isURL(resource)) {
					thingToFetch = new URI(resource);
				} else {
					URI includeContext = new URI(originalResource);
					thingToFetch = includeContext.resolve(resource);
				}
				return Util.downloadAndCacheFile(thingToFetch.toString(), fresh);
			} catch (URISyntaxException | IOException e) {
				throw new IllegalStateException("Could not download " + resource + " relatively to " + originalResource,
						e);
			}
		} else {
			return new File(originalResource)
												.getAbsoluteFile()
												.toPath()
												.getParent()
												.resolve(resource);
		}
	}

}
