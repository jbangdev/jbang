package dev.jbang.source;

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
				ResourceRef.goodTrustURL("https://github.com/maxandersen/myproject/blob/master/file.java"));

		assertEquals("https://repo1.maven.org/maven2/io/quarkus/quarkus-cli/",
				ResourceRef.goodTrustURL(
						"https://repo1.maven.org/maven2/io/quarkus/quarkus-cli/2.0.0.Final/quarkus-cli-2.0.0.Final-runner.jar"));

		assertEquals("https://github.com/",
				ResourceRef.goodTrustURL("https://github.com/t.java"));

		assertEquals("https://github.com/",
				ResourceRef.goodTrustURL("https://github.com/"));

		assertEquals("https://acme.org",
				ResourceRef.goodTrustURL("https://acme.org"));

	}

}
