package dev.jbang;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.google.gson.Gson;

public class Util {

	public static final String JBANG_CATALOG_JSON = "jbang-catalog.json";

	static public void info(String msg) {
		infoMsg(msg);
	}

	static public void infoMsg(String msg) {
		System.err.println("[jbang] " + msg);
	}

	static public void warnMsg(String msg) {
		System.err.println("[jbang] [WARN] " + msg);
	}

	static public void errorMsg(String msg) {
		System.err.println("[jbang] [ERROR] " + msg);
	}

	static public void errorMsg(String msg, Throwable t) {
		System.err.println("[jbang] [ERROR] " + msg);
		t.printStackTrace();
	}

	static public void quit(int status) {
		System.out.print(status > 0 ? "true" : "false");
		throw new ExitException(status);
	}

	/**
	 * Java 8 approximate version of Java 11 Files.readString()
	 **/
	static public String readString(Path toPath) throws IOException {
		return new String(Files.readAllBytes(toPath));
	}

	/**
	 * Java 8 approximate version of Java 11 Files.writeString()
	 **/
	static public void writeString(Path toPath, String scriptText) throws IOException {
		Files.write(toPath, scriptText.getBytes());
	}

	private static final int BUFFER_SIZE = 4096;

	private static final Pattern mainClassPattern = Pattern.compile(
			"(?sm)class *(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*) .*static void main");

