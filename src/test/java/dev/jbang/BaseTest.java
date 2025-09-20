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
import java.util.function.Function;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

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
import dev.jbang.cli.JBang;
import dev.jbang.dependencies.DependencyCache;
import dev.jbang.util.Util;

import picocli.CommandLine;

public abstract class BaseTest {
	public Path jbangTempDir;
	public Path cwdDir;
	public WireMockServer globalwms;

	public static Path mavenTempDir;
	public static Path jdksTempDir;
	public static Path examplesTestFolder;

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@BeforeEach
	void initEnv(@TempDir Path tempPath) throws IOException {
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
		mavenTempDir = Files.createTempDirectory("jbang_tests_maven");
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
		Util.deletePath(mavenTempDir, true);
		Util.deletePath(jdksTempDir, true);
	}

	protected <T> CaptureResult<Integer> checkedRun(Function<T, Integer> commandRunner, String... args)
			throws Exception {
		CommandLine cli = JBang.getCommandLine();
		args = Main.handleDefaultRun(cli.getCommandSpec(), args);
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs(args);
		while (pr.subcommand() != null) {
			pr = pr.subcommand();
		}
		@SuppressWarnings("unchecked")
		T usrobj = (T) pr.commandSpec().userObject();

		return captureOutput(() -> {
			if (commandRunner != null) {
				return commandRunner.apply(usrobj);
			} else if (usrobj instanceof BaseCommand) {
				BaseCommand cmd = ((BaseCommand) usrobj);
				cmd.realOut = System.out; // Reset the output stream to the original, just for testing
				return cmd.doCall();
			} else {
				throw new IllegalStateException("usrobj is of unsupported type");
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
	public static void trustWireMock() {
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
		} catch (IOException | GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	protected void cleanupWireMock() {
		if (globalwms == null) {
			return; // Nothing to clean up
		}
		globalwms.stop();
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
