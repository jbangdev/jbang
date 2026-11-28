package dev.jbang;

import static com.github.tomakehurst.wiremock.client.WireMock.recordSpec;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.jar.JarOutputStream;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.aesh.command.parser.CommandLineParserException;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.JvmProxyConfigurer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.CommandInvoker;
import dev.jbang.cli.JBang;
import dev.jbang.cli.Run;
import dev.jbang.dependencies.DependencyCache;
import dev.jbang.util.Util;

public abstract class BaseTest {
	public Path jbangTempDir;
	public Path cwdDir;
	public WireMockServer globalwms;

	public static Path mavenTempDir;
	private static boolean sharedMavenRepo;
	public static Path jdksTempDir;
	public static Path examplesTestFolder;

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@BeforeEach
	void initEnv(@TempDir Path tempPath) throws IOException {
		Util.setQuiet(false);
		Util.setOffline(false);
		Util.setAskInputTimeout(0);
		Util.setFresh(false);
		Util.setPreview(false);
		Util.setVerbose(true);
		jbangTempDir = Files.createDirectory(tempPath.resolve("jbang"));
		cwdDir = Files.createDirectory(tempPath.resolve("cwd"));
		Util.setCwd(cwdDir);
		System.setProperty("user.home", tempPath.toString());
		System.setProperty("maven.repo.local", mavenTempDir.toString());
		// System.setProperty("jbang.build.integration.forceExternal", "true");
		// Each test gets a fresh JBang config folder
		environmentVariables.set(Settings.JBANG_DIR, jbangTempDir.toString());
		// Each test gets a fresh cache folder
		environmentVariables.set(Settings.JBANG_CACHE_DIR, jbangTempDir.resolve("cache").toString());
		// Except we make all tests use the same JDK installation folder to prevent
		// excessive downloads
		environmentVariables.set(Settings.JBANG_CACHE_DIR + "_JDKS", jdksTempDir.toString());
		// Make sure we don't go looking outside our temp dir
		environmentVariables.set(Settings.JBANG_LOCAL_ROOT, tempPath.toString());
		// Don't check fo rnew versions while running tests
		environmentVariables.set(Settings.ENV_NO_VERSION_CHECK, "true");
		if (Util.isWindows()) {
			// On Windows assume we're running from within a CMD shell
			environmentVariables.set(Util.JBANG_RUNTIME_SHELL, "cmd");
		}
		Configuration.instance(null);
		DependencyCache.clear();
		initWireMock();
	}

	@AfterEach
	public void cleanupEnv() {
		cleanupWireMock();
	}

	public static final String EXAMPLES_FOLDER = "itests";

	// Code to be run before all tests using JBangTestExecutionListener
	// Not using @BeforeAll because it runs each time for each test class
	static void initBeforeAll() throws URISyntaxException, IOException {
		try {
			// The default ConsoleHandler for logging doesn't like us changing
			// System.err out from under it, so we remove it and add our own
			LogManager lm = LogManager.getLogManager();
			lm.readConfiguration(BaseTest.class.getResourceAsStream("/logging.properties"));
			Logger rl = lm.getLogger("");
			Arrays.stream(rl.getHandlers())
				.filter(h -> h instanceof ConsoleHandler)
				.forEach(rl::removeHandler);
			rl.addHandler(new JBangHandler());
		} catch (IOException e) {
			// Ignore
		}
		// CI points this at a repo it caches between runs, so the tests don't download
		// the same artifacts from Maven Central in every test JVM (which gets rate
		// limited with 429s)
		String testRepo = System.getenv("_JBANG_TEST_REPO");
		if (testRepo != null) {
			mavenTempDir = Files.createDirectories(Paths.get(testRepo).toAbsolutePath());
			sharedMavenRepo = true;
		} else {
			mavenTempDir = Files.createTempDirectory("jbang_tests_maven");
		}
		jdksTempDir = Files.createTempDirectory("jbang_tests_jdks");
		URL examplesUrl = BaseTest.class.getClassLoader().getResource(EXAMPLES_FOLDER);
		if (examplesUrl == null) {
			examplesTestFolder = Paths.get(EXAMPLES_FOLDER).toAbsolutePath();
		} else {
			examplesTestFolder = Paths.get(new File(examplesUrl.toURI()).getAbsolutePath());
		}
	}

