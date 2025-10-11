package dev.jbang.util;

import static dev.jbang.util.Util.deletePath;
import static dev.jbang.util.Util.getAgentString;
import static dev.jbang.util.Util.getStableID;
import static dev.jbang.util.Util.infoMsg;
import static dev.jbang.util.Util.isBlankString;
import static dev.jbang.util.Util.isFresh;
import static dev.jbang.util.Util.isOffline;
import static dev.jbang.util.Util.readString;
import static dev.jbang.util.Util.swizzleURL;
import static dev.jbang.util.Util.verboseMsg;
import static dev.jbang.util.Util.writeString;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import dev.jbang.Cache;
import dev.jbang.Settings;

public class NetUtil {
	public static final String JBANG_AUTH_BASIC_USERNAME = "JBANG_AUTH_BASIC_USERNAME";
	public static final String JBANG_AUTH_BASIC_PASSWORD = "JBANG_AUTH_BASIC_PASSWORD";

	/**
	 * Either retrieves a previously downloaded file from the cache or downloads a
	 * file from a URL and stores it in the cache.
	 *
	 * @param fileURL HTTP URL of the file to be downloaded
	 * @return Path to the downloaded file
	 * @throws IOException
	 */
	public static Path downloadAndCacheFile(String fileURL) throws IOException {
		Path saveDir = getUrlCacheDir(fileURL);
		Path metaSaveDir = getCacheMetaDir(saveDir);
		Path cachedFile = getCachedFile(saveDir);
		if (cachedFile == null || isEvicted(cachedFile)) {
			return downloadFileAndCache(fileURL, saveDir, metaSaveDir, cachedFile);
		} else {
			verboseMsg(String.format("Using cached file %s for remote %s", cachedFile, fileURL));
			return saveDir.resolve(cachedFile);
		}
	}

	// Returns true if the cached file doesn't exist or if its last
	// modified time is longer ago than the configuration value
	// indicated by "cache-evict" (defaults to "0" which will
	// cause this method to always return "true").
	private static boolean isEvicted(Path cachedFile) {
		if (isOffline()) {
			return false;
		}
		if (isFresh()) {
			return true;
		}
		if (Files.isRegularFile(cachedFile)) {
			long cma = Settings.getCacheEvict();
			if (cma == 0) {
				return true;
			} else if (cma == -1) {
				return false;
			}
			try {
				Instant cachedLastModified = Files.getLastModifiedTime(cachedFile).toInstant();
				Duration d = Duration.between(cachedLastModified, Instant.now());
				return d.getSeconds() >= cma;
			} catch (IOException e) {
				return true;
			}
		} else {
			return true;
		}
	}

	private static Path downloadFileAndCache(String fileURL, Path saveDir, Path metaSaveDir, Path cachedFile)
			throws IOException {
		ConnectionConfigurator cfg = ConnectionConfigurator.all(
				ConnectionConfigurator.userAgent(),
				ConnectionConfigurator.authentication(),
				ConnectionConfigurator.timeout(null),
				ConnectionConfigurator.cacheControl(cachedFile, metaSaveDir));
		ResultHandler handler = ResultHandler.redirects(cfg,
				ResultHandler.handleUnmodified(cachedFile,
						ResultHandler.throwOnError(
								ResultHandler.downloadToTempDir(saveDir, metaSaveDir,
										ResultHandler::downloadTo))));
		return connect(fileURL, cfg, handler);
	}

	/**
	 * Downloads a file from a URL
	 *
	 * @param fileURL HTTP URL of the file to be downloaded
	 * @param saveDir path of the directory to save the file
	 * @return Path to the downloaded file
	 * @throws IOException
	 */
	public static Path downloadFile(String fileURL, Path saveDir) throws IOException {
		return downloadFile(fileURL, saveDir, -1);
	}

