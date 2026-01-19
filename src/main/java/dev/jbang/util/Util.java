package dev.jbang.util;

import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jspecify.annotations.NonNull;

import com.google.gson.Gson;

import dev.jbang.Configuration;
import dev.jbang.Settings;
import dev.jbang.catalog.Catalog;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.DependencyResolver;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.ModularClassPath;
import dev.jbang.devkitman.util.OsUtils;
import dev.jbang.devkitman.util.OsUtils.Arch;
import dev.jbang.devkitman.util.OsUtils.OS;
import dev.jbang.source.Source;

public class Util {

	public static final String JBANG_JDK_VENDOR = "JBANG_JDK_VENDOR";
	public static final String JBANG_RUNTIME_SHELL = "JBANG_RUNTIME_SHELL";
	public static final String JBANG_STDIN_NOTTY = "JBANG_STDIN_NOTTY";
	public static final String JBANG_PREFER_GUI = "JBANG_PREFER_GUI";
	private static final String JBANG_DOWNLOAD_SOURCES = "JBANG_DOWNLOAD_SOURCES";

	public static final Pattern patternMainMethod = Pattern.compile(
			"^.*(public\\s+static|static\\s+public)\\s+void\\s+main\\s*\\(.*|void\\s+main\\s*\\(\\)",
			Pattern.MULTILINE);

	public static final Pattern mainClassMethod = Pattern.compile(
			"(?<=\\n|\\A)(?:public\\s)\\s*(class)\\s*([^\\n\\s]*)");

	public static final Pattern patternFQCN = Pattern.compile(
			"^([a-z][a-z0-9]*\\.)*[a-zA-Z][a-zA-Z0-9_]*$");

	public static final Pattern patternModuleId = Pattern.compile(
			"^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$");

	private static final Pattern subUrlPattern = Pattern.compile(
			"^(%?%https?://.+$)|(%?%\\{[a-z]+:[^}]+})");

	public static final String MAIN_JAVA = "main.java";

	private static boolean verbose;
	private static boolean quiet;
	private static boolean offline;
	private static boolean fresh;
	private static boolean ignoreTransitiveRepositories;
	private static boolean preview;

	private static Path cwd;
	private static Boolean downloadSources;
	private static Instant startTime = Instant.now();

	public static void setVerbose(boolean verbose) {
		Util.verbose = verbose;
		if (verbose) {
			setQuiet(false);
		}
		Logger parent = Logger.getLogger("dev.jbang");
		parent.setLevel(verbose ? java.util.logging.Level.FINE : java.util.logging.Level.INFO);
	}

	public static boolean isVerbose() {
		return verbose;
	}

	public static void setQuiet(boolean quiet) {
		Util.quiet = quiet;
		if (quiet) {
			setVerbose(false);
		}
		LogManager.getLogManager()
			.getLogger("")
			.setLevel(quiet ? java.util.logging.Level.WARNING : java.util.logging.Level.INFO);
	}

	public static boolean isQuiet() {
		return quiet;
	}

	public static void setOffline(boolean offline) {
		Util.offline = offline;
		if (offline) {
			setFresh(false);
		}
	}

	public static void setIgnoreTransitiveRepositories(boolean ignoreTransitiveRepositories) {
		Util.ignoreTransitiveRepositories = ignoreTransitiveRepositories;
	}

	public static void setDownloadSources(boolean flag) {
		downloadSources = flag;
	}

	public static boolean downloadSources() {
		if (downloadSources == null) {
			downloadSources = Boolean.valueOf(System.getenv(JBANG_DOWNLOAD_SOURCES));
		}
		return downloadSources;
	}

	public static boolean isOffline() {
		return offline;
	}

	public static boolean isIgnoreTransitiveRepositories() {
		return ignoreTransitiveRepositories;
	}

	public static void setFresh(boolean fresh) {
		Util.fresh = fresh;
		if (fresh) {
			setOffline(false);
		}
	}

	public static boolean isPreview() {
		return preview;
	}

	public static void setPreview(boolean preview) {
		Util.preview = preview;
	}

	public static boolean isFresh() {
		return fresh;
	}

	public static Path getCwd() {
		return cwd != null ? cwd : Paths.get("").toAbsolutePath();
	}

	public static void setCwd(Path cwd) {
		Util.cwd = cwd.toAbsolutePath().normalize();
	}

	/**
	 * Runs the given code with the global <code>fresh</code> variable set to
	 * <code>true</code>, thereby forcing that all requests to remote resources are
	 * actually performed and any previously cached documents are updated to their
	 * latest versions.
	 */
	public static <T> T freshly(Callable<T> func) {
		boolean oldFresh = isFresh();
		setFresh(true);
		try {
			return func.call();
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			setFresh(oldFresh);
		}
	}

	/**
	 * Runs the given code with the "cache-evict" configuration key set to a very
	 * low value. This forces all requests to remote resources to be performed.
	 * Cached resources will only be updated if the remote documents have actually
	 * been updated.
	 */
	public static <T> T withCacheEvict(Callable<T> func) {
		return withCacheEvict("5", func);
	}

