package dev.jbang.filesystem.github;

import java.net.URI;

import org.jspecify.annotations.NonNull;

/**
 * Represents information about a GitHub repository, branch, and base path.
 */
public class GitHubRepoInfo {
	private final String owner;
	private final String repo;
	private final String ref;
	private final String basePath;

	public GitHubRepoInfo(@NonNull String owner, @NonNull String repo, @NonNull String ref,
			@NonNull String basePath) {
		this.owner = owner;
		this.repo = repo;
		this.ref = ref;
		this.basePath = basePath.isEmpty() || basePath.equals("/") ? "" : basePath;
	}

	@NonNull
	public String getOwner() {
		return owner;
	}

	@NonNull
	public String getRepo() {
		return repo;
	}

	@NonNull
	public String getRef() {
		return ref;
	}

	@NonNull
	public String getBasePath() {
		return basePath;
	}

	boolean matches(URI uri) {
		String url = uri.toString();
		return url.contains(owner) && url.contains(repo);
	}

	@Override
	public String toString() {
		return String.format("%s/%s@%s%s", owner, repo, ref, basePath);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		GitHubRepoInfo that = (GitHubRepoInfo) obj;
		return owner.equals(that.owner) && repo.equals(that.repo) && ref.equals(that.ref)
				&& basePath.equals(that.basePath);
	}

	@Override
	public int hashCode() {
		return owner.hashCode() ^ repo.hashCode() ^ ref.hashCode() ^ basePath.hashCode();
	}
}

