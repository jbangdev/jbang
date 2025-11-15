package dev.jbang.source.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.jbang.util.Util;

class BuildJbangFileUpdateStrategy implements FileUpdateStrategy {

	@Override
	public boolean canHandle(Path file) {
		String fileName = file.getFileName().toString();
		return fileName.endsWith(".jbang");
	}

	@Override
	public void updateFile(Path file, List<String> newDeps)
			throws IOException {
		String content = Util.readFileContent(file);
		List<String> lines = new ArrayList<>();
		boolean depsAdded = false;

		for (String line : content.split("\n")) {
			lines.add(line);
		}

		// Add new dependencies at the end
		for (String dep : newDeps) {
			if (!DependencyHelper.isDependencyPresent(content, dep)) {
				// Remove any existing dependency with same groupId:artifactId but different
				// version
				DependencyHelper.removeExistingDependencyWithSameGroupArtifact(lines, dep);
				lines.add("DEPS " + dep);
				content += "\nDEPS " + dep; // Update content for subsequent checks
				depsAdded = true;
			}
		}

		if (depsAdded) {
			String newContent = String.join("\n", lines);
			Files.write(file, newContent.getBytes());
		}
	}
}