	public static <T> T withCacheEvict(String val, Callable<T> func) {
		return withConfig(Settings.CONFIG_CACHE_EVICT, val, func);
	}

	public static <T> T withConfig(String key, String value, Callable<T> func) {
		Configuration cfg = Configuration.create(Configuration.instance());
		cfg.put(key, value);
		return withConfig(cfg, func);
	}

	public static <T> T withConfig(Configuration cfg, Callable<T> func) {
		Configuration oldCfg = Configuration.instance();
		Configuration.instance(cfg);
		try {
			return func.call();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			Configuration.instance(oldCfg);
		}
	}

	public static String kebab2camel(String name) {

		if (name.contains("-")) { // xyz-plug becomes XyzPlug
			return Arrays.stream(name.split("-"))
				.map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase())
				.collect(Collectors.joining());
		} else {
			return name; // xyz stays xyz
		}
	}

	public static String getBaseName(String fileName) {
		String ext = extension(fileName);
		if (ext.isEmpty()) {
			return kebab2camel(fileName);
		} else {
			return base(fileName);
		}
	}

	/**
	 * Returns the name without extension. Will return the name itself if it has no
	 * extension
	 * 
	 * @param name A file name
	 * @return A name without extension
	 */
	public static String base(String name) {
		int p = name.lastIndexOf('.');
		return p > 0 ? name.substring(0, p) : name;
	}

	/**
	 * Returns the name without a valid source file extension. Will return the name
	 * itself if it has no extension or if the extension is not a valid source file
	 * extension.
	 * 
	 * @param name A file name
	 * @return A name without a source file extension
	 */
	public static String sourceBase(String name) {
		for (String extension : Source.Type.extensions()) {
			if (name.endsWith("." + extension)) {
				return base(name);
			}
		}
		return name;
	}

	/**
	 * Returns the extension of the given file name. The extension will not include
	 * the dot as part of the result. Returns an empty string if the name has no
	 * extension.
	 * 
	 * @param name A file name
	 * @return An extension or an empty string
	 */
	public static String extension(String name) {
		int p = name.lastIndexOf('.');
		return p > 0 ? name.substring(p + 1) : "";
	}

	static public boolean isPattern(String pattern) {
		return pattern.contains("?") || pattern.contains("*");
	}

	/**
	 * Explodes filePattern found in baseDir returning list of relative Path names.
	 * If the filePattern refers to an existing folder the filePattern will be
	 * treated as if it ended in "/**".
	 */
	public static List<String> explode(String source, Path baseDir, String filePattern) {
		if (source != null && isURL(source)) {
			// if url then just return it back for others to resolve.
			// TODO: technically this is really where it should get resolved!
			if (isPattern(filePattern)) {
				warnMsg("Pattern " + filePattern + " used while using URL to run; this could result in errors.");
				return Collections.emptyList();
			} else {
				return Collections.singletonList(filePattern);
			}
		} else if (isURL(filePattern)) {
			return Collections.singletonList(filePattern);
		}

		if (!isPattern(filePattern)) {
			if (!Catalog.isValidCatalogReference(filePattern)
					&& isValidPath(filePattern) && Files.isDirectory(baseDir.resolve(filePattern))) {
				// The filePattern refers to a folder, so let's add "/**"
				if (!filePattern.endsWith("/") && !filePattern.endsWith(File.separator)) {
					filePattern = filePattern + "/";
				}
				filePattern = filePattern + "**";
			} else {
				// not a pattern and not a folder thus just as well return path directly
				return Collections.singletonList(filePattern);
			}
		}

		// it is a non-url let's try to locate it
		final Path bd;
		final boolean useAbsPath;
		Path base = basePathWithoutPattern(filePattern);
		String fp;
		if (base.isAbsolute()) {
			bd = base;
			fp = filePattern.substring(bd.toString().length() + 1);
			useAbsPath = true;
		} else {
			bd = baseDir.resolve(base);
			fp = base.toString().isEmpty() ? filePattern : filePattern.substring(base.toString().length() + 1);
			useAbsPath = false;
		}
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + fp);

		List<String> results = new ArrayList<>();
		FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {
				Path relpath = bd.relativize(file);
				if (matcher.matches(relpath)) {
					// to avoid windows fail.
					if (file.toFile().exists()) {
						Path p = useAbsPath ? file : base.resolve(relpath);
						if (isWindows()) {
							results.add(p.toString().replace("\\", "/"));
						} else {
							results.add(p.toString());
						}
					} else {
						verboseMsg("Warning: " + relpath + " matches but does not exist!");
					}
				}
				return FileVisitResult.CONTINUE;
			}
		};

		try {
			Files.walkFileTree(bd, matcherVisitor);
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_INTERNAL_ERROR,
					"Problem looking for " + fp + " in " + bd + ": " + e, e);
		}

		return results;
	}

	public static Path basePathWithoutPattern(String path) {
		int p1 = path.indexOf('?');
		int p2 = path.indexOf('*');
		int pp = p1 < 0 ? p2 : (p2 < 0 ? p1 : Math.min(p1, p2));
		if (pp >= 0) {
			String npath = isWindows() ? path.replace('\\', '/') : path;
			int ps = npath.lastIndexOf('/', pp);
			if (ps >= 0) {
				return Paths.get(path.substring(0, ps + 1));
			} else {
				return Paths.get("");
			}
		} else {
			return Paths.get(path);
		}
	}

	/**
	 * @param name script name
	 * @return camel case of kebab string if name does not end with .java or .jsh
	 */
	public static String unkebabify(String name) {
		if (name.endsWith(".sh")) {
			name = name.substring(0, name.length() - 3);
		}
		boolean valid = false;
		for (String extension : Source.Type.extensions()) {
			valid |= name.endsWith("." + extension);
		}
		if (!valid) {
			name = kebab2camel(name) + ".java";
		}
		return name;
	}

	public enum Shell {
		bash, cmd, powershell
	}

	static public void verboseMsg(String msg) {
		if (isVerbose()) {
			System.err.print(getMsgHeader());
			System.err.println(msg);
		}
	}

	static public void verboseMsg(String msg, Throwable e) {
		if (isVerbose()) {
			System.err.print(getMsgHeader());
			System.err.println(msg);
			e.printStackTrace();
		}
	}

	static public void infoMsg(String msg) {
		if (!isQuiet()) {
			System.err.print(getMsgHeader());
			System.err.println(msg);
		}
	}

	static public void warnMsg(String msg) {
		if (!isQuiet()) {
			System.err.print(getMsgHeader());
			System.err.print("[WARN] ");
			System.err.println(msg);
		}
	}

	static public void warnMsg(String msg, Throwable verboseInfo) {
		if (!isQuiet()) {
			System.err.print(getMsgHeader());
			System.err.print("[WARN] ");
			System.err.println(msg);
			if (isVerbose()) {
				verboseInfo.printStackTrace();
			}
		}
	}

	static public void errorMsg(String msg) {
		System.err.print(getMsgHeader());
		System.err.print("[ERROR] ");
		System.err.println(msg);
	}

	static public void errorMsg(String msg, Throwable e) {
		System.err.print(getMsgHeader());
		System.err.print("[ERROR] ");
		if (msg != null) {
			System.err.println(msg);
		} else if (e.getMessage() != null) {
			System.err.println(e.getMessage());
		} else {
			System.err.println(e.getClass().toGenericString());
		}
		if (isVerbose()) {
			e.printStackTrace();
		} else {
			if (msg != null) {
				infoMsg(e.getMessage());
			}
			infoMsg("Run with --verbose for more details. The --verbose must be placed before the jbang command. I.e. jbang --verbose run [...]");
		}
	}

	static public String getMsgHeader() {
		if (isVerbose()) {
			Duration d = Duration.between(startTime, Instant.now());
			long s = d.getSeconds();
			long n = d.minus(s, ChronoUnit.SECONDS).toMillis();
			return String.format("[jbang] [%d:%03d] ", s, n);
		} else {
			return "[jbang] ";
		}
	}

	static public void quit(int status) {
		System.out.print(status > 0 ? "true" : "false");
		throw new ExitException(status);
	}

	static public String readFileContent(Path file) {
		try {
			return readString(file);
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_UNEXPECTED_STATE, "Could not read content for " + file, e);
		}
	}

	/**
	 * Java 8 approximate version of Java 11 Files.readString()
	 **/
	static public String readString(Path toPath) throws IOException {
		return new String(Files.readAllBytes(toPath));
	}

	static public String readString(InputStream is) {
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		return br.lines().collect(Collectors.joining("\n"));
	}

	static public Stream<String> stringLines(String text) {
		return Arrays.stream(text.split("\\r?\\n"));
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
		return OsUtils.getOS();
	}

	public static Arch getArch() {
		return OsUtils.getArch();
	}

	public static boolean isWindows() {
		return OsUtils.isWindows();
	}

	public static Shell getShell() {
		try {
			return Shell.valueOf(System.getenv(JBANG_RUNTIME_SHELL));
		} catch (IllegalArgumentException | NullPointerException ex) {
			// We'll just hope for the best
			return isWindows() ? Shell.powershell : Shell.bash;
		}
	}

	public static boolean isMac() {
		return OsUtils.isMac();
	}

	public static String getVendor() {
		return System.getenv(JBANG_JDK_VENDOR);
	}

	/**
	 * Swizzles the content retrieved from sites that are known to possibly embed
	 * code (i.e. mastodon and carbon)
	 */
	public static Path swizzleContent(String fileURL, Path filePath) throws IOException {
		boolean mastodon = fileURL.matches("https://.*/@(\\w+)/([0-9]+)");
		if (mastodon || fileURL.startsWith("https://carbon.now.sh") || fileURL.startsWith("https://bsky.app/")) { // sites
																													// known
			// to have
			// og:description
			// meta name or
			// property
			try {
				Document doc = Jsoup.parse(filePath.toFile(), "UTF-8", fileURL);

				String proposedString = null;

				proposedString = doc.select("meta[property=og:description],meta[name=og:description]")
					.first()
					.attr("content");

				/*
				 * if (twitter) { // remove fake quotes // proposedString =
				 * proposedString.replace("\u201c", ""); // proposedString =
				 * proposedString.replace("\u201d", ""); // unescape properly proposedString =
				 * Parser.unescapeEntities(proposedString, true);
				 * 
				 * }
				 */

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
						wantedfilename = filePath.getFileName() + ".jsh";
					}

					File f = filePath.toFile();
					File newFile = new File(f.getParent(), wantedfilename);
					f.renameTo(newFile);
					filePath = newFile.toPath();
					writeString(filePath, proposedString);
				}

			} catch (RuntimeException re) {
				// ignore any errors that can be caused by parsing
			}
		}

		// to handle if kubectl-style name (i.e. extension less)
		File f = filePath.toFile();
		String nonkebabname = f.getName();
		if (!f.getName().endsWith(".jar") && !f.getName().endsWith(".jsh")) { // avoid directly downloaded jar files
																				// getting renamed to .java
			nonkebabname = unkebabify(f.getName());
		}
		if (nonkebabname.equals(f.getName())) {
			return filePath;
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

	static public String getAgentString() {
		if (agent == null) {
			String version = System.getProperty("java.version") + "/"
					+ System.getProperty("java.vm.vendor", "<unknown>");
			agent = "JBang/" + getJBangVersion() + " (" + System.getProperty("os.name") + "/"
					+ System.getProperty("os.version") + "/" + System.getProperty("os.arch") + ") " + "Java/" + version;

			// This is a hack to avoid being blocked by some websites
			// because of the word "Java" in the User-Agent
			// Reverses any occurrence of "java" in the User-Agent using same casing
			Matcher matcher = Pattern.compile("(?i)java").matcher(agent);
			while (matcher.find()) {
				String reversed = new StringBuilder(matcher.group()).reverse().toString();
				agent = agent.replace(matcher.group(), reversed);
			}
		}

		return agent;
	}

	public static String getJBangVersion() {
		return BuildConfig.VERSION;
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
					verboseMsg("Directory url, assuming user want to get default application at main.java");
					url = url + MAIN_JAVA;
				}
			} catch (URISyntaxException e) {
				// ignore
			}

		}

		return url;
	}

	/**
	 * Takes common patterns and return "parent" urls that are meaningfull to trust.
	 * i.e. github.com/maxandersen/myproject/myfile.java will offer to trust the
	 * github.com/maxandersen/myproject project.
	 *
	 * @param url
	 * @return
	 */
	public static String goodTrustURL(String url) {
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

		url = url.replaceFirst("https://repo1.maven.org/maven2/(.*)/[0-9]+.*$",
				"https://repo1.maven.org/maven2/$1/");

		if (url.equals(originalUrl)) {
			java.net.URI uri = null;
			try {
				uri = new java.net.URI(url);
			} catch (URISyntaxException e) {
				return url;
			}
			if (uri.getPath().isEmpty() || uri.getPath().equals("/")) {
				return uri.toString();
			} else {
				URI suggested = (uri.getPath().endsWith("/") ? uri.resolve("..") : uri.resolve("."));
				if (suggested.getPath().isEmpty() || suggested.getPath().equals("/")) {
					// not returning top domain by default
					return originalUrl;
				} else {
					return suggested.toString();
				}
			}
		}

		return url;
	}

	public static String[] extractOrgProject(String url) {
		String orggprj = url.replaceFirst("^https://github.com/(.*)/blob/(.*)$", "$1/$2");
		orggprj = orggprj.replaceFirst("^https://raw.githubusercontent.com/(.*)/(.*)$", "$1/$2");
		orggprj = orggprj.replaceFirst("^https://gitlab.com/(.*)/-/(blob|raw)/(.*)$", "$1/$2");
		orggprj = orggprj.replaceFirst("^https://bitbucket.org/(.*)/(src|raw)/(.*)$", "$1/$2");
		if (!orggprj.equals(url)) {
			return orggprj.split("/", 2);
		} else {
			return null;
		}
	}

	public static String getStableID(File backingFile) {
		try {
			return getStableID(readString(backingFile.toPath()));
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR, e);
		}
	}

	public static String getStableID(String input) {
		return getStableID(Stream.of(input));
	}

	public static String getStableID(Stream<String> inputs) {
		final MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new ExitException(-1, e);
		}
		inputs.forEach(input -> {
			digest.update(input.getBytes(StandardCharsets.UTF_8));
		});
		final byte[] hashbytes = digest.digest();
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
				"^https://gist.github.com/(([a-zA-Z0-9\\-]*)/)?(?<gistid>[a-zA-Z0-9]*)$",
				"https://api.github.com/gists/${gistid}");

		verboseMsg("Gist url api: " + gistapi);
		Gist gist = null;
		try {
			gist = readJsonFromURL(gistapi, Gist.class);
		} catch (IOException e) {
			verboseMsg("Error when extracting file from gist url.");
			throw new IllegalStateException(e);
		}

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
					if (key.endsWith(".jsh") || hasMainMethod(entry.getValue().get("content")))
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

	private static String getFileNameFromGistURL(String url) {
		StringBuilder fileName = new StringBuilder();
		String[] pathPlusAnchor = url.split("#");
		if (pathPlusAnchor.length == 2) {
			String[] anchor = pathPlusAnchor[1].split("-");
			if (anchor.length < 2)
				throw new IllegalArgumentException("Invalid Gist url: " + url);
			fileName = new StringBuilder(anchor[1]);
			for (int i = 2; i < anchor.length - 1; ++i)
				fileName.append("-").append(anchor[i]);
		}
		return fileName.toString();
	}

	public static <T> T readJsonFromURL(String requestURL, Class<T> type) throws IOException {
		Path jsonFile = NetUtil.downloadAndCacheFile(requestURL);
		try (BufferedReader rdr = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
			Gson parser = new Gson();
			return parser.fromJson(rdr, type);
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
			ProcessBuilder pb = CommandBuffer.of(cmd).asProcessBuilder();
			pb.redirectErrorStream(true);
			Process p = pb.start();
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String cmdOutput = br.lines().collect(Collectors.joining("\n"));
			int exitCode = p.waitFor();
			if (exitCode == 0) {
				return cmdOutput;
			} else {
				verboseMsg(String.format("Command failed: #%d - %s", exitCode, cmdOutput));
			}
		} catch (IOException | InterruptedException ex) {
			verboseMsg("Error running: " + String.join(" ", cmd), ex);
		}
		return null;
	}

	public static boolean deletePath(Path path, boolean quiet) {
		Exception[] err = new Exception[] { null };
		try {
			if (Files.isDirectory(path)) {
				verboseMsg("Deleting folder " + path);
				try (Stream<Path> s = Files.walk(path)) {
					s.sorted(Comparator.reverseOrder())
						.forEach(f -> {
							try {
								Files.delete(f);
							} catch (IOException e) {
								err[0] = e;
							}
						});
				}
			} else if (Files.exists(path)) {
				verboseMsg("Deleting file " + path);
				Files.delete(path);
			} else if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
				Util.verboseMsg("Deleting broken link " + path);
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

	public static void createLink(Path link, Path target) {
		if (!Files.exists(link)) {
			// On Windows we use junction for directories because their
			// creation doesn't require any special privileges.
			if (isWindows() && Files.isDirectory(target)) {
				if (createJunction(link, target.toAbsolutePath())) {
					return;
				}
			} else {
				if (createSymbolicLink(link, target.toAbsolutePath())) {
					return;
				}
			}
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR, "Failed to create link " + link + " -> " + target);
		}
	}

	private static boolean createSymbolicLink(Path link, Path target) {
		try {
			Files.createSymbolicLink(link, target);
			return true;
		} catch (IOException e) {
			if (isWindows() && e instanceof AccessDeniedException && e.getMessage().contains("privilege")) {
				infoMsg(String.format("Creation of symbolic link failed %s -> %s", link, target));
				infoMsg("This is a known issue with trying to create symbolic links on Windows.");
				infoMsg("See the information available at the link below for a solution:");
				infoMsg("https://www.jbang.dev/documentation/guide/latest/usage.html#usage-on-windows");
			}
			verboseMsg(e.toString());
		}
		return false;
	}

	private static boolean createJunction(Path link, Path target) {
		if (!Files.exists(link) && Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
			// We automatically remove broken links
			deletePath(link, true);
		}
		return runCommand("cmd.exe", "/c", "mklink", "/j", link.toString(), target.toString()) != null;
	}

	public static boolean isLink(Path path) throws IOException {
		return !path.toAbsolutePath().equals(path.toRealPath());
	}

	public static boolean hasMainMethod(String content) {
		return patternMainMethod.matcher(content).find();
	}

	public static Optional<String> getMainClass(String content) {
		Matcher pc = publicClassPattern.matcher(content);

		if (pc.find()) {
			return Optional.ofNullable(pc.group(1));
		} else {
			return Optional.ofNullable(null);
		}
	}

	public static boolean isGistURL(String scriptURL) {
		return scriptURL.startsWith("https://gist.github.com/");
	}

	public static boolean isURL(String str) {
		try {
			URI uri = new URI(str);
			String s = uri.getScheme();
			return s != null && (s.equals("https") || s.equals("http") || s.equals("file"));
		} catch (URISyntaxException e) {
			return false;
		}
	}

	public static boolean isAbsoluteRef(String ref) {
		return isRemoteRef(ref) || Paths.get(ref).isAbsolute();
	}

	public static boolean isRemoteRef(String ref) {
		return ref.startsWith("http:") || ref.startsWith("https:") || DependencyUtil.looksLikeAGav(ref);
	}

	public static boolean isClassPathRef(String ref) {
		return ref.startsWith("classpath:");
	}

	public static boolean isValidPath(String path) {
		try {
			Paths.get(path);
			return true;
		} catch (InvalidPathException e) {
			return false;
		}
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
				if (!line.trim().startsWith("package "))
					continue;
				String[] pkgLine = line.split("package ");
				if (pkgLine.length == 1)
					continue;
				String packageName = pkgLine[1];
				return Optional.of(packageName.split(";")[0].trim()); // remove ';'
			}
		}

		return Optional.empty();
	}

	public static boolean isValidModuleIdentifier(String id) {
		return patternModuleId.matcher(id).matches();
	}

	public static boolean isValidClassIdentifier(String id) {
		return patternFQCN.matcher(id).matches();
	}

	/**
	 * Searches the locations defined by `PATH` for the given executable
	 * 
	 * @param cmd The name of the executable to look for
	 * @return A Path to the executable, if found, null otherwise
	 */
	public static Path searchPath(String cmd) {
		String envPath = System.getenv("PATH");
		envPath = envPath != null ? envPath : "";
		return searchPath(cmd, envPath, p -> true);
	}

	/**
	 * Searches the given `paths` for the given executable
	 *
	 * @param cmd   The name of the executable to look for
	 * @param paths A string containing the paths to search
	 * @return A Path to the executable, if found, null otherwise
	 */
	public static Path searchPath(String cmd, String paths) {
		return searchPath(cmd, paths, p -> true);
	}

	/**
	 * Searches the locations defined by `paths` for the given executable
	 *
	 * @param cmd        The name of the executable to look for
	 * @param paths      A string containing the paths to search
	 * @param pathFilter User filter for the executables found
	 * @return A Path to the executable, if found, null otherwise
	 */
	public static Path searchPath(String cmd, String paths, Predicate<Path> pathFilter) {
		return Arrays.stream(paths.split(File.pathSeparator))
			.filter(Util::isValidPath)
			.map(Paths::get)
			.filter(Files::isDirectory)
			.map(dir -> dir.resolve(cmd))
			.flatMap(Util::executables)
			.filter(Util::isExecutable)
			.filter(pathFilter)
			.findFirst()
			.orElse(null);
	}

	private static Stream<Path> executables(Path base) {
		if (isWindows()) {
			return Stream.of(Paths.get(base + ".exe"),
					Paths.get(base + ".bat"),
					Paths.get(base + ".cmd"),
					Paths.get(base + ".ps1"));
		} else {
			return Stream.of(base);
		}
	}

	public static boolean isExecutable(Path file) {
		if (Files.isRegularFile(file)) {
			if (isWindows()) {
				String nm = file.getFileName().toString().toLowerCase();
				return nm.endsWith(".exe") || nm.endsWith(".bat") || nm.endsWith(".cmd") || nm.endsWith(".ps1");
			} else {
				return Files.isExecutable(file);
			}
		}
		return false;
	}

	public static void setExecutable(Path file) {
		final Set<PosixFilePermission> permissions;
		try {
			permissions = Files.getPosixFilePermissions(file);
			permissions.add(PosixFilePermission.OWNER_EXECUTE);
			permissions.add(PosixFilePermission.GROUP_EXECUTE);
			Files.setPosixFilePermissions(file, permissions);
		} catch (UnsupportedOperationException | IOException e) {
			throw new ExitException(BaseCommand.EXIT_GENERIC_ERROR, "Couldn't mark script as executable: " + file, e);
		}
	}

	/**
	 * Converts a Path to a String. This is normally a trivial operation, but this
	 * method handles the special case when running in a Bash shell on Windows,
	 * where paths have a special format that Java doesn't know about (eg.
	 * C:\Directory\File.txt becomes /c/Directory/File.txt).
	 *
	 * @param path the Path to convert
	 * @return a String representing the given Path
	 */
	public static String pathToString(Path path) {
		if (isWindows() && getShell() == Shell.bash) {
			StringBuilder str = new StringBuilder();
			if (path.isAbsolute()) {
				if (path.getRoot().toString().endsWith(":\\")) {
					// Convert `x:` to `/x/`
					str.append("/").append(path.getRoot().toString().charAt(0)).append("/");
				} else {
					// Convert `\\server\share` to `//server/share`
					str.append(path.getRoot().toString().replace("\\", "/"));
				}
				for (int i = 0; i < path.getNameCount(); i++) {
					if (i > 0) {
						str.append("/");
					}
					str.append(path.getName(i).toString());
				}
			}
			return str.toString();
		} else {
			return path.toString();
		}
	}

	/**
	 * Converts a Path to a String. This is normally a trivial operation, but this
	 * method handles the special case when running in a Bash shell on Windows,
	 * where the default output format would cause problems because the backslashes
	 * would be interpreted as escape sequences. So we need to escape the
	 * backslashes.
	 *
	 * @param path the Path to convert
	 * @return a String representing the given Path
	 */
	public static String pathToOsString(Path path) {
		if (isWindows() && getShell() == Shell.bash) {
			return path.toString().replace("\\", "\\\\");
		} else {
			return path.toString();
		}
	}

	/**
	 * Determines if the current JBang we're running was one installed using `app
	 * install` or not
	 */
	public static boolean runningManagedJBang() {
		try {
			return getJarLocation().toRealPath().startsWith(Settings.getConfigBinDir().toRealPath());
		} catch (IOException e) {
			return getJarLocation().startsWith(Settings.getConfigBinDir());
		}
	}

	/**
	 * Determines the path to the JAR of the currently running JBang
	 *
	 * @return An actual Path if it was found, or an empty path if it was not
	 */
	public static Path getJarLocation() {
		return getJarLocation(VersionChecker.class);
	}

	/**
	 * Determines the path to the JAR that contains the given class
	 *
	 * @return An actual Path if it was found, or an empty path if it was not
	 */
	public static Path getJarLocation(Class<?> klazz) {
		try {
			File jarFile = new File(klazz.getProtectionDomain().getCodeSource().getLocation().toURI());
			return jarFile.toPath();
		} catch (URISyntaxException e) {
			// ignore
		}
		return Paths.get("");
	}

	public static Function<Path, Path> acceptFile(String fileName) {
		return p -> {
			Path file = p.resolve(fileName);
			if (Files.isRegularFile(file) && Files.isReadable(file)) {
				return file;
			}
			return null;
		};
	}

	public static <U, V> Function<U, V> notNull(Function<U, V> func) {
		return u -> {
			if (u == null) {
				return null;
			}
			return func.apply(u);
		};
	}

	public static <T> T findNearestWith(Path dir, Function<Path, T> accept) {
		T result = findNearestLocalWith(dir, accept);
		if (result == null) {
			result = accept.apply(Settings.getConfigDir());
		}
		return result;
	}

	private static <T> T findNearestLocalWith(Path dir, Function<Path, T> accept) {
		if (dir == null) {
			dir = getCwd();
		}
		Path root = Settings.getLocalRootDir();
		while (dir != null && !isSameFile(dir, root)) {
			T result = accept.apply(dir);
			if (result != null) {
				return result;
			}
			result = accept.apply(dir.resolve(Settings.JBANG_DOT_DIR));
			if (result != null) {
				return result;
			}
			dir = dir.getParent();
		}
		return null;
	}

	public static boolean isSameFile(Path f1, Path f2) {
		try {
			return Files.isSameFile(f1, f2);
		} catch (IOException e) {
			return f1.toAbsolutePath().equals(f2.toAbsolutePath());
		}
	}

	public static boolean isNullOrEmptyString(String str) {
		return str == null || str.isEmpty();
	}

	public static boolean isNullOrBlankString(String str) {
		return str == null || isBlankString(str);
	}

	public static boolean isBlankString(String str) {
		return str.trim().isEmpty();
	}

	public static int askInput(String message, int timeout, int defaultValue, String... options) {
		if (options.length == 0) {
			return -1;
		}
		ConsoleInput con = ConsoleInput.get(1, timeout, TimeUnit.SECONDS);
		if (con != null) {
			StringBuilder msg = new StringBuilder(message + "\n\n");
			for (int i = 0; i < options.length; i++) {
				msg.append("(").append(i + 1).append(") ").append(options[i]).append("\n");
			}
			msg.append("(0) Cancel\n");
			infoMsg(msg.toString());
			while (true) {
				infoMsg("Type in your choice and hit enter. Will automatically select option (" + defaultValue
						+ ") after " + timeout + " seconds.");
				String input = con.readLine();
				if (input == null) {
					infoMsg("Timeout reached, selecting option (" + defaultValue + ")");
					return defaultValue;
				}
				if (input.isEmpty()) {
					break;
				}
				try {
					int result = Integer.parseInt(input);
					if (result >= 0 && result <= options.length) {
						return result;
					}
				} catch (NumberFormatException ef) {
					errorMsg("Could not parse answer as a number. Canceling");
				}
			}
		} else if (!GraphicsEnvironment.isHeadless()) {
			infoMsg("Please make your selection in the pop-up dialog.");
			String defOpt = defaultValue > 0 ? options[defaultValue - 1] : "";
			setupApplicationIcon();
			Object selected = JOptionPane.showInputDialog(null, message, "Select your choice",
					JOptionPane.QUESTION_MESSAGE, getJbangIcon(), options, defOpt);
			if (selected == null) {
				return 0;
			}
			for (int i = 0; i < options.length; i++) {
				if (options[i] == selected) {
					return i + 1;
				}
			}
		} else {
			errorMsg("No console and no graphical interface, we can't ask for feedback!");
		}
		return -1;
	}

	public static boolean haveConsole() {
		return !"true".equalsIgnoreCase(System.getenv(JBANG_STDIN_NOTTY));
	}

	private static void setupApplicationIcon() {
		try {
			Class<?> clazz = Util.class.getClassLoader().loadClass("java.awt.Taskbar");
			Method getTaskbarMth = clazz.getMethod("getTaskbar");
			Object taskbar = getTaskbarMth.invoke(null);
			Method setIconImageMth = clazz.getMethod("setIconImage", Image.class);
			setIconImageMth.invoke(taskbar, getJbangIcon().getImage());
		} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
			verboseMsg("Unable to set application icon: Taskbar API not available");
		} catch (InvocationTargetException e) {
			verboseMsg("Unable to set application icon: " + e.getTargetException());
		}
	}

	public static boolean mkdirs(Path p) {
		try {
			Files.createDirectories(p);
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	private static ImageIcon getJbangIcon() {
		URL url = Util.class.getResource("/jbang_icon_64x64.png");
		if (url != null) {
			return new ImageIcon(url);
		} else {
			return null;
		}
	}

	@SafeVarargs
	public static <T> List<T> join(Collection<T>... lists) {
		List<T> res = new ArrayList<>();
		for (Collection<T> c : lists) {
			if (c != null && !c.isEmpty()) {
				res.addAll(c);
			}
		}
		return res;
	}

	public static String replaceAll(@NonNull Pattern pattern, @NonNull String input,
			@NonNull Function<MatchResult, String> replacer) {
		Matcher matcher = pattern.matcher(input);
		matcher.reset();
		boolean result = matcher.find();
		if (result) {
			StringBuffer sb = new StringBuffer();
			do {
				String replacement = replacer.apply(matcher);
				matcher.appendReplacement(sb, replacement);
				result = matcher.find();
			} while (result);
			matcher.appendTail(sb);
			return sb.toString();
		}
		return input;
	}

	public static String substituteRemote(String arg) {
		if (arg == null) {
			return null;
		}
		return Util.replaceAll(subUrlPattern, arg, m -> {
			String txt = m.group().substring(1);
			if (txt.startsWith("%")) {
				return Matcher.quoteReplacement(txt);
			}
			if (txt.startsWith("{") && txt.endsWith("}")) {
				txt = txt.substring(1, txt.length() - 1);
			}
			if (txt.startsWith("http://") || txt.startsWith("https://")) {
				try {
					return Matcher.quoteReplacement(NetUtil.downloadAndCacheFile(txt).toString());
				} catch (IOException e) {
					throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Error substituting remote file: " + txt,
							e);
				}
			} else if (txt.startsWith("deps:")) {
				List<String> deps = Arrays.asList(txt.substring(5).split(","));
				DependencyResolver resolver = new DependencyResolver();
				resolver.addDependencies(deps);
				ModularClassPath mcp = resolver.resolve();
				return mcp.getClassPath();
			} else {
				String type = txt.substring(0, txt.indexOf(":"));
				throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Unknown substitution type: " + type);
			}
		});
	}

	public static <K, V> Entry<K, V> entry(K k, V v) {
		return new AbstractMap.SimpleEntry<K, V>(k, v);
	}

	/**
	 * Converts an arbitrary string into a valid Java identifier that can be used as
	 * a class name. This is particularly important for JEP-445 implicit classes
	 * where the compiler derives class names from file names.
	 *
	 * @param baseName The input string to convert
	 * @return A valid Java identifier, never null or empty
	 */
	public static String toJavaIdentifier(@NonNull String baseName) {
		if (isValidJavaIdentifier(baseName)) {
			return baseName;
		}
		return "_" + baseName.replaceAll("[^\\p{L}\\p{N}_]", "_");
	}

	public static boolean isValidJavaIdentifier(String s) {
		if (s == null || s.isEmpty()) {
			return false;
		}

		if (!Character.isJavaIdentifierStart(s.charAt(0))) {
			return false;
		}

		for (int i = 1; i < s.length(); i++) {
			if (!Character.isJavaIdentifierPart(s.charAt(i))) {
				return false;
			}
		}

		return !isJavaKeyword(s); // Optional: block reserved keywords
	}

	private static final Set<String> JAVA_KEYWORDS = new HashSet<String>(Arrays.asList(
			"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
			"class", "const", "continue", "default", "do", "double", "else", "enum",
			"extends", "final", "finally", "float", "for", "goto", "if", "implements",
			"import", "instanceof", "int", "interface", "long", "native", "new",
			"package", "private", "protected", "public", "return", "short", "static",
			"strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
			"transient", "try", "void", "volatile", "while", "true", "false", "null"));

	public static boolean isJavaKeyword(String s) {
		return JAVA_KEYWORDS.contains(s);
	}
}
