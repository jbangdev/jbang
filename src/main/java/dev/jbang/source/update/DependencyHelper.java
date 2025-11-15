package dev.jbang.source.update;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.jbang.dependencies.MavenCoordinate;

class DependencyHelper {

	static boolean isDependencyPresent(String content, String dependency) {
		// Check for exact dependency match (including version)
		// Match both //DEPS (for .java files) and DEPS (for .jbang files)
		Pattern pattern = Pattern.compile("^(//)?DEPS\\s+" + Pattern.quote(dependency) + "\\s*$",
				Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(content);
		return matcher.find();
	}

	static void removeExistingDependencyWithSameGroupArtifact(List<String> lines, String dependency) {
		// Extract groupId:artifactId from the dependency
		MavenCoordinate coord = MavenCoordinate.fromString(dependency);
		if (coord == null) {
			return;
		}

		String groupId = coord.getGroupId();
		String artifactId = coord.getArtifactId();
		String groupArtifact = groupId + ":" + artifactId;

		// Remove any existing dependency with same groupId:artifactId but different
		// version
		lines.removeIf(line -> {
			String trimmed = line.trim();
			return (trimmed.startsWith("//DEPS ") || trimmed.startsWith("DEPS ")) &&
					trimmed.contains(groupArtifact) &&
					!trimmed.contains(dependency);
		});
	}
}