	/**
	 * Downloads a file from a URL
	 *
	 * @param fileURL HTTP URL of the file to be downloaded
	 * @param saveDir path of the directory to save the file
	 * @param timeOut the timeout in milliseconds to use for opening the connection.
	 *                0 is an infinite timeout while -1 uses the defaults
	 * @return Path to the downloaded file
	 * @throws IOException
	 */
	public static Path downloadFile(String fileURL, Path saveDir, Integer timeOut) throws IOException {
		ConnectionConfigurator cfg = ConnectionConfigurator.all(
				ConnectionConfigurator.userAgent(),
				ConnectionConfigurator.authentication(),
				ConnectionConfigurator.timeout(timeOut));
		ResultHandler handler = ResultHandler.redirects(cfg,
				ResultHandler.throwOnError(
						ResultHandler.downloadTo(saveDir, saveDir)));
		return connect(fileURL, cfg, handler);
	}

	static Path etagFile(Path cachedFile, Path metaSaveDir) {
		return metaSaveDir.resolve(cachedFile.getFileName() + ".etag");
	}

	private static String safeReadEtagFile(Path cachedFile, Path metaSaveDir) {
		Path etag = etagFile(cachedFile, metaSaveDir);
		if (Files.isRegularFile(etag)) {
			try {
				return readString(etag);
			} catch (IOException e) {
				// Ignore
			}
		}
		return null;
	}

	private static Path connect(String fileURL, ConnectionConfigurator configurator, ResultHandler resultHandler)
			throws IOException {
		if (isOffline()) {
			throw new FileNotFoundException("jbang is in offline mode, no remote access permitted");
		}

		URL url = new URL(fileURL);
		URLConnection urlConnection = url.openConnection();
		configurator.configure(urlConnection);

		if (urlConnection instanceof HttpURLConnection) {
			HttpURLConnection httpConn = (HttpURLConnection) urlConnection;
			verboseMsg(String.format("Requesting HTTP %s %s", httpConn.getRequestMethod(), httpConn.getURL()));
			verboseMsg(String.format("Headers %s", httpConn.getRequestProperties()));
		} else {
			verboseMsg(String.format("Requesting %s", urlConnection.getURL()));
		}

		try {
			return resultHandler.handle(urlConnection);
		} finally {
			if (urlConnection instanceof HttpURLConnection) {
				((HttpURLConnection) urlConnection).disconnect();
			}
		}
	}

	interface ConnectionConfigurator {

		void configure(URLConnection conn) throws IOException;

		static ConnectionConfigurator all(ConnectionConfigurator... configurators) {
			return conn -> {
				for (ConnectionConfigurator c : configurators) {
					c.configure(conn);
				}
			};
		}

		static ConnectionConfigurator userAgent() {
			return conn -> {
				conn.setRequestProperty("User-Agent", getAgentString());
			};
		}

		static ConnectionConfigurator authentication() {
			return NetUtil::addAuthHeaderIfNeeded;
		}

		interface HttpConnectionConfigurator {
			void configure(HttpURLConnection conn) throws IOException;
		}

		static ConnectionConfigurator forHttp(HttpConnectionConfigurator configurator) {
			return conn -> {
				if (conn instanceof HttpURLConnection) {
					configurator.configure((HttpURLConnection) conn);
				}
			};
		}

		static ConnectionConfigurator timeout(Integer timeOut) {
			return forHttp(conn -> {
				int t = (timeOut != null) ? timeOut : Settings.getConnectionTimeout();
				if (t >= 0) {
					conn.setConnectTimeout(t);
					conn.setReadTimeout(t);
				}
			});
		}

		static ConnectionConfigurator cacheControl(Path cachedFile, Path metaSaveDir) throws IOException {
			if (cachedFile != null && !isFresh()) {
				String cachedETag = safeReadEtagFile(cachedFile, metaSaveDir);
				Instant lmt = Files.getLastModifiedTime(cachedFile).toInstant();
				ZonedDateTime zlmt = ZonedDateTime.ofInstant(lmt, ZoneId.of("GMT"));
				String cachedLastModified = DateTimeFormatter.RFC_1123_DATE_TIME.format(zlmt);
				return conn -> {
					if (cachedETag != null) {
						conn.setRequestProperty("If-None-Match", cachedETag);
					}
					conn.setRequestProperty("If-Modified-Since", cachedLastModified);
				};
			} else {
				return conn -> {
				};
			}
		}
	}

