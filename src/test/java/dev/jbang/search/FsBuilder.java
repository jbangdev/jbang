package dev.jbang.search;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FsBuilder {
	private final Path root;

	private FsBuilder(Path root) {
		this.root = root;
	}

	public static FsBuilder under(Path root) {
		return new FsBuilder(root);
	}

	public FsBuilder dir(String relative) throws IOException {
		Files.createDirectories(root.resolve(relative));
		return this;
	}

	public FsBuilder file(String relative, String content) throws IOException {
		Path p = root.resolve(relative);
		Files.createDirectories(p.getParent());
		Files.writeString(p, content);
		return this;
	}

	public Path root() {
		return root;
	}

	FsBuilder artifact(
			String groupId,
			String artifactId,
			String... versions) throws IOException {
		Path artifactDir = root
			.resolve(groupId.replace('.', File.separatorChar))
			.resolve(artifactId);

		for (String v : versions) {
			Files.createDirectories(artifactDir.resolve(v));
		}
		return this;
	}
}