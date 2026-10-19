package dev.jbang.dependencies;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import dev.jbang.util.Util;

class GradleBuildSystem implements BuildSystem {

	@Override
	public boolean supports(String fileName) {
		return "build.gradle".equals(fileName) || "build.gradle.kts".equals(fileName);
	}

	@Override
	public String resolveClassPath(Path buildFile) throws IOException {
		Path dir = buildFile.getParent();
		Path init = Files.createTempFile("jbang-classpath-", ".gradle");
		try {
			// Fail loud if no subproject applied the java plugin — otherwise the task
			// would silently produce no output and the user would see a confusing
			// "Build did not print JBANG_CLASSPATH=" error.
			Util.writeString(init,
					"def jbangPrinted = new java.util.concurrent.atomic.AtomicBoolean(false)\n"
							+ "allprojects { plugins.withId('java') { tasks.register('jbangPrintClasspath') { doLast {"
							+ " jbangPrinted.set(true);"
							+ " println('" + BuildTools.CLASSPATH_MARKER
							+ "' + sourceSets.main.compileClasspath.asPath) } } } }\n"
							+ "gradle.buildFinished { if (!jbangPrinted.get()) { throw new GradleException("
							+ "'No project applies the java plugin; JBang cannot resolve a compile classpath.') } }\n");
			List<String> command = Arrays.asList(
					BuildTools.wrapper(dir, "gradlew", "gradlew.bat", "gradle"),
					"-q", "-I", init.toString(), "jbangPrintClasspath");
			return BuildTools.markedClassPath(BuildTools.run(command, dir), buildFile);
		} finally {
			Files.deleteIfExists(init);
		}
	}
}
