package dev.jbang.cli.completion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Provides tab-completion for GitHub blob/tree URLs by calling the GitHub
 * Contents API to list files and directories.
 * <p>
 * Supports URLs of the form:
 * 
 * <pre>
 *   https://github.com/{owner}/{repo}/blob/{branch}/{path...}
 * </pre>
 * 
 * Results are filtered to JBang-supported file extensions and directories.
 */
class GitHubCompletionProvider {

	/** Optional https:// prefix — we accept bare github.com/ too. */
	private static final String GITHUB_PREFIX = "(?:https?://)?github\\.com";

	/** Matches a GitHub blob/tree URL and captures owner, repo, branch, path. */
	private static final Pattern GITHUB_URL_PATTERN = Pattern.compile(
			"^(" + GITHUB_PREFIX + "/([^/]+)/([^/]+)/(?:blob|tree)/([^/]+))(/(.*))?$");

	/**
	 * Matches a GitHub repo URL without blob/tree/branch — we default to using HEAD
	 * ref and list from the root. Trailing slash is optional.
	 */
	private static final Pattern GITHUB_REPO_PATTERN = Pattern.compile(
			"^(" + GITHUB_PREFIX + "/([^/]+)/([^/]+))/?$");

	/** Connection timeout in milliseconds. */
	private static final int CONNECT_TIMEOUT_MS = 2000;

	/** Read timeout in milliseconds. */
	private static final int READ_TIMEOUT_MS = 3000;

	/** Maximum cache entries. */
	private static final int MAX_CACHE_ENTRIES = 50;

	/** Cache TTL in milliseconds (5 minutes). */
	private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

