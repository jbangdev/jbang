package dev.jbang.util;

import static dev.jbang.util.Util.verboseMsg;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Parser for {@code .netrc} credential files.
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
		private final String jbangAuth;
		private final String jbangAuthHost;
		private final String jbangAuthScheme;
		private final boolean isDefault;

		NetrcEntry(String machine, String login, String password, String jbangAuth, String jbangAuthHost,
				String jbangAuthScheme, boolean isDefault) {
			this.machine = machine;
			this.login = login;
			this.password = password;
			this.jbangAuth = jbangAuth;
			this.jbangAuthHost = jbangAuthHost;
			this.jbangAuthScheme = jbangAuthScheme;
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

		/**
		 * Returns the value of the JBang-specific {@code jbang-auth} key, or
		 * {@code null} if not present. The value is a comma-separated list of auth
		 * methods tried in order. Recognised methods:
		 * <ul>
		 * <li>{@code git-credential} — delegate to {@code git credential fill}</li>
		 * <li>{@code gh-auth} — delegate to {@code gh auth token}</li>
		 * <li>{@code glab-auth} — delegate to {@code glab config get token}</li>
		 * <li>{@code env.NAME} — read the {@code NAME} environment variable</li>
		 * </ul>
		 */
		public String getJbangAuth() {
			return jbangAuth;
		}

		/**
		 * Returns the value of the JBang-specific {@code jbang-auth-host} key, or
		 * {@code null} if not present. When set, auth methods use this host instead of
		 * the entry's {@code machine} for credential lookups.
		 */
		public String getJbangAuthHost() {
			return jbangAuthHost;
		}

		/**
		 * Returns the HTTP auth scheme for this entry. Defaults to Basic auth.
		 */
		public String getJbangAuthScheme() {
			return jbangAuthScheme;
		}

		public boolean isDefault() {
			return isDefault;
		}
	}

	private final List<NetrcEntry> entries;

	NetrcParser(List<NetrcEntry> entries) {
		this.entries = Collections.unmodifiableList(entries);
	}

	static NetrcParser empty() {
		return new NetrcParser(Collections.emptyList());
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
	 * Returns the default netrc file path: {@code ~/.netrc}.
	 */
	public static Path getDefaultNetrcPath() {
		return Paths.get(System.getProperty("user.home")).resolve(".netrc");
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
			verboseMsg("No netrc file found at: " + path);
			return empty();
		}
		try {
			if (!hasSafePermissions(path)) {
				verboseMsg("Ignoring netrc file with unsafe permissions: " + path);
				return empty();
			}
			return parseContent(path);
		} catch (IOException e) {
			verboseMsg("Failed to read netrc file: " + path, e);
			return empty();
		}
	}

	private static boolean hasSafePermissions(Path path) throws IOException {
		try {
			Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
			return !permissions.contains(PosixFilePermission.GROUP_READ)
					&& !permissions.contains(PosixFilePermission.GROUP_WRITE)
					&& !permissions.contains(PosixFilePermission.GROUP_EXECUTE)
					&& !permissions.contains(PosixFilePermission.OTHERS_READ)
					&& !permissions.contains(PosixFilePermission.OTHERS_WRITE)
					&& !permissions.contains(PosixFilePermission.OTHERS_EXECUTE);
		} catch (UnsupportedOperationException e) {
			verboseMsg("Netrc permission check not supported on this file system: " + path);
			return true;
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
				i = parseEntryFields(tokens, i + 2, entries, machine, false);
			} else if ("default".equals(token)) {
				i = parseEntryFields(tokens, i + 1, entries, null, true);
			} else {
				i++;
			}
		}

		return new NetrcParser(entries);
	}

	private static int parseEntryFields(List<String> tokens, int i,
			List<NetrcEntry> entries, String machine, boolean isDefault) {
		String login = null;
		String password = null;
		String jbangAuth = null;
		String jbangAuthHost = null;
		String jbangAuthScheme = null;
		while (i < tokens.size()) {
			String key = tokens.get(i);
			if ("login".equals(key) && i + 1 < tokens.size()) {
				login = tokens.get(i + 1);
				i += 2;
			} else if ("password".equals(key) && i + 1 < tokens.size()) {
				password = tokens.get(i + 1);
				i += 2;
			} else if ("jbang-auth".equals(key) && i + 1 < tokens.size()) {
				jbangAuth = tokens.get(i + 1);
				i += 2;
			} else if ("jbang-auth-host".equals(key) && i + 1 < tokens.size()) {
				jbangAuthHost = tokens.get(i + 1);
				i += 2;
			} else if ("jbang-auth-scheme".equals(key) && i + 1 < tokens.size()) {
				jbangAuthScheme = tokens.get(i + 1);
				i += 2;
			} else if ("account".equals(key) && i + 1 < tokens.size()) {
				i += 2; // account is part of the spec but we don't use it
			} else if ("machine".equals(key) || "default".equals(key) || "macdef".equals(key)) {
				break;
			} else {
				i++;
			}
		}
		entries.add(new NetrcEntry(machine, login, password, jbangAuth, jbangAuthHost, jbangAuthScheme, isDefault));
		return i;
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
