package dev.jbang;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import com.google.gson.Gson;

import picocli.CommandLine;

public class Util {

	static boolean verbose;

	public static void setVerbose(boolean verbose) {
		Util.verbose = verbose;
	}

	public static boolean isVerbose() {
		return verbose;
	}

	public enum OS {
		linux, mac, windows
	}

	public enum Arch {
		x32, x64
	}

	static public void debugMsg(String msg) {
		if (isVerbose()) {
			System.err.println("[jbang] " + msg);
		}
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

	static public void errorMsg(String msg, Throwable e) {
		System.err.println("[jbang] [ERROR] " + msg);
		if (isVerbose()) {
			e.printStackTrace();
		} else {
			infoMsg(e.getMessage());
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

	private static final int BUFFER_SIZE = 4096;

	private static final Pattern mainClassPattern = Pattern.compile(
			"(?sm)class *(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*) .*static void main");

	public static OS getOS() {
		String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "");
		if (os.startsWith("mac") || os.startsWith("osx")) {
			return OS.mac;
		} else if (os.startsWith("linux")) {
			return OS.linux;
		} else if (os.startsWith("win")) {
			return OS.windows;
		} else {
			throw new ExitException(CommandLine.ExitCode.SOFTWARE, "Unsupported OS: " + os);
		}
	}

	public static Arch getArch() {
		String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "");
		if (arch.matches("^(x8664|amd64|ia32e|em64t|x64)$")) {
			return Arch.x64;
		} else if (arch.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) {
			return Arch.x32;
		} else {
			throw new ExitException(CommandLine.ExitCode.SOFTWARE, "Unsupported architecture: " + arch);
		}
	}

	public static boolean isWindows() {
		return getOS() == OS.windows;
	}

	public static boolean isMac() {
		return getOS() == OS.mac;
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
		try (ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
				FileOutputStream fileOutputStream = new FileOutputStream(saveFilePath.toFile())) {
			fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
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
		Path file = Files.isDirectory(urlCache) ? Files.list(urlCache).findFirst().orElse(null) : null;
		if (updateCache || file == null) {
			try {
				urlCache.toFile().mkdirs();
				return downloadFile(fileURL, urlCache.toFile());
			} catch (Throwable th) {
				deleteFolder(urlCache, true);
				throw th;
			}
		} else {
			return urlCache.resolve(file);
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

	public static boolean deleteFolder(Path folder, boolean quiet) {
		if (Files.isDirectory(folder)) {
			try {
				Files	.walk(folder)
						.sorted(Comparator.reverseOrder())
						.map(Path::toFile)
						.forEach(File::delete);
			} catch (IOException e) {
				if (quiet) {
					return false;
				} else {
					throw new ExitException(-1, "Could not delete folder " + folder.toString());
				}
			}
		}
		return true;
	}

}
