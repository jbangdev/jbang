package dev.jbang;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import com.google.gson.Gson;

import dev.jbang.cli.BaseCommand;

public class Util {

	public static final String JBANG_JDK_VENDOR = "JBANG_JDK_VENDOR";
	public static final String JBANG_USES_POWERSHELL = "JBANG_USES_POWERSHELL";

	public static final String SOURCES_COMMENT_PREFIX = "//SOURCES ";

	public static final Pattern patternMainMethod = Pattern.compile(
			"^.*(public\\s+static|static\\s+public)\\s+void\\s+main\\s*\\(.*",
			Pattern.MULTILINE);

	private static boolean verbose;
	private static boolean quiet;

	public static void setVerbose(boolean verbose) {
		Util.verbose = verbose;
		if (verbose) {
			setQuiet(false);
		}
	}

	public static boolean isVerbose() {
		return verbose;
	}

	public static void setQuiet(boolean quiet) {
		Util.quiet = quiet;
		if (quiet) {
			setVerbose(false);
		}
	}

	public static boolean isQuiet() {
		return quiet;
	}

	public static String kebab2camel(String name) {

		if (name.contains("-")) { // xyz-plug becomes XyzPlug
			return Arrays	.stream(name.split("\\-"))
							.map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase())
							.collect(Collectors.joining());
		} else {
			return name; // xyz stays xyz
		}
	}

	public static String getBaseName(String fileName) {
		int index = fileName.lastIndexOf('.');
		if (index == -1) {
			return kebab2camel(fileName);
		} else {
			return fileName.substring(0, index);
		}
	}

	static private boolean isPattern(String pattern) {
		return pattern.contains("?") || pattern.contains("*");
	}

	/**
	 * Explodes filepattern found in baseDir returnin list of relative Path names.
	 *
	 * TODO: this really should return some kind of abstraction of paths that allow
	 * it be portable for urls as wells as files...or have a filesystem for each...
	 * 
	 * @param source
	 * @param baseDir
	 * @param filepattern
	 * @return
	 */
	public static List<String> explode(String source, Path baseDir, String filepattern) {

		List<String> results = new ArrayList<>();

		if (Util.isURL(source)) {
			// if url then just return it back for others to resolve.
			// TODO: technically this is really where it should get resolved!
			if (isPattern(filepattern)) {
				Util.warnMsg("Pattern " + filepattern + " used while using URL to run; this could result in errors.");
				return results;
			} else {
				results.add(filepattern);
			}
		} else if (Util.isURL(filepattern)) {
			results.add(filepattern);
		} else if (!filepattern.contains("?") && !filepattern.contains("*")) {
			// no a pattern thus just as well return path directly
			results.add(filepattern);
		} else {
			// it is a non-url letls try locate it
			PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + filepattern);

			FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {
					Path relpath = baseDir.relativize(file);
					if (matcher.matches(relpath)) {
						// to avoid windows fail.
						results.add(relpath.toString().replace("\\", "/"));
					}
					return FileVisitResult.CONTINUE;
				}
			};

			try {
				Files.walkFileTree(baseDir, matcherVisitor);
			} catch (IOException e) {
				throw new ExitException(BaseCommand.EXIT_INTERNAL_ERROR,
						"Problem looking for " + filepattern + " in " + baseDir.toString(), e);
			}
		}
		return results;
	}

	/**
	 * @param name script name
	 * @return camel case of kebab string if name does not end with .java or .jsh
	 */
	public static String unkebabify(String name) {
		if (name.endsWith(".sh")) {
			name = name.substring(0, name.length() - 3);
		}
		if (!(name.endsWith(".java") || name.endsWith(".jsh"))) {
			name = kebab2camel(name) + ".java";
		}
		return name;
	}

	public enum OS {
		linux, mac, windows
	}

	public enum Arch {
		x32, x64, aarch64
	}

	public enum Vendor {
		adoptopenjdk, openjdk
	}

	static public void verboseMsg(String msg) {
		if (isVerbose()) {
			System.err.print("[jbang] ");
			System.err.println(msg);
		}
	}

	static public void verboseMsg(String msg, Throwable e) {
		if (isVerbose()) {
			System.err.print("[jbang] ");
			System.err.println(msg);
			e.printStackTrace();
		}
	}

	static public void infoMsg(String msg) {
		if (!isQuiet()) {
			System.err.print("[jbang] ");
			System.err.println(msg);
		}
	}

	static public void infoHeader() {
		if (!isQuiet()) {
			System.err.print("[jbang] ");
		}
	}

	static public void infoMsgFmt(String fmt, Object... args) {
		if (!isQuiet()) {
			System.err.print(String.format(fmt, args));
		}
	}

	static public void warnMsg(String msg) {
		if (!isQuiet()) {
			System.err.print("[jbang] [WARN] ");
			System.err.println(msg);
		}
	}

	static public void errorMsg(String msg) {
		System.err.print("[jbang] [ERROR] ");
		System.err.println(msg);
	}

	static public void errorMsg(String msg, Throwable e) {
		System.err.print("[jbang] [ERROR] ");
		if (msg != null) {
			System.err.println(msg);
		} else {
			System.err.println(e.getMessage());
		}
		if (isVerbose()) {
			e.printStackTrace();
		} else {
			if (msg != null) {
				infoMsg(e.getMessage());
			}
			infoMsg("Run with --verbose for more details");
		}
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

	private static final Pattern mainClassPattern = Pattern.compile(
			"(?sm)class *(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*) .*static void main");

	private static final Pattern publicClassPattern = Pattern.compile(
			"(?sm)public class *(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)");

	public static OS getOS() {
		String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "");
		if (os.startsWith("mac") || os.startsWith("osx")) {
			return OS.mac;
		} else if (os.startsWith("linux")) {
			return OS.linux;
		} else if (os.startsWith("win")) {
			return OS.windows;
		} else {
			throw new ExitException(EXIT_UNEXPECTED_STATE, "Unsupported Operating System: " + os);
		}
	}

	public static Arch getArch() {
		String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "");
		if (arch.matches("^(x8664|amd64|ia32e|em64t|x64)$")) {
			return Arch.x64;
		} else if (arch.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) {
			return Arch.x32;
		} else if (arch.matches("^(aarch64)$")) {
			return Arch.aarch64;
		} else {
			throw new ExitException(EXIT_UNEXPECTED_STATE, "Unsupported Architecture: " + arch);
		}
	}

	public static boolean isWindows() {
		return getOS() == OS.windows;
	}

	public static boolean isUsingPowerShell() {
		return isWindows() && "true".equalsIgnoreCase(System.getenv(JBANG_USES_POWERSHELL));
	}

	public static boolean isMac() {
		return getOS() == OS.mac;
	}

	public static Vendor getVendor() {
		String vendorName = System.getenv(JBANG_JDK_VENDOR);
		if (vendorName != null) {
			try {
				return Vendor.valueOf(vendorName);
			} catch (IllegalArgumentException ex) {
				warnMsg("JDK vendor '" + vendorName + "' does not exist, should be one of: " + Vendor.values());
			}
		}
		return Vendor.adoptopenjdk;
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
					proposedString = Parser.unescapeEntities(proposedString, true);

				}

				if (proposedString != null) {
					Matcher m = mainClassPattern.matcher(proposedString);
					Matcher pc = publicClassPattern.matcher(proposedString);

					String wantedfilename;
					if (m.find()) {
						String guessedClass = m.group(1);
						wantedfilename = guessedClass + ".java";
					} else if (pc.find()) {
						String guessedClass = pc.group(1);
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

		// to handle if kubectl-style name (i.e. extension less)
		File f = path.toFile();
		String nonkebabname = f.getName();
		if (!f.getName().endsWith(".jar")) { // avoid directly downloaded jar files getting renamed to .java
			nonkebabname = unkebabify(f.getName());
		}
		if (nonkebabname.equals(f.getName())) {
			return path;
		} else {
			File newfile = new File(f.getParent(), nonkebabname);
			if (f.renameTo(newfile)) {
				return newfile.toPath();
			} else {
				throw new IllegalStateException("Could not rename downloaded extension-less file to proper .java file");
			}
		}
	}

	static private String agent;

	static private String getAgentString() {
		if (agent == null) {
			String version = System.getProperty("java.version");
			agent = "JBang/" + BuildConfig.VERSION + " (" + System.getProperty("os.name") + "/"
					+ System.getProperty("os.version") + "/" + System.getProperty("os.arch") + ") " + "Java/" + version;
		}
		return agent;
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
		urlConnection.setRequestProperty("User-Agent", getAgentString());

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
						responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
						responseCode == 307 /* TEMP REDIRECT */) {
					if (redirects++ > 8) {
						throw new IOException("Too many redirects");
					}
					String location = httpConn.getHeaderField("Location");
					url = new URL(url, location);
					url = new URL(swizzleURL(url.toString()));
					fileURL = url.toExternalForm();
					// info("Redirecting to: " + url); // Should be debug info
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
						fileName = disposition.substring(index + 9);
						// Seems not everybody properly quotes the filename
						if (fileName.startsWith("\"") && fileName.endsWith("\"")) {
							fileName = fileName.substring(1, fileName.length() - 1);
						}
					}
				}

				if (fileName.trim().isEmpty()) {
					// extracts file name from URL if nothing found
					fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1);
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
			fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1);
		}

		// copy content from connection to file
		saveDir.mkdirs();
		Path saveFilePath = saveDir.toPath().resolve(fileName);
		try (ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
				FileOutputStream fileOutputStream = new FileOutputStream(saveFilePath.toFile())) {
			fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
		}

		if (httpConn != null)
			httpConn.disconnect();

		Util.verboseMsg(String.format("Downloaded file %s", fileURL));

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
		Path urlCache = Util.getUrlCache(fileURL);
		Path file = getFirstFile(urlCache);
		if (updateCache || file == null) {
			// create a temp directory for the downloaded content
			Path saveTmpDir = urlCache.getParent().resolve(urlCache.getFileName() + ".tmp");
			Path saveOldDir = urlCache.getParent().resolve(urlCache.getFileName() + ".old");
			try {
				Util.deletePath(saveTmpDir, true);
				Util.deletePath(saveOldDir, true);

				Path saveFilePath = downloadFile(fileURL, saveTmpDir.toFile());

				// temporarily save the old content
				if (Files.isDirectory(urlCache)) {
					Files.move(urlCache, saveOldDir);
				}
				// rename the folder to its final name
				Files.move(saveTmpDir, urlCache);
				// remove any old content
				Util.deletePath(saveOldDir, true);

				return urlCache.resolve(saveFilePath.getFileName());
			} catch (Throwable th) {
				// remove the temp folder if anything went wrong
				Util.deletePath(saveTmpDir, true);
				// and move the old content back if it exists
				if (!Files.isDirectory(urlCache) && Files.isDirectory(saveOldDir)) {
					try {
						Files.move(saveOldDir, urlCache);
					} catch (IOException ex) {
						// Ignore
					}
				}
				throw th;
			}
		} else {
			Util.verboseMsg(String.format("Retrieved file from cache %s = %s", fileURL, file));
			return urlCache.resolve(file);
		}
	}

	public static List<String> getLines(List<String> lines, String script) {
		if (lines == null && script != null) {
			lines = Arrays.asList(script.split("\\r?\\n"));
		}
		return lines;
	}

	private static Path getFirstFile(Path dir) throws IOException {
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				return files.findFirst().orElse(null);
			}
		} else {
			return null;
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
		try {
			Path file = Paths.get(filePathOrURL);
			if (Files.isRegularFile(file)) {
				return file;
			}
		} catch (InvalidPathException ex) {
			// Ignore
		}
		return downloadAndCacheFile(filePathOrURL, updateCache);
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

		if (isGistURL(url)) {
			url = extractFileFromGist(url);
		} else {
			try {
				URI u = new URI(url);
				if (u.getPath().endsWith("/")) {
					Util.verboseMsg("Directory url, assuming user want to get default application at main.java");
					url = url + "main.java";
				}
			} catch (URISyntaxException e) {
				// ignore
			}

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
		String rawURL = "";
		String[] pathPlusAnchor = url.split("#");
		String fileName = getFileNameFromGistURL(url);
		String gistapi = pathPlusAnchor[0].replaceFirst(
				"^https://gist.github.com/(([a-zA-Z0-9]*)/)?(?<gistid>[a-zA-Z0-9]*)$",
				"https://api.github.com/gists/${gistid}");

		Util.verboseMsg("Gist url api: " + gistapi);
		String strdata = null;
		try {
			strdata = readStringFromURL(gistapi, getGitHubHeaders());
		} catch (IOException e) {
			Util.verboseMsg("Error when extracting file from gist url.");
			throw new IllegalStateException(e);
		}

		Gson parser = new Gson();
		Gist gist = parser.fromJson(strdata, Gist.class);

		for (Entry<String, Map<String, String>> entry : gist.files.entrySet()) {
			String key = entry.getKey();
			String lowerCaseKey = key.toLowerCase();
			if (key.endsWith(".java") || key.endsWith(".jsh")) {
				String[] tmp = entry.getValue().get("raw_url").split("/raw/");
				String prefix = tmp[0] + "/raw/";
				String suffix = tmp[1].split("/")[1];
				String mostRecentVersionRawUrl = prefix + gist.history[0].version + "/" + suffix;
				if (!fileName.isEmpty()) { // User wants to run specific Gist file
					if ((fileName + ".java").equals(lowerCaseKey) || (fileName + ".jsh").equals(lowerCaseKey))
						return mostRecentVersionRawUrl;
				} else {
					if (key.endsWith(".jsh") || Util.hasMainMethod(entry.getValue().get("content")))
						return mostRecentVersionRawUrl;
					rawURL = mostRecentVersionRawUrl;
				}
			}
		}

		if (!fileName.isEmpty())
			throw new IllegalArgumentException("Could not find file: " + fileName);

		if (rawURL.isEmpty())
			throw new IllegalArgumentException("Gist does not contain any .java or .jsh file.");

		return rawURL;
	}

	private static Map<String, String> getGitHubHeaders() {
		if (System.getenv().containsKey("GITHUB_TOKEN")) {
			return Collections.singletonMap("Authorization", "token " + System.getenv("GITHUB_TOKEN"));
		} else {
			return Collections.emptyMap();
		}
	}

	private static String getFileNameFromGistURL(String url) {
		String fileName = "";
		String[] pathPlusAnchor = url.split("#");
		if (pathPlusAnchor.length == 2) {
			String[] anchor = pathPlusAnchor[1].split("-");
			if (anchor.length < 2)
				throw new IllegalArgumentException("Invalid Gist url: " + url);
			fileName = anchor[1];
			for (int i = 2; i < anchor.length - 1; ++i)
				fileName += "-" + anchor[i];
		}
		return fileName;
	}

	static String readStringFromURL(String requestURL, Map<String, String> headers) throws IOException {
		URLConnection connection = new URL(requestURL).openConnection();
		headers.forEach(connection::setRequestProperty);
		try (Scanner scanner = new Scanner(connection.getInputStream(),
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
		History[] history;
	}

	static class History {
		String version;
	}

	/**
	 * Runs the given command + arguments and returns its output (both stdout and
	 * stderr) as a string
	 * 
	 * @param cmd The command to execute
	 * @return The output of the command or null if anything went wrong
	 */
	public static String runCommand(String... cmd) {
		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true);
			Process p = pb.start();
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String cmdOutput = br.lines().collect(Collectors.joining());
			int exitCode = p.waitFor();
			if (exitCode == 0) {
				return cmdOutput;
			}
		} catch (IOException | InterruptedException ex) {
			// Ignore
		}
		return null;
	}

	public static boolean deletePath(Path path, boolean quiet) {
		Exception err[] = new Exception[] { null };
		try {
			if (Files.isDirectory(path)) {
				Util.verboseMsg("Deleting folder " + path);
				Files	.walk(path)
						.sorted(Comparator.reverseOrder())
						.forEach(f -> {
							try {
								Files.delete(f);
							} catch (IOException e) {
								err[0] = e;
							}
						});
			} else if (Files.exists(path)) {
				Util.verboseMsg("Deleting file " + path);
				Files.delete(path);
			}
		} catch (IOException e) {
			err[0] = e;
		}
		if (!quiet && err[0] != null) {
			throw new ExitException(-1, "Could not delete " + path.toString(), err[0]);
		}
		return err[0] == null;
	}

	public static boolean createLink(Path src, Path target) {
		if (!Files.exists(src)
				&& !createSymbolicLink(src, target.toAbsolutePath())) {
			return createHardLink(src, target.toAbsolutePath());
		} else {
			return false;
		}
	}

	private static boolean createSymbolicLink(Path src, Path target) {
		try {
			Files.createSymbolicLink(src, target);
			return true;
		} catch (IOException e) {
			infoMsg(e.toString());
		}
		infoMsg("Creation of symbolic link failed.");
		return false;
	}

	private static boolean createHardLink(Path src, Path target) {
		try {
			infoMsg("Now try creating a hard link instead of symbolic.");
			Files.createLink(src, target);
		} catch (IOException e) {
			infoMsg("Creation of hard link failed. Script must be on the same drive as $JBANG_CACHE_DIR (typically under $HOME) for hardlink creation to work. Or call the command with admin rights.");
			throw new ExitException(1, e);
		}
		return true;
	}

	public static Path getUrlCache(String fileURL) {
		String urlHash = getStableID(fileURL);
		return Settings.getCacheDir(Settings.CacheClass.urls).resolve(urlHash);
	}

	public static boolean hasMainMethod(String content) {
		return patternMainMethod.matcher(content).find();
	}

	public static boolean isGistURL(String scriptURL) {
		return scriptURL.startsWith("https://gist.github.com/");
	}

	private static List<String> collectSources(String source, Path backingPath, List<String> lines) {
		List<String> sources = new ArrayList<>();
		for (String line : lines) {
			if (!line.startsWith(Util.SOURCES_COMMENT_PREFIX))
				continue;
			String[] tmp1 = line.split("[ ;,]+");
			for (int i = 1; i < tmp1.length; ++i) {
				sources.addAll(Util	.explode(source, backingPath.getParent(), tmp1[i])
									.stream()
									.map(x -> x.toString())
									.collect(Collectors.toList()));
			}
		}
		return sources;
	}

	public static List<String> collectSources(String source, Path backingPath, String content) {
		if (content == null) {
			return Collections.emptyList();
		}
		List<String> lines = getLines(null, content);
		return collectSources(source, backingPath, lines);
	}

	public static boolean isURL(String str) {
		return str.startsWith("https://") || str.startsWith("http://")
				|| str.startsWith("file:///");
	}

	/**
	 *
	 * @param content
	 * @return the package as declared in the source file, eg: a.b.c
	 */
	public static Optional<String> getSourcePackage(String content) {
		try (Scanner sc = new Scanner(content)) {
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				if (!line.trim().startsWith("package"))
					continue;
				String[] pkgLine = line.split("package");
				if (pkgLine.length == 1)
					continue;
				String packageName = pkgLine[1];
				return Optional.of(packageName.split(";")[0].trim()); // remove ';'
			}
		}

		return Optional.empty();
	}

	/**
	 * Searches the locations defined by PATH for the given executable
	 * 
	 * @param name The name of the executable to look for
	 * @return A Path to the executable, if found, null otherwise
	 */
	public static Path searchPath(String name) {
		String envPath = System.getenv("PATH");
		envPath = envPath != null ? envPath : "";
		return Arrays	.stream(envPath.split(File.pathSeparator))
						.map(p -> Paths.get(p))
						.filter(p -> isExecutable(p.resolve(name)))
						.findFirst()
						.orElse(null);
	}

	private static boolean isExecutable(Path file) {
		if (Files.isExecutable(file)) {
			if (Util.isWindows()) {
				String nm = file.getFileName().toString().toLowerCase();
				if (nm.endsWith(".exe") || nm.endsWith(".bat") || nm.endsWith(".cmd") || nm.endsWith(".ps1")) {
					return true;
				}
			} else {
				return true;
			}
		}
		return false;
	}

	public static Path findNearestFileWith(Path dir, String fileName, Function<Path, Boolean> accept) {
		if (dir == null) {
			dir = getCwd();
		}
		while (dir != null) {
			Path catalog = dir.resolve(fileName);
			if (Files.isRegularFile(catalog) && Files.isReadable(catalog) && accept.apply(catalog)) {
				return catalog;
			}
			catalog = dir.resolve(Settings.JBANG_DOT_DIR).resolve(fileName);
			if (Files.isRegularFile(catalog) && Files.isReadable(catalog) && accept.apply(catalog)) {
				return catalog;
			}
			dir = dir.getParent();
		}
		return null;
	}

	public static Path getCwd() {
		return Paths.get("").toAbsolutePath();
	}

	/**
	 * Helper function for looking up local files according to the "nearest"
	 * principle. This means that we start from a given folder and look for a file
	 * there, second we look into the `.jbang` folder (if any) for the same file.
	 * Then we go up one folder and do the same, and so on until we reach the root
	 * of the file system. Finally we call the `mergeFile()` method in reverse order
	 * on all the files that were found. The final result is then returned from the
	 * method.
	 * 
	 * @param dir       The folder to start
	 * @param file      The file to look for
	 * @param result    The current value of the result (has to be mutable)
	 * @param mergeFile The function to call for each file that was found
	 * @param <R>       The type of the result
	 */
	public static <R> void mergeLocalFiles(Path dir, Path file, R result, BiConsumer<Path, R> mergeFile) {
		if (dir.getParent() != null) {
			mergeLocalFiles(dir.getParent(), file, result, mergeFile);
		}
		Path catalogFile = dir.resolve(Settings.JBANG_DOT_DIR).resolve(file);
		if (Files.isRegularFile(catalogFile) && Files.isReadable(catalogFile)) {
			mergeFile.accept(catalogFile, result);
		}
		catalogFile = dir.resolve(file);
		if (Files.isRegularFile(catalogFile) && Files.isReadable(catalogFile)) {
			mergeFile.accept(catalogFile, result);
		}
	}
}