	// Code to be run after all tests using JBangTestExecutionListener
	// Not using @AfterAll because it runs each time for each test class
	static void cleanupAfterAll() {
		if (!sharedMavenRepo) {
			Util.deletePath(mavenTempDir, true);
		}
		Util.deletePath(jdksTempDir, true);
	}

	protected CaptureResult<Integer> checkedRun(String... args) throws Exception {
		return captureOutput(() -> {
			try {
				BaseCommand cmd = JBang.parseCommand(args);
				if (cmd instanceof Run) {
					((Run) cmd).realOut = System.out;
				}
				return CommandInvoker.validateAndCall(cmd);
			} catch (RuntimeException e) {
				if (e.getCause() instanceof CommandLineParserException) {
					Util.verboseMsg("checkedRun: parseCommand failed with "
							+ e.getCause().getMessage() + ", falling back to JBang.execute()");
					return JBang.execute(args);
				}
				throw e;
			}
		});
	}

	protected <T> CaptureResult<T> captureOutput(Callable<T> func) throws Exception {
		ByteArrayOutputStream newOut = new ByteArrayOutputStream();
		PrintWriter pwOut = new PrintWriter(newOut);
		final PrintStream originalOut = System.out;
		PrintStream psOut = new PrintStream(newOut);
		System.setOut(psOut);

		ByteArrayOutputStream newErr = new ByteArrayOutputStream();
		PrintWriter pwErr = new PrintWriter(newErr);
		final PrintStream originalErr = System.err;
		PrintStream psErr = new PrintStream(newErr);
		System.setErr(psErr);

		final T result;
		String outStr, errStr;
		try {
			result = func.call();
		} finally {
			pwOut.flush();
			System.setOut(originalOut);
			outStr = newOut.toString(Charset.defaultCharset());
			System.out.println(outStr);

			pwErr.flush();
			System.setErr(originalErr);
			errStr = newErr.toString(Charset.defaultCharset());
			System.err.println(errStr);
		}

		return new CaptureResult<>(result, outStr, errStr);
	}

	/**
	 * Writes an empty artifact <code>example:artifactId:version</code>, including
	 * the <code>maven-metadata.xml</code> needed to resolve RELEASE and LATEST, to
	 * a file based Maven repository so tests can resolve it without network access.
	 */
	protected static void writeTestArtifact(Path repo, String artifactId, String version) throws IOException {
		Path dir = Files.createDirectories(repo.resolve("example").resolve(artifactId));
		Util.writeString(dir.resolve("maven-metadata.xml"),
				"<metadata><groupId>example</groupId><artifactId>" + artifactId + "</artifactId><versioning>"
						+ "<latest>" + version + "</latest><release>" + version + "</release>"
						+ "<versions><version>" + version + "</version></versions></versioning></metadata>");
		Path versionDir = Files.createDirectories(dir.resolve(version));
		Util.writeString(versionDir.resolve(artifactId + "-" + version + ".pom"),
				"<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>" + artifactId
						+ "</artifactId><version>" + version + "</version></project>");
		try (JarOutputStream jar = new JarOutputStream(
				Files.newOutputStream(versionDir.resolve(artifactId + "-" + version + ".jar")))) {
			// empty jar
		}
	}

	protected static class CaptureResult<T> {
		public final T result;
		public final String out;
		public final String err;

		CaptureResult(T result, String out, String err) {
			this.result = result;
			this.out = out;
			this.err = err;
		}

		public String normalizedOut() {
			return out.replaceAll("\\r\\n", "\n");
		}

