package dev.jbang.filesystem.github;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import org.jspecify.annotations.NonNull;

/**
 * Basic file attributes for GitHub files.
 */
class GitHubFileAttributes implements BasicFileAttributes {

	private final GitHubPath path;
	private final boolean isDirectory;
	private final long size;

	GitHubFileAttributes(GitHubPath path) throws IOException {
		this.path = path;
		GitHubFileSystemProvider provider = (GitHubFileSystemProvider) path.getFileSystem().provider();
		try {
			this.isDirectory = provider.isDirectory(path);
		} catch (IOException e) {
			// If we can't determine, assume it's a file
			this.isDirectory = false;
		}
		this.size = isDirectory ? 0 : getFileSize();
	}

	private long getFileSize() {
		// For now, we don't know the size without downloading
		// Could be improved by using GitHub API which provides size
		return 0;
	}

	@Override
	public FileTime lastModifiedTime() {
		// GitHub doesn't provide reliable modification times via API
		return FileTime.fromMillis(System.currentTimeMillis());
	}

	@Override
	public FileTime lastAccessTime() {
		return lastModifiedTime();
	}

	@Override
	public FileTime creationTime() {
		return lastModifiedTime();
	}

	@Override
	public boolean isRegularFile() {
		return !isDirectory;
	}

	@Override
	public boolean isDirectory() {
		return isDirectory;
	}

	@Override
	public boolean isSymbolicLink() {
		return false;
	}

	@Override
	public boolean isOther() {
		return false;
	}

	@Override
	public long size() {
		return size;
	}

	@Override
	public @NonNull Object fileKey() {
		return path.toUri();
	}
}

