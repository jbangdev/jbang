package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class TestRunChecksum {

	@Test
	void splitRefAndChecksumParsesDigestSuffix() {
		Run.RefWithChecksum ref = Run.splitRefAndChecksum("alias@catalog#sha256:abcdef123456");
		assertEquals("alias@catalog", ref.ref);
		assertEquals("sha256:abcdef123456", ref.checksum);
	}

	@Test
	void splitRefAndChecksumTreatsBareSuffixAsSha256Prefix() {
		Run.RefWithChecksum ref = Run.splitRefAndChecksum("env@jbangdev#blanah");
		assertEquals("env@jbangdev", ref.ref);
		assertEquals("sha256:blanah", ref.checksum);
	}

	@Test
	void verifyDigestSpecAcceptsPrefix() {
		Run.verifyDigestSpec("sha256:abcdef1234567890", "sha256:abcdef123456", "test");
	}

	@Test
	void verifyDigestSpecRejectsShortPrefix() {
		assertThrows(ExitException.class,
				() -> Run.verifyDigestSpec("sha256:abcdef1234567890", "sha256:abcd", "test"));
	}

	@Test
	void verifyDigestSpecRejectsMismatch() {
		assertThrows(ExitException.class,
				() -> Run.verifyDigestSpec("sha256:abcdef1234567890", "sha256:bbbbbbbbbbbb", "test"));
	}

	@Test
	void verifyLockedSetRejectsDependencyDrift() {
		Set<String> expected = new LinkedHashSet<>();
		expected.add("a:b:jar:1");
		expected.add("x:y:jar:2");
		Set<String> actual = new LinkedHashSet<>();
		actual.add("a:b:jar:1");
		actual.add("x:y:jar:3");
		ExitException ex = assertThrows(ExitException.class,
				() -> Run.verifyLockedSet("dependency graph", "demo@cat", expected, actual));
		assertTrue(ex.getMessage().contains("Locked dependency graph mismatch for demo@cat"));
	}

	@Test
	void verifyLockedSetRejectsSourcesDrift() {
		Set<String> expected = new LinkedHashSet<>();
		expected.add("https://example/a.java");
		Set<String> actual = new LinkedHashSet<>();
		actual.add("https://example/b.java");
		ExitException ex = assertThrows(ExitException.class,
				() -> Run.verifyLockedSet("sources", "env@jbangdev", expected, actual));
		assertTrue(ex.getMessage().contains("Locked sources mismatch for env@jbangdev"));
	}
}
