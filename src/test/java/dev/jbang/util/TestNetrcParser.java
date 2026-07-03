package dev.jbang.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.util.NetrcParser.NetrcEntry;

public class TestNetrcParser {

	@TempDir
	Path tempDir;

	private NetrcParser parseString(String content) throws IOException {
		Path netrc = tempDir.resolve(".netrc");
		Files.writeString(netrc, content);
		return NetrcParser.parseContent(netrc);
	}

	@Test
	void testMultiLineFormat() throws IOException {
		NetrcParser parser = parseString(
				"machine gitlab.com\n" +
						"login __token__\n" +
						"password glpat-xxxxxxxxxxxx\n");

		Optional<NetrcEntry> entry = parser.getEntry("gitlab.com");
		assertTrue(entry.isPresent());
		assertEquals("gitlab.com", entry.get().getMachine());
		assertEquals("__token__", entry.get().getLogin());
		assertEquals("glpat-xxxxxxxxxxxx", entry.get().getPassword());
		assertFalse(entry.get().isDefault());
	}

	@Test
	void testSingleLineFormat() throws IOException {
		NetrcParser parser = parseString(
				"machine gitlab.com login __token__ password glpat-xxxxxxxxxxxx\n");

		Optional<NetrcEntry> entry = parser.getEntry("gitlab.com");
		assertTrue(entry.isPresent());
		assertEquals("__token__", entry.get().getLogin());
		assertEquals("glpat-xxxxxxxxxxxx", entry.get().getPassword());
	}

	@Test
	void testMultipleEntries() throws IOException {
		NetrcParser parser = parseString(
				"machine gitlab.com\n" +
						"login __token__\n" +
						"password glpat-aaaa\n" +
						"\n" +
						"machine gitlab.mycompany.com\n" +
						"login __token__\n" +
						"password glpat-bbbb\n" +
						"\n" +
						"machine github.com\n" +
						"login __token__\n" +
						"password ghp-cccc\n");

		Optional<NetrcEntry> gitlab = parser.getEntry("gitlab.com");
		assertTrue(gitlab.isPresent());
		assertEquals("glpat-aaaa", gitlab.get().getPassword());

		Optional<NetrcEntry> custom = parser.getEntry("gitlab.mycompany.com");
		assertTrue(custom.isPresent());
		assertEquals("glpat-bbbb", custom.get().getPassword());

		Optional<NetrcEntry> github = parser.getEntry("github.com");
		assertTrue(github.isPresent());
		assertEquals("ghp-cccc", github.get().getPassword());
	}

	@Test
	void testDefaultEntry() throws IOException {
		NetrcParser parser = parseString(
				"machine gitlab.com\n" +
						"login __token__\n" +
						"password glpat-specific\n" +
						"\n" +
						"default\n" +
						"login anonymous\n" +
						"password user@example.com\n");

		// Exact match returns specific entry
		Optional<NetrcEntry> gitlab = parser.getEntry("gitlab.com");
		assertTrue(gitlab.isPresent());
		assertEquals("glpat-specific", gitlab.get().getPassword());
		assertFalse(gitlab.get().isDefault());

		// Unknown host falls back to default
		Optional<NetrcEntry> unknown = parser.getEntry("unknown.example.com");
		assertTrue(unknown.isPresent());
		assertEquals("anonymous", unknown.get().getLogin());
		assertEquals("user@example.com", unknown.get().getPassword());
		assertTrue(unknown.get().isDefault());
	}

	@Test
	void testNoMatch() throws IOException {
		NetrcParser parser = parseString(
				"machine gitlab.com\n" +
						"login __token__\n" +
						"password glpat-xxxx\n");

		Optional<NetrcEntry> entry = parser.getEntry("unknown.example.com");
		assertFalse(entry.isPresent());
	}

	@Test
	void testCaseInsensitiveHostMatch() throws IOException {
		NetrcParser parser = parseString(
				"machine GitLab.COM\n" +
						"login __token__\n" +
						"password glpat-xxxx\n");

		Optional<NetrcEntry> entry = parser.getEntry("gitlab.com");
		assertTrue(entry.isPresent());
		assertEquals("glpat-xxxx", entry.get().getPassword());
	}

	@Test
	void testCommentsAreSkipped() throws IOException {
		NetrcParser parser = parseString(
				"# This is a comment\n" +
						"machine gitlab.com\n" +
						"# login comment\n" +
						"login __token__\n" +
						"password glpat-xxxx\n");

		Optional<NetrcEntry> entry = parser.getEntry("gitlab.com");
		assertTrue(entry.isPresent());
		assertEquals("__token__", entry.get().getLogin());
		assertEquals("glpat-xxxx", entry.get().getPassword());
	}

	@Test
	void testEmptyFile() throws IOException {
		NetrcParser parser = parseString("");

		Optional<NetrcEntry> entry = parser.getEntry("gitlab.com");
		assertFalse(entry.isPresent());
	}

	@Test
	void testNonExistentFile() {
		NetrcParser parser = NetrcParser.parse(tempDir.resolve("nonexistent"));

		Optional<NetrcEntry> entry = parser.getEntry("gitlab.com");
		assertFalse(entry.isPresent());
	}

	@Test
	void testPasswordOnly() throws IOException {
		NetrcParser parser = parseString(
				"machine gitlab.com\n" +
						"password glpat-xxxx\n");

		Optional<NetrcEntry> entry = parser.getEntry("gitlab.com");
		assertTrue(entry.isPresent());
		assertNull(entry.get().getLogin());
		assertEquals("glpat-xxxx", entry.get().getPassword());
	}

