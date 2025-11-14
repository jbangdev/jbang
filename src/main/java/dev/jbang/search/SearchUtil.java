package dev.jbang.search;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.aether.artifact.Artifact;

class SearchUtil {

	public static Set<Artifact> localMavenArtifacts(Path localMaven) {
		Map<String, Artifact> packages = new HashMap<>();
		try {
			if (!Files.exists(localMaven)) {
				return new HashSet<Artifact>();
			}
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

						if (!packages.containsKey(group + ":" + artifact)) {
							Artifact a = null;

							// Look for version directories under artifactDir and select the latest version
							if (Files.isDirectory(artifactDir)) {
								try (Stream<Path> versions = Files.list(artifactDir)) {
									List<String> versionList = versions
										.filter(Files::isDirectory)
										.map(p -> p.getFileName().toString())
										.collect(Collectors.toList());
									if (!versionList.isEmpty()) {
										org.eclipse.aether.version.VersionScheme versionScheme = new org.eclipse.aether.util.version.GenericVersionScheme();
										String latestVersion = versionList.stream()
											.filter(v -> {
												// Accept only plausible Maven version strings
												return !v.startsWith(".") && !v.startsWith("_");
											})
											.max(Comparator.comparing(v -> {
												try {
													return versionScheme.parseVersion(v);
												} catch (Exception e) {
													return null;
												}
											}, Comparator.nullsLast(Comparator.naturalOrder())))
											.orElse(dir.getFileName().toString());
										a = new org.eclipse.aether.artifact.DefaultArtifact(group, artifact, "",
												latestVersion);
									}
								} catch (Exception ex) {
									// Fallback to original version if error
									a = new org.eclipse.aether.artifact.DefaultArtifact(group, artifact, "",
											dir.getFileName().toString());
								}
							}
							if (a != null) {
								packages.put(group + ":" + artifact, a);
							}
						}
						return FileVisitResult.SKIP_SUBTREE;
					}

					return FileVisitResult.CONTINUE;
				}
			});

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return packages.entrySet().stream().map(Map.Entry::getValue).collect(Collectors.toSet());
	}

	public static Set<Artifact> localMavenArtifactsVersions(Path localMavenRepo, Artifact artifact) {
		Set<Artifact> versions = new HashSet<>();
		try {
			if (!Files.exists(localMavenRepo)) {
				return versions;
			}

			// Build path to artifact directory: groupId/artifactId/
			// Convert groupId dots to path separators
			String groupPath = artifact.getGroupId().replace(".", FileSystems.getDefault().getSeparator());
			Path artifactDir = localMavenRepo.resolve(groupPath).resolve(artifact.getArtifactId());

			if (!Files.exists(artifactDir) || !Files.isDirectory(artifactDir)) {
				return versions;
			}

			// List all version directories
			try (Stream<Path> versionDirs = Files.list(artifactDir)) {
				versionDirs
					.filter(Files::isDirectory)
					.map(Path::getFileName)
					.map(Path::toString)
					.filter(v -> {
						// Accept only plausible Maven version strings
						return !v.startsWith(".") && !v.startsWith("_");
					})
					.forEach(version -> {
						Artifact versionedArtifact = new org.eclipse.aether.artifact.DefaultArtifact(
								artifact.getGroupId(),
								artifact.getArtifactId(),
								artifact.getClassifier(),
								artifact.getExtension(),
								version);
						versions.add(versionedArtifact);
					});
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return versions;
	}

}