package dev.jbang.filesystem.github;

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A Path implementation for GitHub filesystem.
 */
public class GitHubPath implements Path {

	private final GitHubFileSystem fileSystem;
	private final String path;

	GitHubPath(GitHubFileSystem fileSystem, String path) {
		this.fileSystem = fileSystem;
		// Normalize path
		if (path.isEmpty()) {
			this.path = "/";
		} else if (!path.startsWith("/")) {
			this.path = "/" + path;
		} else {
			this.path = path;
		}
	}

	@Override
	public GitHubFileSystem getFileSystem() {
		return fileSystem;
	}

	@Override
	public boolean isAbsolute() {
		return path.startsWith("/");
	}

	@Override
	public Path getRoot() {
		return new GitHubPath(fileSystem, "/");
	}

	@Override
	public Path getFileName() {
		if (path.equals("/")) {
			return null;
		}
		int lastSlash = path.lastIndexOf('/');
		if (lastSlash < 0) {
			return this;
		}
		return new GitHubPath(fileSystem, path.substring(lastSlash + 1));
	}

	@Override
	public Path getParent() {
		if (path.equals("/")) {
			return null;
		}
		int lastSlash = path.lastIndexOf('/');
		if (lastSlash <= 0) {
			return getRoot();
		}
		return new GitHubPath(fileSystem, path.substring(0, lastSlash));
	}

	@Override
	public int getNameCount() {
		if (path.equals("/")) {
			return 0;
		}
		String[] parts = path.split("/");
		return parts.length - 1; // Exclude empty first element
	}

	@Override
	public Path getName(int index) {
		if (index < 0 || index >= getNameCount()) {
			throw new IllegalArgumentException("Index out of bounds: " + index);
		}
		String[] parts = path.split("/");
		return new GitHubPath(fileSystem, "/" + parts[index + 1]);
	}

	@Override
	public Path subpath(int beginIndex, int endIndex) {
		if (beginIndex < 0 || endIndex > getNameCount() || beginIndex >= endIndex) {
			throw new IllegalArgumentException("Invalid indices");
		}
		String[] parts = path.split("/");
		StringBuilder subpath = new StringBuilder();
		for (int i = beginIndex + 1; i <= endIndex; i++) {
			if (subpath.length() > 0) {
				subpath.append("/");
			}
			subpath.append(parts[i]);
		}
		return new GitHubPath(fileSystem, "/" + subpath.toString());
	}

	@Override
	public boolean startsWith(Path other) {
		if (!(other instanceof GitHubPath)) {
			return false;
		}
		GitHubPath otherPath = (GitHubPath) other;
		return path.startsWith(otherPath.path);
	}

	@Override
	public boolean startsWith(String other) {
		return path.startsWith(other);
	}

	@Override
	public boolean endsWith(Path other) {
		if (!(other instanceof GitHubPath)) {
			return false;
		}
		GitHubPath otherPath = (GitHubPath) other;
		return path.endsWith(otherPath.path);
	}

	@Override
	public boolean endsWith(String other) {
		return path.endsWith(other);
	}

	@Override
	public Path normalize() {
		// For now, just return this as paths are already normalized
		return this;
	}

	@Override
	public Path resolve(Path other) {
		if (other.isAbsolute()) {
			return other;
		}
		if (other instanceof GitHubPath) {
			GitHubPath otherPath = (GitHubPath) other;
			String otherPathStr = otherPath.path;
			if (otherPathStr.startsWith("/")) {
				otherPathStr = otherPathStr.substring(1);
			}
			String resolved = path.equals("/") ? "/" + otherPathStr : path + "/" + otherPathStr;
			return new GitHubPath(fileSystem, resolved);
		}
		return resolve(other.toString());
	}

	@Override
	public Path resolve(String other) {
		if (other.startsWith("/")) {
			return new GitHubPath(fileSystem, other);
		}
		String resolved = path.equals("/") ? "/" + other : path + "/" + other;
		return new GitHubPath(fileSystem, resolved);
	}

	@Override
	public Path resolveSibling(Path other) {
		Path parent = getParent();
		if (parent == null) {
			return other;
		}
		return parent.resolve(other);
	}

	@Override
	public Path resolveSibling(String other) {
		return resolveSibling(new GitHubPath(fileSystem, other));
	}

	@Override
	public Path relativize(Path other) {
		if (!(other instanceof GitHubPath) || !other.getFileSystem().equals(fileSystem)) {
			throw new IllegalArgumentException("Paths must be from same filesystem");
		}
		GitHubPath otherPath = (GitHubPath) other;
		// Simple implementation - could be improved
		if (otherPath.path.startsWith(path)) {
			String relative = otherPath.path.substring(path.length());
			if (relative.startsWith("/")) {
				relative = relative.substring(1);
			}
			return new GitHubPath(fileSystem, relative);
		}
		throw new IllegalArgumentException("Cannot relativize paths");
	}

	@Override
	public URI toUri() {
		GitHubRepoInfo repoInfo = fileSystem.getRepoInfo();
		try {
			String uriStr = String.format("github://github.com/%s/%s/tree/%s%s", repoInfo.getOwner(),
					repoInfo.getRepo(), repoInfo.getRef(), path);
			return new URI(uriStr);
		} catch (Exception e) {
			throw new RuntimeException("Failed to create URI", e);
		}
	}

	@Override
	public Path toAbsolutePath() {
		return isAbsolute() ? this : new GitHubPath(fileSystem, "/" + path);
	}

	@Override
	public Path toRealPath(LinkOption... options) throws java.io.IOException {
		return toAbsolutePath().normalize();
	}

	@Override
	public File toFile() {
		throw new UnsupportedOperationException("GitHub paths cannot be converted to File");
	}

	@Override
	public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
			throws java.io.IOException {
		throw new UnsupportedOperationException("WatchService not supported");
	}

	@Override
	public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws java.io.IOException {
		throw new UnsupportedOperationException("WatchService not supported");
	}

	@Override
	public Iterator<Path> iterator() {
		return new Iterator<Path>() {
			private int index = 0;

			@Override
			public boolean hasNext() {
				return index < getNameCount();
			}

			@Override
			public Path next() {
				return getName(index++);
			}
		};
	}

	@Override
	public int compareTo(Path other) {
		if (!(other instanceof GitHubPath)) {
			throw new ClassCastException("Cannot compare GitHubPath with " + other.getClass());
		}
		return path.compareTo(((GitHubPath) other).path);
	}

	String getPathString() {
		return path;
	}

	@Override
	public String toString() {
		return path;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		GitHubPath that = (GitHubPath) obj;
		return Objects.equals(fileSystem, that.fileSystem) && Objects.equals(path, that.path);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileSystem, path);
	}
}

