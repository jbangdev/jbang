package dev.jbang.dependencies;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import dev.jbang.util.Util;

class MavenBuildSystem implements BuildSystem {

	@Override
	public boolean supports(String fileName) {
		return "pom.xml".equals(fileName);
	}

	@Override
	public String resolveClassPath(Path pom) throws IOException {
		Path dir = pom.getParent();
		Path outputFile = Files.createTempFile("jbang-classpath-", ".txt");
		try {
			List<String> command = Arrays.asList(
					BuildTools.wrapper(dir, "mvnw", "mvnw.cmd", "mvn"),
					"-q", "-f", pom.toString(),
					"dependency:build-classpath",
					"-DincludeScope=compile",
					"-Dmdep.outputFile=" + outputFile.toAbsolutePath(),
					"-Dmdep.pathSeparator=" + File.pathSeparator);
			BuildTools.run(command, dir);
			return Util.readString(outputFile);
		} finally {
			Files.deleteIfExists(outputFile);
		}
	}
}
