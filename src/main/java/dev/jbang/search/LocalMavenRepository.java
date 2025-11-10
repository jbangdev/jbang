package dev.jbang.search;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

public class LocalMavenRepository {

	public static Set<Artifact> packages() {
		Set<Artifact> packages = new HashSet<Artifact>();
		try {
			Path localMaven = Paths.get(System.getProperty("user.home"), ".m2", "repository");
			Files.walkFileTree(localMaven, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
						throws IOException {
					if (Character.isDigit(dir.getFileName().toString().charAt(0))) {
						Path artifactDir = dir.getParent();
						String artifact = artifactDir.getFileName().toString();
						String group = localMaven.relativize(artifactDir.getParent())
							.toString()
							.replace(FileSystems.getDefault().getSeparator(), ".");
						packages.add(new DefaultArtifact(group, artifact, "", ""));
						return FileVisitResult.SKIP_SUBTREE;
					}

					return FileVisitResult.CONTINUE;
				}
			});

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return packages;
	}
}