	public static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("windows");
	}

	/**
	 * Download file from url but will swizzle output for sites that are known to
	 * possibly embed code (i.e. twitter and carbon)
	 * 
	 * @param fileURL
	 * @param saveDir
	 * @return
	 * @throws IOException
	 */
	public static Path downloadFileSwizzled(String fileURL, File saveDir) throws IOException {
		Path path = downloadFile(fileURL, saveDir);

		boolean twitter = fileURL.startsWith("https://mobile.twitter.com");
		if (twitter || fileURL.startsWith("https://carbon.now.sh")) { // sites known
																		// to have
																		// og:description
																		// meta name or
																		// property
			try {
				Document doc = Jsoup.parse(path.toFile(), "UTF-8", fileURL);

				String proposedString = null;
				if (twitter) {
					proposedString = doc.select("div[class=dir-ltr]").first().wholeText().trim();
				} else {
					proposedString = doc.select("meta[property=og:description],meta[name=og:description]")
										.first()
										.attr("content");
				}

				if (twitter) {
					// remove fake quotes
					// proposedString = proposedString.replace("\u201c", "");
					// proposedString = proposedString.replace("\u201d", "");
					// unescape properly
					proposedString = org.jsoup.parser.Parser.unescapeEntities(proposedString, true);

				}

				if (proposedString != null) {
					Matcher m = mainClassPattern.matcher(proposedString);
					String wantedfilename;
					if (m.find()) {
						String guessedClass = m.group(1);
						wantedfilename = guessedClass + ".java";
					} else {
						wantedfilename = path.getFileName() + ".jsh";
					}

					File f = path.toFile();
					File newFile = new File(f.getParent(), wantedfilename);
					f.renameTo(newFile);
					path = newFile.toPath();
					writeString(path, proposedString);
				}

			} catch (RuntimeException re) {
				// ignore any errors that can be caused by parsing
			}
		}

		return path;
	}

	/**
	 * Downloads a file from a URL
	 *
	 * @param fileURL HTTP URL of the file to be downloaded
	 * @param saveDir path of the directory to save the file
	 * @return Path to the downloaded file
	 * @throws IOException
	 */
	public static Path downloadFile(String fileURL, File saveDir)
			throws IOException {
		URL url = new URL(fileURL);

		URLConnection urlConnection = url.openConnection();
		HttpURLConnection httpConn = null;

		String fileName = "";

		if (urlConnection instanceof HttpURLConnection) {
			int responseCode;
			int redirects = 0;
			while (true) {
				httpConn = (HttpURLConnection) urlConnection;
				httpConn.setInstanceFollowRedirects(false);
				responseCode = httpConn.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
						responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
					if (redirects++ > 8) {
						throw new IOException("Too many redirects");
					}
					String location = URLDecoder.decode(httpConn.getHeaderField("Location"), "UTF-8");
					url = new URL(url, location);
					url = new URL(swizzleURL(url.toString()));
					fileURL = url.toExternalForm();
					info("Redirecting to: " + url);
					urlConnection = url.openConnection();
					continue;
				}
				break;
			}

			// always check HTTP response code first
			if (responseCode == HttpURLConnection.HTTP_OK) {
				String disposition = httpConn.getHeaderField("Content-Disposition");
				// String contentType = httpConn.getContentType();
				// int contentLength = httpConn.getContentLength();

				if (disposition != null) {
					// extracts file name from header field
					int index = disposition.indexOf("filename=");
					if (index > 0) {
						fileName = disposition.substring(index + 10,
								disposition.length() - 1);
					}
				}

				if (fileName == null || fileName.trim().isEmpty()) {
					// extracts file name from URL if nothing found
					fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1,
							fileURL.length());
				}

				/*
				 * System.out.println("Content-Type = " + contentType);
				 * System.out.println("Content-Disposition = " + disposition);
				 * System.out.println("Content-Length = " + contentLength);
				 * System.out.println("fileName = " + fileName);
				 */

			} else {
				throw new FileNotFoundException("No file to download. Server replied HTTP code: " + responseCode);
			}
		} else {
			fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1,
					fileURL.length());
		}

		// copy content from connection to file
		Path saveFilePath = saveDir.toPath().resolve(fileName);
		try (InputStream inputStream = urlConnection.getInputStream()) {
			Files.copy(inputStream, saveFilePath, StandardCopyOption.REPLACE_EXISTING);
		}

		if (httpConn != null)
			httpConn.disconnect();

		return saveFilePath;

	}

	/**
	 * Downloads a file from a URL and stores it in the cache. NB: The last part of
	 * the URL must contain the name of the file to be downloaded!
	 *
	 * @param fileURL     HTTP URL of the file to be downloaded
	 * @param updateCache Retrieve the file form the URL even if it already exists
	 *                    in the cache
	 * @return Path to the downloaded file
	 * @throws IOException
	 */
	public static Path downloadAndCacheFile(String fileURL, boolean updateCache) throws IOException {
		fileURL = swizzleURL(fileURL);
		String urlHash = getStableID(fileURL);
		Path urlCache = Settings.getCacheDir().resolve("url_cache_" + urlHash);
		String fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1,
				fileURL.length());
		Path file = urlCache.resolve(fileName);
		if (updateCache || !Files.isRegularFile(file)) {
			urlCache.toFile().mkdirs();
			return downloadFile(fileURL, urlCache.toFile());
		} else {
			return urlCache.resolve(fileName);
		}
	}

	/**
	 * Returns a path to the file indicated by a path or URL. In case the file is
	 * referenced by a path no action is performed and the value of updateCache is
	 * ignored. In case the file is referenced by a URL the file will be downloaded
	 * from that URL and stored in the cache. NB: In the case of URLs this only work
	 * when the last part of the URL contains the name of the file to be downloaded!
	 *
	 * @param filePathOrURL Path or URL to the file to be retrieved
	 * @param updateCache   Retrieve the file form the URL even if it already exists
	 *                      in the cache
	 * @return Path to the downloaded file
	 * @throws IOException
	 */
	public static Path obtainFile(String filePathOrURL, boolean updateCache) throws IOException {
		Path file = Paths.get(filePathOrURL);
		if (Files.isRegularFile(file)) {
			return file;
		} else {
			return downloadAndCacheFile(filePathOrURL, updateCache);
		}
	}

	public static String swizzleURL(String url) {
		url = url.replaceFirst("^https://github.com/(.*)/blob/(.*)$",
				"https://raw.githubusercontent.com/$1/$2");

		url = url.replaceFirst("^https://gitlab.com/(.*)/-/blob/(.*)$",
				"https://gitlab.com/$1/-/raw/$2");

		url = url.replaceFirst("^https://bitbucket.org/(.*)/src/(.*)$",
				"https://bitbucket.org/$1/raw/$2");

		url = url.replaceFirst("^https://twitter.com/(.*)/status/(.*)$",
				"https://mobile.twitter.com/$1/status/$2");

		if (url.startsWith("https://gist.github.com/")) {
			url = extractFileFromGist(url);
		}

		return url;
	}

	public static String getStableID(File backingFile) throws IOException {
		return getStableID(readString(backingFile.toPath()));
	}

	public static String getStableID(String input) {
		final MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new ExitException(-1, e);
		}
		final byte[] hashbytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
		StringBuilder sb = new StringBuilder();
		for (byte b : hashbytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	private static String extractFileFromGist(String url) {
		// TODO: for gist we need to be smarter when it comes to downloading as it gives
		// an invalid flag when jbang compiles

		try {
			String gistapi = url.replaceFirst("^https://gist.github.com/(([a-zA-Z0-9]*)/)?(?<gistid>[a-zA-Z0-9]*)$",
					"https://api.github.com/gists/${gistid}");
			// Util.info("looking at " + gistapi);
			String strdata = null;
			try {
				strdata = readStringFromURL(gistapi);
			} catch (IOException e) {
				// Util.info("error " + e);
				return url;
			}

			Gson parser = new Gson();

			Gist gist = parser.fromJson(strdata, Gist.class);

			// Util.info("found " + gist.files);
			final Optional<Map.Entry<String, Map<String, String>>> first = gist.files	.entrySet()
																						.stream()
																						.filter(e -> e	.getKey()
																										.endsWith(
																												".java")
																								|| e.getKey()
																									.endsWith(".jsh"))
																						.findFirst();

			if (first.isPresent()) {
				// Util.info("looking at " + first);
				return (String) first.get().getValue().getOrDefault("raw_url", url);
			} else {
				// Util.info("nothing worked!");
				return url;
			}
		} catch (RuntimeException re) {
			return url;
		}
	}

	static String readStringFromURL(String requestURL) throws IOException {
		try (Scanner scanner = new Scanner(new URL(requestURL).openStream(),
				StandardCharsets.UTF_8.toString())) {
			scanner.useDelimiter("\\A");
			return scanner.hasNext() ? scanner.next() : "";
		}
	}

	public static String repeat(String s, int times) {
		// If Java 11 becomes the default we can change this to String::repeat
		return String.join("", Collections.nCopies(times, s));
	}

	static class Gist {
		Map<String, Map<String, String>> files;
	}

	public static Settings.Alias getAlias(String ref, List<String> arguments, Map<String, String> properties)
			throws IOException {
		HashSet<String> names = new HashSet<>();
		Settings.Alias alias = new Settings.Alias(null, null, arguments, properties);
		Settings.Alias result = mergeAliases(alias, ref, names);
		return result.scriptRef != null ? result : null;
	}

	private static Settings.Alias mergeAliases(Settings.Alias a1, String ref2, HashSet<String> names)
			throws IOException {
		if (names.contains(ref2)) {
			throw new RuntimeException("Encountered alias loop on '" + ref2 + "'");
		}
		String[] parts = ref2.split("@");
		if (parts.length > 2 || parts[0].isEmpty()) {
			throw new RuntimeException("Invalid alias name '" + ref2 + "'");
		}
		Settings.Alias a2;
		if (parts.length == 1) {
			a2 = Settings.getAliases().get(ref2);
		} else {
			if (parts[1].isEmpty()) {
				throw new RuntimeException("Invalid alias name '" + ref2 + "'");
			}
			a2 = getCatalogAlias(parts[1], parts[0]);
		}
		if (a2 != null) {
			names.add(ref2);
			a2 = mergeAliases(a2, a2.scriptRef, names);
			List<String> args = a1.arguments != null ? a1.arguments : a2.arguments;
			Map<String, String> props = a1.properties != null ? a1.properties : a2.properties;
			return new Settings.Alias(a2.scriptRef, null, args, props);
		} else {
			return a1;
		}
	}

	public static Settings.Aliases getCatalogAliasesByRef(String catalogRef, boolean updateCache) throws IOException {
		if (!catalogRef.endsWith(".json")) {
			if (!catalogRef.endsWith("/")) {
				catalogRef += "/";
			}
			catalogRef += JBANG_CATALOG_JSON;
		}
		Path catalogPath = obtainFile(catalogRef, updateCache);
		Settings.Aliases aliases = Settings.getAliasesFromCatalog(catalogPath, updateCache);
		String catalogBaseRef = catalogRef.substring(0, catalogRef.lastIndexOf('/'));
		if (aliases.baseRef != null) {
			if (!aliases.baseRef.startsWith("/") && !aliases.baseRef.contains(":")) {
				aliases.baseRef = catalogBaseRef + "/" + aliases.baseRef;
			}
		} else {
			aliases.baseRef = catalogBaseRef;
		}
		return aliases;
	}

	public static Settings.Aliases getCatalogAliasesByName(String catalogName, boolean updateCache) throws IOException {
		Settings.Catalog catalog = Settings.getCatalogs().get(catalogName);
		if (catalog != null) {
			Settings.Aliases aliases = getCatalogAliasesByRef(catalog.catalogRef, false);
			return aliases;
		} else {
			throw new RuntimeException("Unknown catalog '" + catalogName + "'");
		}
	}

	public static Settings.Alias getCatalogAlias(String catalogName, String aliasName) throws IOException {
		Settings.Aliases aliases = getCatalogAliasesByName(catalogName, false);
		return getCatalogAlias(aliases, aliasName);
	}

	public static Settings.Alias getCatalogAlias(Settings.Aliases aliases, String aliasName) throws IOException {
		Settings.Alias alias = aliases.aliases.get(aliasName);
		if (alias == null) {
			throw new RuntimeException("No alias found with name '" + aliasName + "'");
		}
		if (aliases.baseRef != null && !isAbsoluteRef(alias.scriptRef)) {
			String ref = aliases.baseRef;
			if (!ref.endsWith("/")) {
				ref += "/";
			}
			if (alias.scriptRef.startsWith("./")) {
				ref += alias.scriptRef.substring(2);
			} else {
				ref += alias.scriptRef;
			}
			alias = new Settings.Alias(ref, alias.description, alias.arguments, alias.properties);
		}
		// TODO if we have to combine the baseUrl with the scriptRef
		// we need to make a copy of the Alias with the full URL
		return alias;
	}

	private static boolean isAbsoluteRef(String ref) {
		return ref.startsWith("/") || ref.contains(":");
	}
}
