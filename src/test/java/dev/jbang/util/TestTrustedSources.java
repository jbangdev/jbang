package dev.jbang.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

/**
 * Both class A and person.B have //SOURCES model/C.java
 */
public class TestTrustedSources extends BaseTest {

	@Test
	void testGoodUrlsToTrust() throws IOException {
		assertEquals("https://github.com/maxandersen/myproject/",
				Util.goodTrustURL("https://github.com/maxandersen/myproject/blob/master/file.java"));

		assertEquals("https://repo1.maven.org/maven2/io/quarkus/quarkus-cli/",
				Util.goodTrustURL(
						"https://repo1.maven.org/maven2/io/quarkus/quarkus-cli/2.0.0.Final/quarkus-cli-2.0.0.Final-runner.jar"));

		assertEquals("https://github.com/t.java",
				Util.goodTrustURL("https://github.com/t.java"));

		assertEquals("https://github.com/",
				Util.goodTrustURL("https://github.com/"));

		assertEquals("https://acme.org",
				Util.goodTrustURL("https://acme.org"));

		assertEquals("https://gist.github.com/maxandersen/",
				Util.goodTrustURL("https://gist.github.com/maxandersen/d4e465ab26ae5d85b7090aecf4003dc1"));

		assertEquals("https://gist.github.com/d4e465ab26ae5d85b7090aecf4003dc1",
				Util.goodTrustURL("https://gist.github.com/d4e465ab26ae5d85b7090aecf4003dc1"));

	}

}
