package dev.jbang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class TestMain extends BaseTest {

	@AfterEach
	void cleanupSystemProperties() {
		System.clearProperty("test.proxy.host");
		System.clearProperty("test.proxy.port");
		System.clearProperty("test.flag.only");
		System.clearProperty("test.proxyHost");
		System.clearProperty("test.proxyPort");
		System.clearProperty("test.nonProxyHosts");
	}

	@Test
	void testApplySystemPropertiesFromEnvDoesNothingWhenEmpty() {
		environmentVariables.set("JBANG_JAVA_OPTIONS", null);
		Main.applySystemPropertiesFromEnv();
		// No exception, no property set
		assertThat(System.getProperty("test.proxy.host"), nullValue());
	}

	@Test
	void testApplySystemPropertiesFromEnvAppliesProperties() {
		environmentVariables.set("JBANG_JAVA_OPTIONS",
				"-Dtest.proxy.host=127.0.0.1 -Dtest.proxy.port=8080");
		Main.applySystemPropertiesFromEnv();
		assertThat(System.getProperty("test.proxy.host"), equalTo("127.0.0.1"));
		assertThat(System.getProperty("test.proxy.port"), equalTo("8080"));
	}

	@Test
	void testApplySystemPropertiesFromEnvDoesNotOverrideExisting() {
		System.setProperty("test.proxy.host", "original");
		environmentVariables.set("JBANG_JAVA_OPTIONS", "-Dtest.proxy.host=override");
		Main.applySystemPropertiesFromEnv();
		assertThat(System.getProperty("test.proxy.host"), equalTo("original"));
	}

	@Test
	void testApplySystemPropertiesFromEnvHandlesEmptyValue() {
		environmentVariables.set("JBANG_JAVA_OPTIONS", "-Dtest.nonProxyHosts=");
		Main.applySystemPropertiesFromEnv();
		assertThat(System.getProperty("test.nonProxyHosts"), equalTo(""));
	}

	@Test
	void testApplySystemPropertiesFromEnvHandlesFlagWithoutValue() {
		environmentVariables.set("JBANG_JAVA_OPTIONS", "-Dtest.flag.only");
		Main.applySystemPropertiesFromEnv();
		assertThat(System.getProperty("test.flag.only"), equalTo(""));
	}

	@Test
	void testApplySystemPropertiesFromEnvIgnoresNonDFlags() {
		environmentVariables.set("JBANG_JAVA_OPTIONS",
				"-Xmx512m -Dtest.proxy.host=127.0.0.1 -verbose:gc");
		Main.applySystemPropertiesFromEnv();
		assertThat(System.getProperty("test.proxy.host"), equalTo("127.0.0.1"));
	}

	@Test
	void testApplySystemPropertiesFromEnvProxySettings() {
		environmentVariables.set("JBANG_JAVA_OPTIONS",
				"-Dtest.proxyHost=127.0.0.1 -Dtest.proxyPort=9999"
						+ " -Dtest.nonProxyHosts=");
		Main.applySystemPropertiesFromEnv();
		assertThat(System.getProperty("test.proxyHost"), equalTo("127.0.0.1"));
		assertThat(System.getProperty("test.proxyPort"), equalTo("9999"));
		assertThat(System.getProperty("test.nonProxyHosts"), equalTo(""));
	}
}
