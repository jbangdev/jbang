package dev.jbang.util;

import static org.junit.jupiter.api.Assertions.*;

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
}
