package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class TestRunChecksum {

	@Test
	void splitRefAndChecksumParsesDigestSuffix() {
		Run.RefWithChecksum ref = Run.splitRefAndChecksum("alias@catalog#sha256:abcdef123456");
		assertEquals("alias@catalog", ref.ref);
		assertEquals("sha256:abcdef123456", ref.checksum);
	}

	@Test
	void splitRefAndChecksumLeavesUrlsWithoutDigestUntouched() {
		Run.RefWithChecksum ref = Run.splitRefAndChecksum("https://example.org/foo#frag");
		assertEquals("https://example.org/foo#frag", ref.ref);
		assertEquals(null, ref.checksum);
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
}
