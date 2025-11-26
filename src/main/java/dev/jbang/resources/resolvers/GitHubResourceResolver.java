package dev.jbang.resources.resolvers;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.filesystem.github.GitHubFileSystemProvider;
import dev.jbang.filesystem.github.GitHubRepoInfo;
import dev.jbang.resources.ResourceNotFoundException;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;
import dev.jbang.util.Util;

/**
 * A ResourceResolver that can resolve GitHub URLs and create FileSystem instances
 * for accessing GitHub repositories as file systems.
 */
public class GitHubResourceResolver implements ResourceResolver {

	@Override
	public @Nullable ResourceRef resolve(String resource, boolean trusted) {
		if (!isGitHubUrl(resource)) {
			return null;
		}

		try {
			GitHubRepoInfo repoInfo = GitHubFileSystemProvider.parseGitHubUrl(resource);
			URI fsUri = GitHubFileSystemProvider.toGitHubUri(repoInfo);
			FileSystem fs = FileSystems.newFileSystem(fsUri, Collections.emptyMap());

			// Extract the path from the original URL
			String pathStr = extractPathFromUrl(resource, repoInfo);
			Path filePath = fs.getPath(pathStr);

			return new GitHubResourceRef(resource, filePath, fs, this);
		} catch (Exception e) {
			return ResourceRef.forUnresolvable(resource, "Failed to resolve GitHub URL: " + e.getMessage());
		}
	}

	@Override
	public @NonNull String description() {
		return "GitHub resource resolver";
	}

	private boolean isGitHubUrl(String resource) {
		return resource != null && (resource.startsWith("https://github.com/")
				|| resource.startsWith("https://raw.githubusercontent.com/"));
	}

	private String extractPathFromUrl(String url, GitHubRepoInfo repoInfo) {
		if (url.startsWith("https://raw.githubusercontent.com/")) {
			String path = url.substring("https://raw.githubusercontent.com/".length());
			String[] parts = path.split("/", 4);
			if (parts.length > 3) {
				return "/" + parts[3];
			}
			return "/";
		} else if (url.startsWith("https://github.com/")) {
			String path = url.substring("https://github.com/".length());
			String[] parts = path.split("/");
			// Format: owner/repo/tree/branch/path or owner/repo/blob/branch/path
			if (parts.length >= 5 && ("tree".equals(parts[2]) || "blob".equals(parts[2]))) {
				StringBuilder filePath = new StringBuilder();
				for (int i = 4; i < parts.length; i++) {
					if (filePath.length() > 0) {
						filePath.append("/");
					}
					filePath.append(parts[i]);
				}
				return "/" + filePath.toString();
			}
		}
		return repoInfo.getBasePath();
	}

	private static class GitHubResourceRef implements ResourceRef {
		private final String originalResource;
		private final Path filePath;
		private final FileSystem fileSystem;
		private final ResourceResolver resolver;

		GitHubResourceRef(String originalResource, Path filePath, FileSystem fileSystem,
				ResourceResolver resolver) {
			this.originalResource = originalResource;
			this.filePath = filePath;
			this.fileSystem = fileSystem;
			this.resolver = resolver;
		}

		@Override
		public @Nullable String getOriginalResource() {
			return originalResource;
		}

		@Override
		public boolean exists() {
			try {
				return java.nio.file.Files.exists(filePath);
			} catch (Exception e) {
				return false;
			}
		}

		@Override
		public @NonNull Path getFile() {
			return filePath;
		}

		@Override
		public ResourceRef resolve(String resource, boolean trusted) {
			if (Util.isURL(resource)) {
				return resolver.resolve(resource, trusted);
			}
			try {
				Path resolved = filePath.getParent().resolve(resource).normalize();
				return new GitHubResourceRef(originalResource + "/" + resource, resolved, fileSystem, resolver);
			} catch (Exception e) {
				return ResourceRef.forUnresolvable(resource,
						"Failed to resolve relative path: " + e.getMessage());
			}
		}

		@Override
		public @NonNull String description() {
			return "GitHub resource: " + originalResource;
		}

		@Override
		public int compareTo(@NonNull ResourceRef o) {
			return originalResource.compareTo(o.getOriginalResource());
		}

		@Override
		public ResourceRef.ResourceChildren children() {
			try {
				if (java.nio.file.Files.isDirectory(filePath)) {
					return new GitHubResourceChildren(filePath, fileSystem, resolver);
				}
			} catch (IOException e) {
				// Ignore
			}
			throw new ResourceNotFoundException(originalResource, "Resource is not a directory");
		}

		@Override
		public boolean isParent() {
			try {
				return java.nio.file.Files.isDirectory(filePath);
			} catch (Exception e) {
				return false;
			}
		}
	}

	private static class GitHubResourceChildren implements ResourceRef.ResourceChildren {
		private final Path dirPath;
		private final FileSystem fileSystem;
		private final ResourceResolver resolver;

		GitHubResourceChildren(Path dirPath, FileSystem fileSystem, ResourceResolver resolver) {
			this.dirPath = dirPath;
			this.fileSystem = fileSystem;
			this.resolver = resolver;
		}

		@Override
		public java.util.stream.Stream<Path> list() throws IOException {
			return java.nio.file.Files.list(dirPath);
		}

		@Override
		public @Nullable ResourceRef resolve(String resource, boolean trusted) {
			try {
				Path resolved = dirPath.resolve(resource).normalize();
				return new GitHubResourceRef(resource, resolved, fileSystem, resolver);
			} catch (Exception e) {
				return ResourceRef.forUnresolvable(resource,
						"Failed to resolve: " + e.getMessage());
			}
		}

		@Override
		public @NonNull String description() {
			return "GitHub directory: " + dirPath;
		}
	}
}

