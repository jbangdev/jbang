package dev.jbang.filesystem.github;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Set;

import org.jspecify.annotations.NonNull;

/**
 * A FileSystem implementation for GitHub repositories.
 */
public class GitHubFileSystem extends FileSystem {

	private final GitHubFileSystemProvider provider;
	private final GitHubRepoInfo repoInfo;

	GitHubFileSystem(GitHubFileSystemProvider provider, GitHubRepoInfo repoInfo) {
		this.provider = provider;
		this.repoInfo = repoInfo;
	}

	@Override
	public GitHubFileSystemProvider provider() {
		return provider;
	}

	@Override
	public void close() throws IOException {
		// Nothing to close
	}

	@Override
	public boolean isOpen() {
		return true;
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public String getSeparator() {
		return "/";
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		return Set.of(getPath("/"));
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		return Set.of();
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		return Set.of("basic");
	}

	@Override
	public Path getPath(@NonNull String first, String... more) {
		StringBuilder path = new StringBuilder(first);
		for (String segment : more) {
			if (!path.toString().endsWith("/")) {
				path.append("/");
			}
			path.append(segment);
		}
		return new GitHubPath(this, path.toString());
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		String[] parts = syntaxAndPattern.split(":", 2);
		if (parts.length != 2) {
			throw new IllegalArgumentException("Invalid path matcher syntax: " + syntaxAndPattern);
		}
		String syntax = parts[0];
		String pattern = parts[1];
		if (!"glob".equals(syntax) && !"regex".equals(syntax)) {
			throw new UnsupportedOperationException("Syntax '" + syntax + "' not recognized");
		}
		return new GitHubPathMatcher(this, syntax, pattern);
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		throw new UnsupportedOperationException("UserPrincipalLookupService not supported");
	}

	@Override
	public WatchService newWatchService() throws IOException {
		throw new UnsupportedOperationException("WatchService not supported");
	}

	GitHubRepoInfo getRepoInfo() {
		return repoInfo;
	}
}

