package dev.jbang.util;

import static dev.jbang.util.Util.VerboseCategory.NETWORK;
import static dev.jbang.util.Util.deletePath;
import static dev.jbang.util.Util.getAgentString;
import static dev.jbang.util.Util.getStableID;
import static dev.jbang.util.Util.infoMsg;
import static dev.jbang.util.Util.isBlankString;
import static dev.jbang.util.Util.isFresh;
import static dev.jbang.util.Util.isNullOrBlankString;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import dev.jbang.Cache;
import dev.jbang.Settings;

public class NetUtil {
	public static final String JBANG_AUTH_BASIC_USERNAME = "JBANG_AUTH_BASIC_USERNAME";
	public static final String JBANG_AUTH_BASIC_PASSWORD = "JBANG_AUTH_BASIC_PASSWORD";

	/**
	 * Whenever the HTTP(S) resource being downloaded has a known extension, the
	 * corresponding {@code Accept} header will be additionally sent.
	 * <p>
	 * This is useful in case a remote server (e.g.: GitHub) may serve different
	 * content types for the same URL (e.g. both JSON and the HTML view of JSON
	 * source code), and a proxy server has one of them already cached.
	 *
	 * @see ConnectionConfigurator#accept()
	 */
	private static final @NonNull Map<@NonNull String, @NonNull String> KNOWN_CONTENT_TYPES;

