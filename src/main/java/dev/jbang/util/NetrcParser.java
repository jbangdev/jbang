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
import java.util.StringTokenizer;

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
			} else if ("macdef".equals(token)) {
				// Skip macro definitions — skip until blank line
				i += 2;
				// macdef content ends at a blank line but since we tokenize
				// by whitespace we just skip until next keyword
				while (i < tokens.size()) {
					String key = tokens.get(i);
					if ("machine".equals(key) || "default".equals(key) || "macdef".equals(key)) {
						break;
					}
					i++;
				}
			} else {
				i++;
			}
		}

		return new NetrcParser(entries);
	}

	private static List<String> tokenize(Path path) throws IOException {
		List<String> tokens = new ArrayList<>();
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String line;
			while ((line = reader.readLine()) != null) {
				// Skip comments
				line = line.trim();
				if (line.startsWith("#")) {
					continue;
				}
				StringTokenizer st = new StringTokenizer(line);
				while (st.hasMoreTokens()) {
					tokens.add(st.nextToken());
				}
			}
		}
		return tokens;
	}
}
