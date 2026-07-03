package dev.jbang.util;

import static dev.jbang.util.Util.verboseMsg;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Parser for {@code .netrc} / {@code _netrc} credential files.
 * <p>
 * Supports the standard format used by curl, wget, git, and others:
 *
 * <pre>
 * machine gitlab.com
 * login __token__
 * password glpat-xxxxxxxxxxxx
 *
 * machine github.com
 * login __token__
 * password ghp_xxxxxxxxxxxx
 *
 * default
 * login anonymous
 * password user@example.com
 * </pre>
 *
 * Single-line entries are also supported:
 *
 * <pre>
 * machine gitlab.com login __token__ password glpat-xxxxxxxxxxxx
 * </pre>
 */
public class NetrcParser {

	/**
	 * A single entry from a {@code .netrc} file.
	 */
	public static class NetrcEntry {
		private final String machine;
		private final String login;
		private final String password;
		private final boolean isDefault;

		NetrcEntry(String machine, String login, String password, boolean isDefault) {
			this.machine = machine;
			this.login = login;
			this.password = password;
			this.isDefault = isDefault;
		}

		public String getMachine() {
			return machine;
		}

		public String getLogin() {
			return login;
		}

		public String getPassword() {
			return password;
		}

		public boolean isDefault() {
			return isDefault;
		}
	}

	private final List<NetrcEntry> entries;

	NetrcParser(List<NetrcEntry> entries) {
		this.entries = Collections.unmodifiableList(entries);
	}

	/**
	 * Find the entry matching the given hostname. Returns the exact match first, or
	 * the {@code default} entry if no exact match exists.
	 *
	 * @param hostname the hostname to look up
	 * @return the matching entry, or empty if none found
	 */
	public Optional<NetrcEntry> getEntry(String hostname) {
		NetrcEntry defaultEntry = null;
		for (NetrcEntry entry : entries) {
			if (entry.isDefault()) {
				defaultEntry = entry;
			} else if (hostname.equalsIgnoreCase(entry.getMachine())) {
				return Optional.of(entry);
			}
		}
		return Optional.ofNullable(defaultEntry);
	}

	/**
	 * Returns the default netrc file path for the current platform.
	 * {@code ~/.netrc} on Unix/macOS, {@code ~/_netrc} on Windows.
	 */
	public static Path getDefaultNetrcPath() {
		Path home = Paths.get(System.getProperty("user.home"));
		if (Util.isWindows()) {
			return home.resolve("_netrc");
		}
		return home.resolve(".netrc");
	}

	/**
	 * Parse the default {@code .netrc} file. Returns an empty parser if the file
	 * does not exist or cannot be read.
	 */
	public static NetrcParser parseDefault() {
		return parse(getDefaultNetrcPath());
	}

	/**
	 * Parse a {@code .netrc} file at the given path. Returns an empty parser if the
	 * file does not exist or cannot be read.
	 */
	public static NetrcParser parse(Path path) {
		if (!Files.isRegularFile(path)) {
			return new NetrcParser(Collections.emptyList());
		}
		try {
			return parseContent(path);
		} catch (IOException e) {
			verboseMsg("Failed to read netrc file: " + path, e);
			return new NetrcParser(Collections.emptyList());
		}
	}

	static NetrcParser parseContent(Path path) throws IOException {
		List<String> tokens = tokenize(path);
		List<NetrcEntry> entries = new ArrayList<>();

		int i = 0;
		while (i < tokens.size()) {
			String token = tokens.get(i);
			if ("machine".equals(token)) {
				if (i + 1 >= tokens.size()) {
					break;
				}
				String machine = tokens.get(i + 1);
				i += 2;
				String login = null;
				String password = null;
				while (i < tokens.size()) {
					String key = tokens.get(i);
					if ("login".equals(key) && i + 1 < tokens.size()) {
						login = tokens.get(i + 1);
						i += 2;
					} else if ("password".equals(key) && i + 1 < tokens.size()) {
						password = tokens.get(i + 1);
						i += 2;
					} else if ("account".equals(key) && i + 1 < tokens.size()) {
						// account is part of the spec but we don't use it
						i += 2;
					} else if ("machine".equals(key) || "default".equals(key) || "macdef".equals(key)) {
						// Start of next entry
						break;
					} else {
						i++;
					}
				}
				entries.add(new NetrcEntry(machine, login, password, false));
			} else if ("default".equals(token)) {
				i++;
				String login = null;
				String password = null;
				while (i < tokens.size()) {
					String key = tokens.get(i);
					if ("login".equals(key) && i + 1 < tokens.size()) {
						login = tokens.get(i + 1);
						i += 2;
					} else if ("password".equals(key) && i + 1 < tokens.size()) {
						password = tokens.get(i + 1);
						i += 2;
					} else if ("account".equals(key) && i + 1 < tokens.size()) {
						i += 2;
					} else if ("machine".equals(key) || "default".equals(key) || "macdef".equals(key)) {
						break;
					} else {
						i++;
					}
				}
				entries.add(new NetrcEntry(null, login, password, true));
			} else {
				i++;
			}
		}

		return new NetrcParser(entries);
	}

	private static List<String> tokenize(Path path) throws IOException {
		List<String> tokens = new ArrayList<>();
		boolean inMacdef = false;
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (inMacdef) {
					// macdef body ends at the first blank line
					if (line.trim().isEmpty()) {
						inMacdef = false;
					}
					continue;
				}
				line = line.trim();
				// Skip comments
				if (line.startsWith("#")) {
					continue;
				}
				// Detect macdef — consume name token, then skip body
				if (line.startsWith("macdef") && (line.length() == 6 || Character.isWhitespace(line.charAt(6)))) {
					inMacdef = true;
					continue;
				}
				tokenizeLine(line, tokens);
			}
		}
		return tokens;
	}

	/**
	 * Tokenizes a single line, supporting wget/curl-style double-quoted strings
	 * with backslash escaping. Inside a quoted token, {@code \"} produces a literal
	 * double-quote, {@code \\} a literal backslash, and any other {@code \X}
	 * produces literal {@code X} (including {@code \ } for space). Unquoted tokens
	 * are split on whitespace as before.
	 */
	private static void tokenizeLine(String line, List<String> tokens) {
		int len = line.length();
		int i = 0;
		while (i < len) {
			// Skip whitespace
			while (i < len && Character.isWhitespace(line.charAt(i))) {
				i++;
			}
			if (i >= len) {
				break;
			}
			if (line.charAt(i) == '"') {
				// Quoted token — consume until closing quote, handling escapes
				i++; // skip opening quote
				StringBuilder sb = new StringBuilder();
				while (i < len && line.charAt(i) != '"') {
					if (line.charAt(i) == '\\' && i + 1 < len) {
						i++; // skip backslash, take next char literally
					}
					sb.append(line.charAt(i));
					i++;
				}
				if (i < len) {
					i++; // skip closing quote
				}
				tokens.add(sb.toString());
			} else {
				// Unquoted token — consume until whitespace
				int start = i;
				while (i < len && !Character.isWhitespace(line.charAt(i))) {
					i++;
				}
				tokens.add(line.substring(start, i));
			}
		}
	}
}