	static {
		KNOWN_CONTENT_TYPES = new LinkedHashMap<>();
		KNOWN_CONTENT_TYPES.put(".json", "application/json");
	}

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
			verboseMsg(NETWORK, String.format("Using cached file %s for remote %s", cachedFile, fileURL));
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
				ConnectionConfigurator.accept(),
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
				ConnectionConfigurator.timeout(timeOut),
				ConnectionConfigurator.accept());
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
			verboseMsg(NETWORK, String.format("Requesting HTTP %s %s", httpConn.getRequestMethod(), httpConn.getURL()));
			verboseMsg(NETWORK, String.format("Headers %s", redactAuthHeaders(httpConn.getRequestProperties())));
		} else {
			verboseMsg(NETWORK, String.format("Requesting %s", urlConnection.getURL()));
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

		/**
		 * Returns a configurator that sets the {@code Accept} header for an HTTP
		 * connection based on the file extension of the URL path. The header value is
		 * determined from a predefined mapping of known content types.
		 *
		 * @return a configurator that optionally sets the {@code Accept} header on an
		 *         HTTP(S) connection.
		 * @see #KNOWN_CONTENT_TYPES
		 */
		static @NonNull ConnectionConfigurator accept() {
			return forHttp(conn -> {
				final String path = conn.getURL().getPath();
				for (final Entry<String, String> entry : KNOWN_CONTENT_TYPES.entrySet()) {
					if (path.endsWith(entry.getKey())) {
						conn.setRequestProperty("Accept", entry.getValue());
						break;
					}
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
							String err;
							try (BufferedReader reader = new BufferedReader(
									new InputStreamReader(httpConn.getErrorStream()))) {
								err = reader.lines()
									.collect(Collectors.joining("\n"))
									.trim();
							}
							verboseMsg(NETWORK, "HTTP: " + responseCode + " - " + err);
							if (err.startsWith("{") && err.endsWith("}")) {
								// Could be JSON, let's try to parse it
								try {
									Gson parser = new Gson();
									Map<String, Object> json = parser.fromJson(err,
											new TypeToken<Map<String, Object>>() {
											}.getType());
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
				verboseMsg(NETWORK, String.format("Downloaded file %s", conn.getURL().toExternalForm()));
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
							verboseMsg(NETWORK,
									String.format("Not modified, using cached file %s for remote %s", cachedFile,
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
								verboseMsg(NETWORK, "Unable to set last-modified time for " + cachedFile, e);
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
				verboseMsg(NETWORK, "Redirected to: " + url); // Should be debug info
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

	private static volatile NetrcParser cachedNetrc;
	private static volatile Path netrcFile;
	private static volatile boolean netrcDisabled;

	private static final int AUTH_HELPER_TIMEOUT_SECONDS = 10;
	private static final ConcurrentHashMap<String, String[]> gitCredentialCache = new ConcurrentHashMap<>();

	static NetrcParser getNetrc() {
		if (netrcDisabled) {
			return NetrcParser.empty();
		}
		if (cachedNetrc == null) {
			if (netrcFile != null) {
				cachedNetrc = NetrcParser.parse(netrcFile);
			} else {
				cachedNetrc = NetrcParser.parseDefault();
			}
		}
		return cachedNetrc;
	}

	/**
	 * Sets a custom .netrc file path, overriding the platform default. Resets the
	 * cache so the new file is picked up on next access.
	 */
	public static void setNetrcFile(Path path) {
		netrcFile = path;
		netrcDisabled = false;
		cachedNetrc = null;
	}

	public static void setNetrcDisabled(boolean disabled) {
		netrcDisabled = disabled;
		cachedNetrc = null;
	}

	// Visible for testing
	static void resetNetrcCache() {
		cachedNetrc = null;
		netrcFile = null;
		netrcDisabled = false;
		gitCredentialCache.clear();
	}

	/** Runs {@code git credential fill} for the given hostname. Cached per-host. */
	static String[] getGitCredentials(String host) {
		return gitCredentialCache.computeIfAbsent(host, h -> {
			String output = Util.runCommandQuietly(
					"protocol=https\nhost=" + h + "\n\n",
					Collections.singletonMap("GIT_TERMINAL_PROMPT", "0"),
					AUTH_HELPER_TIMEOUT_SECONDS,
					"git", "credential", "fill");
			if (output != null) {
				String username = null, password = null;
				for (String line : output.split("\n")) {
					if (line.startsWith("username="))
						username = line.substring(9);
					else if (line.startsWith("password="))
						password = line.substring(9);
				}
				if (!isNullOrBlankString(username) && !isNullOrBlankString(password)) {
					verboseMsg(NETWORK, "Using git credential for host: " + h);
					return new String[] { username, password };
				}
			}
			return null;
		});
	}

	/**
	 * Runs {@code gh auth token --hostname <host>} via {@link Util#runCommand}.
	 * Token cached per-host; username applied per-call from the netrc entry's login
	 * field.
	 */
	static String[] getGhAuthCredentials(String host, String login) {
		String[] cached = gitCredentialCache.computeIfAbsent("gh-auth:" + host, k -> {
			String token = Util.runCommandQuietly(null, null, AUTH_HELPER_TIMEOUT_SECONDS,
					"gh", "auth", "token", "--hostname", host);
			if (!isNullOrBlankString(token)) {
				verboseMsg(NETWORK, "Using gh auth token for host: " + host);
				return new String[] { "", token.trim() };
			}
			return null;
		});
		return withUsername(cached, login, "");
	}

	/**
	 * Runs {@code glab config get token --host <host>} via {@link Util#runCommand}.
	 */
	static String[] getGlabAuthCredentials(String host, String login) {
		String[] cached = gitCredentialCache.computeIfAbsent("glab-auth:" + host, k -> {
			String token = Util.runCommandQuietly(null, null, AUTH_HELPER_TIMEOUT_SECONDS,
					"glab", "config", "get", "token", "--host", host);
			if (!isNullOrBlankString(token)) {
				verboseMsg(NETWORK, "Using glab auth token for host: " + host);
				return new String[] { "", token.trim() };
			}
			return null;
		});
		return withUsername(cached, login, "__token__");
	}

	static String[] getEnvCredentials(String spec, String login) {
		String[] names = spec.split("-", 2);
		String passName = names[names.length - 1].toUpperCase(Locale.ROOT);
		String password = System.getenv(passName);
		if (isNullOrBlankString(password)) {
			return null;
		}
		String username = !isNullOrBlankString(login) ? login : "";
		if (names.length == 2) {
			String userName = names[0].toUpperCase(Locale.ROOT);
			username = System.getenv(userName);
			if (isNullOrBlankString(username)) {
				return null;
			}
			verboseMsg(NETWORK, "Using " + userName + " and " + passName + " environment variables");
		} else {
			verboseMsg(NETWORK, "Using " + passName + " environment variable");
		}
		return new String[] { username, password };
	}

	private static String[] withUsername(String[] cached, String login, String defaultUsername) {
		if (cached == null) {
			return null;
		}
		String username = !isNullOrBlankString(login) ? login : defaultUsername;
		return new String[] { username, cached[1] };
	}

	private static void addAuthHeaderIfNeeded(URLConnection urlConnection) {
		AuthHeader auth = null;
		String host = urlConnection.getURL().getHost();
		NetrcParser.NetrcEntry entry = null;

		// 1. URL userinfo (https://user:pass@host/...)
		URL url = urlConnection.getURL();
		if (url.getUserInfo() != null) {
			String[] credentials = url.getUserInfo().split(":", 2);
			String username = PropertiesValueResolver.replaceProperties(credentials[0]);
			String password = credentials.length > 1 ? PropertiesValueResolver.replaceProperties(credentials[1])
					: "";
			auth = AuthHeader.authorization(toBasicAuth(username, password));
			verboseMsg(NETWORK, "Using URL credentials for host: " + host);
		}

		// 2. .netrc exact host match (more specific than env vars)
		if (auth == null) {
			entry = getNetrc().getEntry(host).orElse(null);
			if (entry != null && !entry.isDefault()) {
				String[] creds = extractCredsFromEntry(entry, host);
				if (creds != null) {
					auth = toHttpAuth(entry, creds);
				}
			}
		}

		// 3. GitHub/GitLab env vars use Bearer auth for HTTP downloads
		if (auth == null) {
			String githubToken = System.getenv("GITHUB_TOKEN");
			String gitlabToken = System.getenv("GITLAB_TOKEN");
			if (isAGithubUrl(urlConnection) && !isNullOrBlankString(githubToken)) {
				auth = AuthHeader.authorization("Bearer " + githubToken);
				verboseMsg(NETWORK, "Using GITHUB_TOKEN environment variable for host: " + host);
			} else if (isAGitlabUrl(urlConnection) && !isNullOrBlankString(gitlabToken)) {
				auth = AuthHeader.authorization("Bearer " + gitlabToken);
				verboseMsg(NETWORK, "Using GITLAB_TOKEN environment variable for host: " + host);
			}
		}

		// 4. .netrc default entry (less specific than env vars)
		if (auth == null && entry != null && entry.isDefault()) {
			String[] creds = extractCredsFromEntry(entry, host);
			if (creds != null) {
				auth = toHttpAuth(entry, creds);
			}
		}

		// 5. Fall back to global basic auth env vars
		if (auth == null) {
			String username = System.getenv(JBANG_AUTH_BASIC_USERNAME);
			String password = System.getenv(JBANG_AUTH_BASIC_PASSWORD);
			if (!isNullOrBlankString(username) && !isNullOrBlankString(password)) {
				auth = AuthHeader.authorization(toBasicAuth(username, password));
				verboseMsg(NETWORK, "Using JBANG_AUTH_BASIC environment variables for host: " + host);
			}
		}

		if (auth != null) {
			urlConnection.setRequestProperty(auth.name, auth.value);
			verboseMsg(NETWORK, "Set " + auth.name + " header for host: " + host);
		}
	}

	private static AuthHeader toHttpAuth(NetrcParser.NetrcEntry entry, String[] creds) {
		String scheme = entry.getJbangAuthScheme();
		if (isNullOrBlankString(scheme) || "basic".equalsIgnoreCase(scheme)) {
			return AuthHeader.authorization(toBasicAuth(creds[0], creds[1]));
		}
		switch (scheme.toLowerCase(Locale.ROOT)) {
		case "bearer":
			return AuthHeader.authorization("Bearer " + creds[1]);
		default:
			verboseMsg(NETWORK, "Unknown jbang-auth-scheme: " + scheme + ", using basic");
			return AuthHeader.authorization(toBasicAuth(creds[0], creds[1]));
		}
	}

	private static class AuthHeader {
		private final String name;
		private final String value;

		private AuthHeader(String name, String value) {
			this.name = name;
			this.value = value;
		}

		private static AuthHeader authorization(String value) {
			return new AuthHeader("Authorization", value);
		}
	}

	private static String toBasicAuth(String username, String password) {
		String id = username + ":" + password;
		return "Basic " + Base64.getEncoder().encodeToString(id.getBytes(StandardCharsets.UTF_8));
	}

	private static Map<String, List<String>> redactAuthHeaders(Map<String, List<String>> headers) {
		LinkedHashMap<String, List<String>> redacted = new LinkedHashMap<>(headers);
		if (redacted.containsKey("Authorization")) {
			redacted.put("Authorization", Collections.singletonList("[REDACTED]"));
		}
		return redacted;
	}

	private static boolean isAGithubUrl(URLConnection urlConnection) {
		return isAGithubHost(urlConnection.getURL().getHost());
	}

	private static boolean isAGithubHost(String host) {
		return host.equals("github.com") || host.endsWith(".github.com")
				|| host.equals("githubusercontent.com") || host.endsWith(".githubusercontent.com");
	}

	private static boolean isAGitlabUrl(URLConnection urlConnection) {
		return isAGitlabHost(urlConnection.getURL().getHost());
	}

	private static boolean isAGitlabHost(String host) {
		return host.equals("gitlab.com") || host.endsWith(".gitlab.com");
	}

	/**
	 * Returns Basic credentials (username + password) for the given hostname, or
	 * {@code null} when no credentials are found.
	 * <p>
	 * Lookup chain (most-specific first):
	 * <ol>
	 * <li>{@code .netrc} exact host match (including {@code jbang-auth} delegation
	 * via git-credential or gh-auth)</li>
	 * <li>{@code GITHUB_TOKEN} / {@code GITLAB_TOKEN} env vars for known hosts</li>
	 * <li>{@code .netrc} {@code default} entry (catch-all fallback)</li>
	 * <li>{@code JBANG_AUTH_BASIC_USERNAME/PASSWORD} env vars</li>
	 * </ol>
	 * <p>
	 * Used by both the HTTP download path ({@link #addAuthHeaderIfNeeded}) and the
	 * Maven resolver ({@link dev.jbang.dependencies.ArtifactResolver}).
	 *
	 * @return a two-element array {@code [username, password]}, or {@code null}
	 */
	public static String[] getCredentialsForHost(String host) {
		// 1. .netrc exact host match (most specific)
		NetrcParser.NetrcEntry entry = getNetrc().getEntry(host).orElse(null);
		if (entry != null && !entry.isDefault()) {
			String[] creds = extractCredsFromEntry(entry, host);
			if (creds != null) {
				return creds;
			}
		}

		// 2. Well-known env vars for specific hosts
		String githubToken = System.getenv("GITHUB_TOKEN");
		String gitlabToken = System.getenv("GITLAB_TOKEN");
		if (isAGithubHost(host) && !isNullOrBlankString(githubToken)) {
			verboseMsg(NETWORK, "Using GITHUB_TOKEN for host: " + host);
			return new String[] { "", githubToken };
		} else if (isAGitlabHost(host) && !isNullOrBlankString(gitlabToken)) {
			verboseMsg(NETWORK, "Using GITLAB_TOKEN for host: " + host);
			return new String[] { "__token__", gitlabToken };
		}

		// 3. .netrc default entry (catch-all, less specific than env vars)
		if (entry != null && entry.isDefault()) {
			String[] creds = extractCredsFromEntry(entry, host);
			if (creds != null) {
				return creds;
			}
		}

		// 4. Fall back to global basic auth env vars
		String envUser = System.getenv(JBANG_AUTH_BASIC_USERNAME);
		String envPass = System.getenv(JBANG_AUTH_BASIC_PASSWORD);
		if (!isNullOrBlankString(envUser) && !isNullOrBlankString(envPass)) {
			verboseMsg(NETWORK, "Using JBANG_AUTH_BASIC for host: " + host);
			return new String[] { envUser, envPass };
		}

		verboseMsg(NETWORK, "No credentials found for host: " + host);
		return null;
	}

	/**
	 * Extracts credentials from a single .netrc entry: tries jbang-auth methods in
	 * order, then login/password. Returns null if nothing yields credentials.
	 */
	private static String[] extractCredsFromEntry(NetrcParser.NetrcEntry entry, String host) {
		String jbangAuth = entry.getJbangAuth();
		if (!isNullOrBlankString(jbangAuth)) {
			// Use jbang-auth-host as the lookup host if specified
			String authHost = !isNullOrBlankString(entry.getJbangAuthHost())
					? entry.getJbangAuthHost()
					: host;
			for (String method : jbangAuth.split(",")) {
				String[] creds = runAuthMethod(method.trim(), authHost, entry.getLogin());
				if (creds != null) {
					return creds;
				}
			}
		}
		if (!isNullOrBlankString(entry.getLogin()) && !isNullOrBlankString(entry.getPassword())) {
			verboseMsg(NETWORK, "Using .netrc credentials for host: " + host);
			return new String[] { entry.getLogin(), entry.getPassword() };
		}
		return null;
	}

	private static String[] runAuthMethod(String method, String host, String login) {
		switch (method) {
		case "git-credential":
			return getGitCredentials(host);
		case "gh-auth":
			return getGhAuthCredentials(host, login);
		case "glab-auth":
			return getGlabAuthCredentials(host, login);
		default:
			if (method.startsWith("env.") && method.length() > 4) {
				return getEnvCredentials(method.substring(4), login);
			}
			verboseMsg(NETWORK, "Unknown jbang-auth method: " + method);
			return null;
		}
	}

	/**
	 * Returns a human-readable description of the authentication method that would
	 * be used for the given URL, or {@code null} if no authentication is
	 * configured.
	 */
	public static String describeAuthMethod(String url) {
		try {
			URL u = new URL(url);
			String host = u.getHost();

			// 1. URL userinfo
			if (u.getUserInfo() != null) {
				return "URL credentials";
			}

			// 2. .netrc exact host match
			NetrcParser.NetrcEntry entry = getNetrc().getEntry(host).orElse(null);
			if (entry != null && !entry.isDefault()) {
				String desc = describeEntry(entry);
				if (desc != null) {
					return desc;
				}
			}

			// 3. Well-known env vars for specific hosts
			if (isAGithubHost(host) && !isNullOrBlankString(System.getenv("GITHUB_TOKEN"))) {
				return "GITHUB_TOKEN";
			}
			if (isAGitlabHost(host) && !isNullOrBlankString(System.getenv("GITLAB_TOKEN"))) {
				return "GITLAB_TOKEN";
			}

			// 4. .netrc default entry
			if (entry != null && entry.isDefault()) {
				String desc = describeEntry(entry);
				if (desc != null) {
					return desc;
				}
			}

			// 5. Global basic auth env vars
			if (!isNullOrBlankString(System.getenv(JBANG_AUTH_BASIC_USERNAME))
					&& !isNullOrBlankString(System.getenv(JBANG_AUTH_BASIC_PASSWORD))) {
				return "JBANG_AUTH_BASIC_USERNAME/PASSWORD";
			}
		} catch (Exception e) {
			// ignore
		}
		return null;
	}

	private static String describeEntry(NetrcParser.NetrcEntry entry) {
		if (!isNullOrBlankString(entry.getJbangAuth())) {
			return entry.getJbangAuth() + " (via .netrc)";
		}
		if (!isNullOrBlankString(entry.getLogin()) && !isNullOrBlankString(entry.getPassword())) {
			return ".netrc";
		}
		return null;
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