	private interface ResultHandler {

		Path handle(URLConnection urlConnection) throws IOException;

		static ResultHandler redirects(ConnectionConfigurator configurator, ResultHandler okHandler) {
			return conn -> {
				if (conn instanceof HttpURLConnection) {
					conn = handleRedirects((HttpURLConnection) conn, configurator);
				}
				return okHandler.handle(conn);
			};
		}

		static ResultHandler throwOnError(ResultHandler okHandler) {
			return conn -> {
				if (conn instanceof HttpURLConnection) {
					HttpURLConnection httpConn = (HttpURLConnection) conn;
					int responseCode = httpConn.getResponseCode();
					if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
						String fileURL = conn.getURL().toExternalForm();
						throw new FileNotFoundException(
								"No file to download at " + fileURL + ". Server replied HTTP code: " + responseCode);
					} else if (responseCode >= 400) {
						String message = null;
						if (httpConn.getErrorStream() != null) {
							String err = new BufferedReader(new InputStreamReader(httpConn.getErrorStream()))
								.lines()
								.collect(
										Collectors.joining(
												"\n"))
								.trim();
							verboseMsg("HTTP: " + responseCode + " - " + err);
							if (err.startsWith("{") && err.endsWith("}")) {
								// Could be JSON, let's try to parse it
								try {
									Gson parser = new Gson();
									Map json = parser.fromJson(err, Map.class);
									// GitHub returns useful information in `message`,
									// if it's there we use it.
									// TODO add support for other known sites
									message = Objects.toString(json.get("message"));
								} catch (JsonSyntaxException ex) {
									// Not JSON it seems
								}
							}
						}
						if (message != null) {
							throw new IOException(
									String.format("Server returned HTTP response code: %d for URL: %s with message: %s",
											responseCode, conn.getURL().toString(), message));
						} else {
							throw new IOException(String.format("Server returned HTTP response code: %d for URL: %s",
									responseCode, conn.getURL().toString()));
						}
					}
				}
				return okHandler.handle(conn);
			};
		}

		static ResultHandler downloadTo(Path saveDir, Path metaSaveDir) {
			return (conn) -> {
				// copy content from connection to file
				String fileName = extractFileName(conn);
				Path file = saveDir.resolve(fileName);
				Files.createDirectories(saveDir);
				Files.createDirectories(metaSaveDir);
				try (ReadableByteChannel readableByteChannel = Channels.newChannel(conn.getInputStream());
						FileOutputStream fileOutputStream = new FileOutputStream(file.toFile())) {
					fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
				}
				// create an .etag file if the information is present in the response headers
				String etag = conn.getHeaderField("ETag");
				if (etag != null) {
					writeString(etagFile(file, metaSaveDir), etag);
				}
				verboseMsg(String.format("Downloaded file %s", conn.getURL().toExternalForm()));
				return file;
			};
		}

		static ResultHandler downloadToTempDir(Path saveDir, Path metaSaveDir,
				BiFunction<Path, Path, ResultHandler> downloader) {
			return (conn) -> {
				// create a temp directory for the downloaded content
				Path saveTmpDir = saveDir.getParent().resolve(saveDir.getFileName() + ".tmp");
				Path saveOldDir = saveDir.getParent().resolve(saveDir.getFileName() + ".old");
				Path metaTmpDir = metaSaveDir.getParent().resolve(metaSaveDir.getFileName() + ".tmp");
				Path metaOldDir = metaSaveDir.getParent().resolve(metaSaveDir.getFileName() + ".old");
				try {
					deletePath(saveTmpDir, true);
					deletePath(saveOldDir, true);
					deletePath(metaTmpDir, true);
					deletePath(metaOldDir, true);

					Path saveFilePath = downloader.apply(saveTmpDir, metaTmpDir).handle(conn);

					// temporarily save the old content
					if (Files.isDirectory(saveDir)) {
						Files.move(saveDir, saveOldDir);
					}
					if (Files.isDirectory(metaSaveDir)) {
						Files.move(metaSaveDir, metaOldDir);
					}
					// rename the folder to its final name
					Files.move(saveTmpDir, saveDir);
					Files.move(metaTmpDir, metaSaveDir);
					// remove any old content
					deletePath(saveOldDir, true);
					deletePath(metaOldDir, true);

					return saveDir.resolve(saveFilePath.getFileName());
				} catch (Throwable th) {
					// remove the temp folder if anything went wrong
					deletePath(saveTmpDir, true);
					deletePath(metaTmpDir, true);
					// and move the old content back if it exists
					if (!Files.isDirectory(saveDir) && Files.isDirectory(saveOldDir)) {
						try {
							Files.move(saveOldDir, saveDir);
						} catch (IOException ex) {
							// Ignore
						}
					}
					if (!Files.isDirectory(metaSaveDir) && Files.isDirectory(metaOldDir)) {
						try {
							Files.move(metaOldDir, metaSaveDir);
						} catch (IOException ex) {
							// Ignore
						}
					}
					throw th;
				}
			};
		}

