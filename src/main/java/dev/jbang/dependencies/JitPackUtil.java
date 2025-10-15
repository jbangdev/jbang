package dev.jbang.dependencies;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class JitPackUtil {
	private static final Pattern GITHUB_TREE_PATTERN = Pattern.compile(
			"^https?://github.com/([^/]+?)/([^/]+?)(/tree/([^/]+?)(/(.+?))?)?/?$");
	private static final Pattern GITUB_COMMIT_PATTERN = Pattern.compile(
			"^https?://github.com/([^/]+?)/([^/]+?)/commit/(.+)$");
	private static final Pattern GITLAB_TREE_PATTERN = Pattern.compile(
			"^https?://gitlab.com/([^/]+?)/([^/]+?)(/-/tree/([^/]+?)(/(.+?))?)?/?$");
	private static final Pattern GITLAB_COMMIT_PATTERN = Pattern.compile(
			"^https?://gitlab.com/([^/]+?)/([^/]+?)/-/commit/(.+)$");
	private static final Pattern BITBUCKET_TREE_PATTERN = Pattern.compile(
			"^https?://bitbucket.org/([^/]+?)/([^/]+?)(/src/([^/]+?)(/(.+?))?)?/?$");
	private static final Pattern BITBUCKET_COMMIT_PATTERN = Pattern.compile(
			"^https?://bitbucket.org/([^/]+?)/([^/]+?)/commits/(.+)$");

	private static final Pattern POSSIBLE_SHA1_PATTERN = Pattern.compile("^[0-9a-f]{40}$");

	public static boolean possibleMatch(String ref) {
		return !ensureGAV(ref).equals(ref);
	}

	public static String ensureGAV(String ref) {
		try {
			// If the reference is a URL we'll try to convert it to a proper GAV
			URL url = new URL(ref);
			if (url.getProtocol().equals("https") || url.getProtocol().equals("http")) {
				// Strip and save the #part of the URL
				final String actualRef;
				String hash = url.getRef();
				if (hash != null) {
					actualRef = ref.substring(0, ref.lastIndexOf('#'));
				} else {
					actualRef = ref;
				}

				// Extract GAV coordinates from the URL
				// NB: Ordering of the list below is important!
				Optional<Pgamv> coords = Stream.<Function<String, Pgamv>>of(
						JitPackUtil::githubCommitUrlToGAV,
						JitPackUtil::githubTreeUrlToGAV,
						JitPackUtil::gitlabCommitUrlToGAV,
						JitPackUtil::gitlabTreeUrlToGAV,
						JitPackUtil::bitbucketCommitUrlToGAV,
						JitPackUtil::bitbucketTreeUrlToGAV)
					.map(f -> f.apply(actualRef))
					.filter(Objects::nonNull)
					.findFirst();

				if (coords.isPresent()) {
					if (hash != null) {
						String[] parts = hash.split(":");
						String module = (parts.length > 0) ? parts[0] : null;
						String snapshot = (parts.length == 2) ? parts[1] : null;

						// Override GAV coords with values from the #part of the URL
						Pgamv pgamv = coords.get().module(module);
						if ((snapshot != null && pgamv.version != null) ||
								(!hash.endsWith(":") && ("master".equals(pgamv.version))
										|| "main".equals(pgamv.version))) {
							pgamv = pgamv.version(pgamv.version + "-SNAPSHOT");
						}
						ref = pgamv.toGav();
					} else {
						if ("master".equals(coords.get().version)) {
							ref = coords.get().version("master" + "-SNAPSHOT").toGav();
						} else if ("main".equals(coords.get().version)) {
							ref = coords.get().version("main" + "-SNAPSHOT").toGav();
						} else {
							ref = coords.get().toGav();
						}
					}
				}
			}
		} catch (MalformedURLException ex) {
			// Ignore exception and just return the ref as-is
		}
		return ref;
	}

	static class Pgamv {
		final String provider;
		final String groupId;
		final String artifactId;
		final String module;
		final String version;

		Pgamv(String provider, String groupId, String artifactId, String module, String version) {
			this.provider = provider;
			this.groupId = groupId;
			this.artifactId = artifactId;
			this.module = module;
			this.version = version;
		}

		Pgamv module(String module) {
			if (module == null || module.isEmpty()) {
				return this;
			} else {
				return new Pgamv(provider, groupId, artifactId, module, version);
			}
		}

		Pgamv version(String version) {
			return new Pgamv(provider, groupId, artifactId, module, version);
		}

		String toGav() {
			String v;
			if (version == null) {
				v = "main-SNAPSHOT"; // using HEAD as no longer possible to know what default branch is called.
				// thus for now we default to assume 'main' as default.
			} else if (POSSIBLE_SHA1_PATTERN.matcher(version).matches()) {
				v = version.substring(0, 10);
			} else {
				v = version;
			}
			if (module != null) {
				return provider + "." + groupId + "." + artifactId + ":" + module + ":" + v;
			} else {
				return provider + "." + groupId + ":" + artifactId + ":" + v;
			}
		}
	}

	static Pgamv githubTreeUrlToGAV(String ref) {
		Matcher m = GITHUB_TREE_PATTERN.matcher(ref);
		if (m.matches()) {
			return new Pgamv("com.github", m.group(1), m.group(2), m.group(6), m.group(4));
		}
		return null;
	}

	static Pgamv githubCommitUrlToGAV(String ref) {
		Matcher m = GITUB_COMMIT_PATTERN.matcher(ref);
		if (m.matches()) {
			return new Pgamv("com.github", m.group(1), m.group(2), null, m.group(3));
		}
		return null;
	}

	static Pgamv gitlabTreeUrlToGAV(String ref) {
		Matcher m = GITLAB_TREE_PATTERN.matcher(ref);
		if (m.matches()) {
			return new Pgamv("com.gitlab", m.group(1), m.group(2), m.group(6), m.group(4));
		}
		return null;
	}

	static Pgamv gitlabCommitUrlToGAV(String ref) {
		Matcher m = GITLAB_COMMIT_PATTERN.matcher(ref);
		if (m.matches()) {
			return new Pgamv("com.gitlab", m.group(1), m.group(2), null, m.group(3));
		}
		return null;
	}

	static Pgamv bitbucketTreeUrlToGAV(String ref) {
		Matcher m = BITBUCKET_TREE_PATTERN.matcher(ref);
		if (m.matches()) {
			return new Pgamv("org.bitbucket", m.group(1), m.group(2), m.group(6), m.group(4));
		}
		return null;
	}

	static Pgamv bitbucketCommitUrlToGAV(String ref) {
		Matcher m = BITBUCKET_COMMIT_PATTERN.matcher(ref);
		if (m.matches()) {
			return new Pgamv("org.bitbucket", m.group(1), m.group(2), null, m.group(3));
		}
		return null;
	}

}