	@Test
	void testAccountFieldIsIgnored() throws IOException {
		NetrcParser parser = parseString(
				"machine gitlab.com\n" +
						"login __token__\n" +
						"account someaccount\n" +
						"password glpat-xxxx\n");

		Optional<NetrcEntry> entry = parser.getEntry("gitlab.com");
		assertTrue(entry.isPresent());
		assertEquals("__token__", entry.get().getLogin());
		assertEquals("glpat-xxxx", entry.get().getPassword());
	}

	@Test
	void testMixedSingleAndMultiLine() throws IOException {
		NetrcParser parser = parseString(
				"machine gitlab.com login __token__ password glpat-aaaa\n" +
						"machine github.com\n" +
						"login __token__\n" +
						"password ghp-bbbb\n");

		Optional<NetrcEntry> gitlab = parser.getEntry("gitlab.com");
		assertTrue(gitlab.isPresent());
		assertEquals("glpat-aaaa", gitlab.get().getPassword());

		Optional<NetrcEntry> github = parser.getEntry("github.com");
		assertTrue(github.isPresent());
		assertEquals("ghp-bbbb", github.get().getPassword());
	}

	@Test
	void testQuotedPasswordWithSpaces() throws IOException {
		NetrcParser parser = parseString(
				"machine example.com\n" +
						"login myuser\n" +
						"password \"my secret password\"\n");

		Optional<NetrcEntry> entry = parser.getEntry("example.com");
		assertTrue(entry.isPresent());
		assertEquals("myuser", entry.get().getLogin());
		assertEquals("my secret password", entry.get().getPassword());
	}

	@Test
	void testQuotedPasswordWithEscapedBackslash() throws IOException {
		NetrcParser parser = parseString(
				"machine example.com\n" +
						"login myuser\n" +
						"password \"pass\\\\word\"\n");

		Optional<NetrcEntry> entry = parser.getEntry("example.com");
		assertTrue(entry.isPresent());
		assertEquals("pass\\word", entry.get().getPassword());
	}

	@Test
	void testQuotedPasswordWithEscapedQuote() throws IOException {
		// wget-style: backslash-escaped double quote inside double-quoted string
		NetrcParser parser = parseString(
				"machine example.com\n" +
						"login myuser\n" +
						"password \"pass\\\"word\"\n");

		Optional<NetrcEntry> entry = parser.getEntry("example.com");
		assertTrue(entry.isPresent());
		assertEquals("pass\"word", entry.get().getPassword());
	}

	@Test
	void testQuotedLoginAndPassword() throws IOException {
		NetrcParser parser = parseString(
				"machine example.com login \"my user\" password \"my pass\"\n");

		Optional<NetrcEntry> entry = parser.getEntry("example.com");
		assertTrue(entry.isPresent());
		assertEquals("my user", entry.get().getLogin());
		assertEquals("my pass", entry.get().getPassword());
	}

	@Test
	void testQuotedPasswordOnSingleLineEntry() throws IOException {
		NetrcParser parser = parseString(
				"machine host1.com login user1 password \"p a s s\"\n" +
						"machine host2.com login user2 password plain\n");

		Optional<NetrcEntry> h1 = parser.getEntry("host1.com");
		assertTrue(h1.isPresent());
		assertEquals("p a s s", h1.get().getPassword());

		Optional<NetrcEntry> h2 = parser.getEntry("host2.com");
		assertTrue(h2.isPresent());
		assertEquals("plain", h2.get().getPassword());
	}

	@Test
	void testMacdefIsSkipped() throws IOException {
		NetrcParser parser = parseString(
				"macdef init\n" +
						"cd /pub\n" +
						"get README\n" +
						"quit\n" +
						"\n" +
						"machine ftp.example.com\n" +
						"login myuser\n" +
						"password mypass\n");

		Optional<NetrcEntry> entry = parser.getEntry("ftp.example.com");
		assertTrue(entry.isPresent());
		assertEquals("myuser", entry.get().getLogin());
		assertEquals("mypass", entry.get().getPassword());
	}

	@Test
	void testMacdefBodyWithKeywordWords() throws IOException {
		// macro body contains words like "machine" and "login" that
		// must not confuse the parser
		NetrcParser parser = parseString(
				"macdef upload\n" +
						"machine is a keyword\n" +
						"login should be ignored\n" +
						"password also ignored\n" +
						"\n" +
						"machine real.example.com\n" +
						"login realuser\n" +
						"password realpass\n");

		// The "machine" inside macdef body must not create an entry
		Optional<NetrcEntry> fake = parser.getEntry("is");
		assertFalse(fake.isPresent());

		Optional<NetrcEntry> real = parser.getEntry("real.example.com");
		assertTrue(real.isPresent());
		assertEquals("realuser", real.get().getLogin());
		assertEquals("realpass", real.get().getPassword());
	}

	@Test
	void testQuotedPasswordWithEscapedSpace() throws IOException {
		// wget-style: backslash-space inside quoted string
		NetrcParser parser = parseString(
				"machine example.com\n" +
						"login myuser\n" +
						"password \"hello\\ world\"\n");

		Optional<NetrcEntry> entry = parser.getEntry("example.com");
		assertTrue(entry.isPresent());
		assertEquals("hello world", entry.get().getPassword());
	}
}