		public String normalizedErr() {
			return err.replaceAll("\\r\\n", "\n");
		}
	}

	protected void initWireMock() {
		// Allow disabling WireMock via system property from gradle command line
		if ("false".equals(System.getProperty("jbang.test.wiremock.enable", "true"))) {
			return;
		}

		// Start a WireMock server to capture and replay any remote
		// requests JBang makes (any new code that results in additional
		// requests will result in new recordings being added to the
		// `src/test/resources/mappings` folder which can then be added
		// to the git repository. Future requests will then be replayed
		// from the recordings instead of hitting the real server.)
		globalwms = new WireMockServer(options()
			.caKeystorePath("misc/wiremock.jks")
			.caKeystorePassword("password")
			.enableBrowserProxying(true)
			.withRootDirectory("src/test/resources/wiremock")
			.dynamicPort());
		globalwms.start();
		JvmProxyConfigurer.configureFor(globalwms);
		trustWireMock();
		// System.setProperty("javax.net.ssl.trustStore", "misc/wiremock.jks");
		// System.setProperty("javax.net.ssl.trustStorePassword", "password");

		// This forces MIMA to use the WireMock server as a proxy
		// System.setProperty("aether.connector.http.useSystemProperties", "true");
		// System.setProperty("aether.connector.https.securityMode", "insecure");
	}

	/**
	 * WARNING! Using this will make Http(s)URLConnections _only_ trust the WireMock
	 * server's self-signed certificate. So connections to any other remote server
	 * will fail with an SSLHandshakeException. This only works because all the
	 * remote requests we make in our JBang code use `Http(s)URLConnection` which we
	 * proxy to WireMock while Maven uses the Apache HttpClient which doesn't use
	 * the proxy and is unaffected by this method.
	 */
	private static SSLSocketFactory previousSocketFactory;
	private static HostnameVerifier previousHostnameVerifier;

	public static void trustWireMock() {
		if (previousSocketFactory == null) {
			previousSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
			previousHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
		}
		try {
			KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
			try (FileInputStream fis = new FileInputStream("misc/wiremock.jks")) {
				trustStore.load(fis, "password".toCharArray());
			}

			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(trustStore);

			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, tmf.getTrustManagers(), null);
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			// WireMock 3.x generates per-host certificates using JDK internals that were
			// removed in Java 21+, after which it falls back to a certificate that doesn't
			// match the proxied host name. As we only trust the WireMock CA anyway, it is
			// safe to skip host name verification. Can be removed with WireMock 4.x.
			HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
		} catch (IOException | GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	private static void restoreHttpsDefaults() {
		if (previousSocketFactory != null) {
			HttpsURLConnection.setDefaultSSLSocketFactory(previousSocketFactory);
			HttpsURLConnection.setDefaultHostnameVerifier(previousHostnameVerifier);
			previousSocketFactory = null;
			previousHostnameVerifier = null;
		}
	}

	protected void cleanupWireMock() {
		if (globalwms == null) {
			return; // Nothing to clean up
		}
		globalwms.stop();
		restoreHttpsDefaults();
		if ("true".equals(System.getenv("CI"))) {
			// When running in CI, we want to fail if there are unmatched requests
			List<LoggedRequest> requests = globalwms.getAllServeEvents()
				.stream()
				.filter(se -> se.getStubMapping() == null)
				.map(ServeEvent::getRequest)
				.collect(Collectors.toList());
			if (!requests.isEmpty()) {
				throw new AssertionError("Test generated unmatched requests: "
						+ requests.stream()
							.map(LoggedRequest::getUrl)
							.collect(Collectors.joining(", ")));
			}
		} else {
			// During development, we want to record unknown requests
			globalwms.snapshotRecord(recordSpec().ignoreRepeatRequests());
		}
		JvmProxyConfigurer.restorePrevious();
		globalwms = null;
	}
}
