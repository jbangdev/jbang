package dev.jbang.source.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.jbang.util.Util;

class JavaFileUpdateStrategy implements FileUpdateStrategy {

	@Override
	public boolean canHandle(Path file) {
		String fileName = file.getFileName().toString();
		return fileName.endsWith(".java") || fileName.endsWith(".jbang");
	}

	@Override
	public void updateFile(Path file, List<String> newDeps)
			throws IOException {
		String content = Util.readFileContent(file);
		List<String> lines = new ArrayList<>();
		boolean depsAdded = false;
		int lastDepsIndex = -1;

		// Find the last existing DEPS line
		for (int i = 0; i < content.split("\n").length; i++) {
			String line = content.split("\n")[i];
			lines.add(line);

			if (line.trim().startsWith("//DEPS ")) {
				lastDepsIndex = i;
			}
		}

		// Insert new dependencies after the last DEPS line, or after the /// line if no
		// DEPS exist
		int insertIndex;
		if (lastDepsIndex >= 0) {
			// Insert after the last existing DEPS line
			insertIndex = lastDepsIndex + 1;
		} else if (!lines.isEmpty() && lines.get(0).trim().startsWith("///")) {
			// Insert after the first line (/// env /bang line)
			insertIndex = 1;
		} else {
			// Insert at the beginning
			insertIndex = 0;
		}

		for (String dep : newDeps) {
			if (!DependencyHelper.isDependencyPresent(content, dep)) {
				// Remove any existing dependency with same groupId:artifactId but different
				// version
				DependencyHelper.removeExistingDependencyWithSameGroupArtifact(lines, dep);
				lines.add(insertIndex, "//DEPS " + dep);
				content += "\n//DEPS " + dep; // Update content for subsequent checks
				depsAdded = true;
				insertIndex++; // Move insertion point for next dependency
			}
		}

		if (depsAdded) {
			String newContent = String.join("\n", lines);
			Files.write(file, newContent.getBytes());
		}
	}
}