	/**
	 * Simple LRU + TTL cache for directory listings keyed by API URL.
	 */
	private static final Map<String, CacheEntry> CACHE = new LinkedHashMap<String, CacheEntry>(
			MAX_CACHE_ENTRIES + 1, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
			return size() > MAX_CACHE_ENTRIES;
		}
	};

	/**
	 * Returns true if the partial input looks like a GitHub URL that we can
	 * complete.
	 */
	static boolean canComplete(String partial) {
		return GITHUB_URL_PATTERN.matcher(partial).find()
				|| GITHUB_REPO_PATTERN.matcher(partial).find();
	}

	/**
	 * List completion candidates for the given partial GitHub URL.
	 *
	 * @param candidates set to add candidates to
	 * @param partial    the partial URL typed so far
	 * @param extensions set of file extensions to include (lowercase, no dot)
	 */
	static void complete(Set<String> candidates, String partial,
			Set<String> extensions) {
		String baseUrl;
		String owner;
		String repo;
		String branch;
		String path;

		Matcher m = GITHUB_URL_PATTERN.matcher(partial);
		if (m.find()) {
			baseUrl = ensureHttps(m.group(1)); // up to and including branch
			owner = m.group(2);
			repo = m.group(3);
			branch = m.group(4);
			String pathWithSlash = m.group(5); // "/some/path" or null
			path = m.group(6); // "some/path" or null

			if (pathWithSlash == null) {
				// User typed up to branch but no slash yet
				return;
			}
			if (path == null) {
				path = "";
			}
		} else {
			// Try the short repo URL pattern (no blob/branch)
			Matcher rm = GITHUB_REPO_PATTERN.matcher(partial);
			if (!rm.find()) {
				return;
			}
			owner = rm.group(2);
			repo = rm.group(3);
			branch = "HEAD";
			baseUrl = ensureHttps(rm.group(1)) + "/blob/" + branch;
			path = "";
		}

		// Split into directory part and name prefix
		String dirPath;
		String namePrefix;
		int lastSlash = path.lastIndexOf('/');
		if (lastSlash >= 0) {
			dirPath = path.substring(0, lastSlash);
			namePrefix = path.substring(lastSlash + 1);
		} else {
			dirPath = "";
			namePrefix = path;
		}

		List<GitHubEntry> entries = fetchContents(owner, repo, branch, dirPath);
		if (entries == null) {
			return;
		}

		String urlPrefix = baseUrl + "/" + (dirPath.isEmpty() ? "" : dirPath + "/");

		for (GitHubEntry entry : entries) {
			if (!entry.name.toLowerCase().startsWith(namePrefix.toLowerCase())) {
				continue;
			}
			if ("dir".equals(entry.type)) {
				candidates.add(urlPrefix + entry.name + "/");
			} else if (hasMatchingExtension(entry.name, extensions)) {
				candidates.add(urlPrefix + entry.name);
			}
		}
	}

	// ---- GitHub API ----------------------------------------------------

	/**
	 * Fetch the contents of a directory in a GitHub repo.
	 *
	 * @return list of entries, or null on error/timeout
	 */
	static List<GitHubEntry> fetchContents(String owner, String repo,
			String branch, String path) {
		String apiUrl = buildApiUrl(owner, repo, path, branch);

		// Check cache
		CacheEntry cached = CACHE.get(apiUrl);
		if (cached != null && !cached.isExpired()) {
			return cached.entries;
		}

		try {
			URL url = new URL(apiUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
			conn.setReadTimeout(READ_TIMEOUT_MS);
			conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
			conn.setRequestProperty("User-Agent", "jbang");

			// Use GITHUB_TOKEN if available
			String token = System.getenv("GITHUB_TOKEN");
			if (token == null || token.isEmpty()) {
				token = System.getenv("GH_TOKEN");
			}
			if (token != null && !token.isEmpty()) {
				conn.setRequestProperty("Authorization", "token " + token);
			}

			int status = conn.getResponseCode();
			if (status != 200) {
				conn.disconnect();
				// Cache the failure briefly to avoid hammering
				CACHE.put(apiUrl, new CacheEntry(Collections.emptyList()));
				return null;
			}

			String body;
			try (BufferedReader rdr = new BufferedReader(
					new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
				body = rdr.lines().collect(Collectors.joining("\n"));
			}
			conn.disconnect();

			Gson gson = new Gson();
			List<Map<String, Object>> raw = gson.fromJson(body,
					new TypeToken<List<Map<String, Object>>>() {
					}.getType());

			List<GitHubEntry> entries = new ArrayList<>();
			if (raw != null) {
				for (Map<String, Object> item : raw) {
					String name = (String) item.get("name");
					String type = (String) item.get("type");
					if (name != null && type != null) {
						entries.add(new GitHubEntry(name, type));
					}
				}
			}

			CACHE.put(apiUrl, new CacheEntry(entries));
			return entries;

		} catch (IOException e) {
			// Timeout or network error — fail silently
			return null;
		}
	}

	/**
	 * Ensure the URL starts with {@code https://}. Bare {@code github.com/} gets
	 * the prefix prepended.
	 */
	private static String ensureHttps(String url) {
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			return "https://" + url;
		}
		return url;
	}

	static String buildApiUrl(String owner, String repo, String path,
			String branch) {
		String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo
				+ "/contents";
		if (path != null && !path.isEmpty()) {
			apiUrl += "/" + path;
		}
		apiUrl += "?ref=" + branch;
		return apiUrl;
	}

	private static boolean hasMatchingExtension(String name,
			Set<String> extensions) {
		int dot = name.lastIndexOf('.');
		if (dot < 0) {
			return false;
		}
		return extensions.contains(name.substring(dot + 1).toLowerCase());
	}

	// ---- Data classes --------------------------------------------------

	static class GitHubEntry {
		final String name;
		final String type; // "file" or "dir"

		GitHubEntry(String name, String type) {
			this.name = name;
			this.type = type;
		}
	}

	private static class CacheEntry {
		final List<GitHubEntry> entries;
		final long timestamp;

		CacheEntry(List<GitHubEntry> entries) {
			this.entries = entries;
			this.timestamp = System.currentTimeMillis();
		}

		boolean isExpired() {
			return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
		}
	}
}
