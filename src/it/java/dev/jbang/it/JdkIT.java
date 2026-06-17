package dev.jbang.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

public class JdkIT extends BaseIT {

	private WireMockServer wireMock;

	@BeforeEach
	void setupWireMock() {
		wireMock = new WireMockServer(com.github.tomakehurst.wiremock.core.WireMockConfiguration.options()
			.caKeystorePath("misc/wiremock.jks")
			.caKeystorePassword("password")
			.enableBrowserProxying(true)
			.withRootDirectory("src/test/resources/wiremock")
			.dynamicPort());
		wireMock.start();
	}

	@AfterEach
	void cleanupWireMock() {
		if (wireMock != null) {
			wireMock.stop();
		}
	}

	@Test
	void testJdkList() {
		CommandResult result = shell(proxyEnv(), "jbang jdk list");

		CommandResultAssert.assertThat(result)
			.succeeded()
			.outContains("Installed JDKs (<=default):")
			.outFind(Pattern.compile("^ {3}\\d+ \\([^,]+\\)$", Pattern.MULTILINE));
		assertNoDiscoPackagesRequests();
	}

	@Test
	void testJdkListWithDetails() {
		CommandResult result = shell(proxyEnv(), "jbang jdk list --details");

		CommandResultAssert.assertThat(result)
			.succeeded()
			.outContains("Installed JDKs (<=default):")
			.outFind(Pattern.compile("^ {3}\\d+ \\([^,]+, [^,]+, .+\\)$", Pattern.MULTILINE));
		assertNoDiscoPackagesRequests();
	}

	@Test
	void testJdkListAvailable() {
		CommandResult result = shell(proxyEnv(), "jbang jdk list --available");

		CommandResultAssert.assertThat(result)
			.succeeded()
			.outContains("Available JDKs:\n")
			.outFind(Pattern.compile("^ {3}\\d+ \\([^,]+\\)$", Pattern.MULTILINE));
		assertDiscoPackagesRequestsServedByWireMock();
	}

	@Test
	void testJdkListAvailableWithDetails() {
		CommandResult result = shell(proxyEnv(), "jbang jdk list --available --details");

		CommandResultAssert.assertThat(result)
			.succeeded()
			.outContains("Available JDKs:\n")
			.outFind(Pattern.compile("^ {3}\\d+ \\([^,]+, [^,]+, .+\\)$", Pattern.MULTILINE));
		assertDiscoPackagesRequestsServedByWireMock();
	}

	@Test
	void testJdkListDistros() {
		CommandResult result = shell(proxyEnv(), "jbang jdk list --distros");

		CommandResultAssert.assertThat(result)
			.succeeded()
			.outContains("Available JDK Distributions:\n")
			.outFind(Pattern.compile("^ {3}[a-z0-9_]+$", Pattern.MULTILINE));
		assertDiscoPackagesRequestsServedByWireMock();
	}

	private Map<String, String> proxyEnv() {
		Map<String, String> env = new HashMap<>();
		String trustStore = Paths.get("misc", "wiremock.jks").toAbsolutePath().toString();
		Path runRoot = scratch().resolve("jdkit-" + UUID.randomUUID());
		try {
			Files.createDirectories(runRoot);
		} catch (IOException e) {
			throw new IllegalStateException("Could not create isolated test directory: " + runRoot, e);
		}
		String jbangJavaOptions = String.join(" ",
				"-Dhttp.proxyHost=127.0.0.1",
				"-Dhttp.proxyPort=" + wireMock.port(),
				"-Dhttps.proxyHost=127.0.0.1",
				"-Dhttps.proxyPort=" + wireMock.port(),
				"-Dhttp.nonProxyHosts=",
				"-Djavax.net.ssl.trustStore=" + trustStore,
				"-Djavax.net.ssl.trustStorePassword=password");
		env.put("JBANG_JAVA_OPTIONS", jbangJavaOptions);
		env.put("JBANG_DIR", runRoot.resolve("jbang").toString());
		env.put("JBANG_REPO", runRoot.resolve("repo").toString());
		env.put("JBANG_CACHE_DIR", runRoot.resolve("cache").toString());
		return env;
	}

	private void assertDiscoPackagesRequestsServedByWireMock() {
		List<ServeEvent> discoEvents = wireMock.getAllServeEvents()
			.stream()
			.filter(event -> event.getRequest().getUrl().startsWith("/disco/v3.0/packages"))
			.collect(Collectors.toList());
		assertThat(discoEvents).isNotEmpty();
		assertThat(discoEvents).allMatch(event -> event.getStubMapping() != null);
	}

	private void assertNoDiscoPackagesRequests() {
		List<ServeEvent> discoEvents = wireMock.getAllServeEvents()
			.stream()
			.filter(event -> event.getRequest().getUrl().startsWith("/disco/v3.0/packages"))
			.collect(Collectors.toList());
		assertThat(discoEvents).isEmpty();
	}
}