		static ResultHandler handleUnmodified(Path cachedFile, ResultHandler okHandler) {
			if (cachedFile != null) {
				return (conn) -> {
					if (conn instanceof HttpURLConnection) {
						HttpURLConnection httpConn = (HttpURLConnection) conn;
						if (httpConn.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
							verboseMsg(String.format("Not modified, using cached file %s for remote %s", cachedFile,
									conn.getURL().toExternalForm()));
							// Update cached file's last modified time
							try {
								Files.setLastModifiedTime(cachedFile, FileTime.from(ZonedDateTime.now().toInstant()));
							} catch (IOException e) {
								// There is an issue with Java not being able to set the file's last modified
								// time
								// on certain systems, resulting in an exception. There's not much we can do
								// about
								// that, so we'll just ignore it. It does mean that files affected by this will
								// be
								// re-downloaded every time.
								verboseMsg("Unable to set last-modified time for " + cachedFile, e);
							}
							return cachedFile;
						}
					}
					return okHandler.handle(conn);
				};
			} else {
				return okHandler;
			}
		}
	}

	private static String extractFileName(URLConnection urlConnection) throws IOException {
		String fileURL = urlConnection.getURL().toExternalForm();
		String fileName = "";
		if (urlConnection instanceof HttpURLConnection) {
			HttpURLConnection httpConn = (HttpURLConnection) urlConnection;
			int responseCode = httpConn.getResponseCode();
			// always check HTTP response code first
			if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
				String disposition = httpConn.getHeaderField("Content-Disposition");
				if (disposition != null) {
					// extracts file name from header field
					fileName = getDispositionFilename(disposition);
				}
			}
			if (isBlankString(fileName)) {
				// extracts file name from URL if nothing found
				int p = fileURL.indexOf("?");
				// Strip parameters from the URL (if any)
				String simpleUrl = (p > 0) ? fileURL.substring(0, p) : fileURL;
				while (simpleUrl.endsWith("/")) {
					simpleUrl = simpleUrl.substring(0, simpleUrl.length() - 1);
				}
				fileName = simpleUrl.substring(simpleUrl.lastIndexOf("/") + 1);
			}
		} else {
			fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1);
		}
		return fileName;
	}

	private static HttpURLConnection handleRedirects(HttpURLConnection httpConn, ConnectionConfigurator configurator)
			throws IOException {
		int responseCode;
		int redirects = 0;
		while (true) {
			httpConn.setInstanceFollowRedirects(false);
			responseCode = httpConn.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_MULT_CHOICE ||
					responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
					responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
					responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
					responseCode == 307 /* TEMP REDIRECT */ ||
					responseCode == 308 /* PERM REDIRECT */) {
				if (redirects++ > 8) {
					throw new IOException("Too many redirects");
				}
				String location = httpConn.getHeaderField("Location");
				if (location == null) {
					throw new IOException("No 'Location' header in redirect");
				}
				URL url = new URL(httpConn.getURL(), location);
				// TODO we should make this swizzler optional/configurable!
				// Right now it assumes we're always trying to get Java
				// source files which just isn't the case (eg //FILES)
				url = new URL(swizzleURL(url.toString()));
				verboseMsg("Redirected to: " + url); // Should be debug info
				httpConn = (HttpURLConnection) url.openConnection();
				if (responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
					// This response code forces the method to GET
					httpConn.setRequestMethod("GET");
				}
				configurator.configure(httpConn);
				continue;
			}
			break;
		}
		return httpConn;
	}

	private static void addAuthHeaderIfNeeded(URLConnection urlConnection) {
		String auth = null;
		if (isAGithubUrl(urlConnection) && System.getenv().containsKey("GITHUB_TOKEN")) {
			auth = "token " + System.getenv("GITHUB_TOKEN");
		} else {
			URL url = urlConnection.getURL();
			String username;
			String password;
			if (url.getUserInfo() != null) {
				String[] credentials = url.getUserInfo().split(":", 2);
				username = PropertiesValueResolver.replaceProperties(credentials[0]);
				password = credentials.length > 1 ? PropertiesValueResolver.replaceProperties(credentials[1]) : "";
			} else {
				username = System.getenv(JBANG_AUTH_BASIC_USERNAME);
				password = System.getenv(JBANG_AUTH_BASIC_PASSWORD);
			}
			if (username != null && password != null) {
				String id = username + ":" + password;
				String encodedId = Base64.getEncoder().encodeToString(id.getBytes(StandardCharsets.UTF_8));
				auth = "Basic " + encodedId;
			}
		}
		if (auth != null) {
			urlConnection.setRequestProperty("Authorization", auth);
		}
	}

	private static boolean isAGithubUrl(URLConnection urlConnection) {
		String host = urlConnection.getURL().getHost();
		return host.endsWith("github.com")
				|| host.endsWith("githubusercontent.com");
	}

	public static String getDispositionFilename(String disposition) {
		String fileName = "";
		int index1 = disposition.toLowerCase().lastIndexOf("filename=");
		int index2 = disposition.toLowerCase().lastIndexOf("filename*=");
		if (index1 > 0 && index1 > index2) {
			fileName = unquote(disposition.substring(index1 + 9));
		}
		if (index2 > 0 && index2 > index1) {
			String encodedName = disposition.substring(index2 + 10);
			String[] parts = encodedName.split("'", 3);
			if (parts.length == 3) {
				String encoding = parts[0].isEmpty() ? "iso-8859-1" : parts[0];
				String name = parts[2];
				try {
					fileName = URLDecoder.decode(name, encoding);
				} catch (UnsupportedEncodingException e) {
					infoMsg("Content-Disposition contains unsupported encoding " + encoding);
				}
			}
		}
		return fileName;
	}

	public static String unquote(String txt) {
		if (txt.startsWith("\"") && txt.endsWith("\"")) {
			txt = txt.substring(1, txt.length() - 1);
		}
		return txt;
	}

	/**
	 * Checks if a file was previously downloaded and is available in the cache. The
	 * result takes into account the connection status and freshness requests.
	 *
	 * @param fileURL HTTP URL of the file to check
	 * @return boolean indicating if the file can be retrieved from the cache
	 * @throws IOException
	 */
	public static boolean isFileCached(String fileURL) throws IOException {
		Path urlCache = getUrlCacheDir(fileURL);
		Path file = getCachedFile(urlCache);
		return ((!isFresh() || isOffline()) && file != null);
	}

	private static Path getCachedFile(Path dir) throws IOException {
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
	 * @return Path to the downloaded file
	 * @throws IOException
	 */
	public static Path obtainFile(String filePathOrURL) throws IOException {
		try {
			Path file = Paths.get(filePathOrURL);
			if (Files.isRegularFile(file)) {
				return file;
			}
		} catch (InvalidPathException ex) {
			// Ignore
		}
		return downloadAndCacheFile(swizzleURL(filePathOrURL));
	}

	public static Path getUrlCacheDir(String fileURL) {
		String urlHash = getStableID(fileURL);
		return Settings.getCacheDir(Cache.CacheClass.urls).resolve(urlHash);
	}

	public static Path getCacheMetaDir(Path cacheDir) {
		return cacheDir.getParent().resolve(cacheDir.getFileName() + "-meta");
	}

}
