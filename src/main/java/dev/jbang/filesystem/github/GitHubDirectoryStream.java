package dev.jbang.filesystem.github;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.NonNull;

/**
 * Directory stream implementation for GitHub filesystem.
 */
class GitHubDirectoryStream implements DirectoryStream<Path> {

	private final GitHubPath dir;
	private final DirectoryStream.Filter<? super Path> filter;
	private List<GitHubPath> children;

	GitHubDirectoryStream(GitHubPath dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
		this.dir = dir;
		this.filter = filter;
		GitHubFileSystemProvider provider = (GitHubFileSystemProvider) dir.getFileSystem().provider();
		this.children = provider.listDirectory(dir);
	}

	@Override
	public Iterator<Path> iterator() {
		return new Iterator<Path>() {
			private final Iterator<GitHubPath> it = children.iterator();
			private Path next;

			@Override
			public boolean hasNext() {
				if (next != null) {
					return true;
				}
				while (it.hasNext()) {
					Path candidate = it.next();
					try {
						if (filter == null || filter.accept(candidate)) {
							next = candidate;
							return true;
						}
					} catch (IOException e) {
						// Skip this entry
					}
				}
				return false;
			}

			@Override
			public Path next() {
				if (next == null && !hasNext()) {
					throw new java.util.NoSuchElementException();
				}
				Path result = next;
				next = null;
				return result;
			}
		};
	}

	@Override
	public void close() throws IOException {
		// Nothing to close
	}
}

