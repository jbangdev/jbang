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
			// Register a print task on every java subproject (runtime classpath ==
			// compile + runtime dependencies, without the project's own output). If no
			// project applies the java plugin, register a fallback task that fails with
			// an actionable error. The no-java detection runs in `projectsEvaluated`
			// (configuration phase) instead of `gradle.buildFinished`, which is
			// incompatible with the Gradle configuration cache.
			Util.writeString(init,
					"allprojects { plugins.withId('java') { tasks.register('jbangPrintClasspath') { doLast {"
							+ " println('" + BuildTools.CLASSPATH_MARKER
							+ "' + configurations.runtimeClasspath.asPath) } } } }\n"
							+ "gradle.projectsEvaluated {"
							+ " if (rootProject.allprojects.every { !it.plugins.hasPlugin('java') }) {"
							+ " rootProject.tasks.register('jbangPrintClasspath') { doLast {"
							+ " throw new GradleException("
							+ "'No project applies the java plugin; JBang cannot resolve a classpath.') } } } }\n");
			List<String> command = Arrays.asList(
					BuildTools.wrapper(dir, "gradlew", "gradlew.bat", "gradle"),
					"-q", "-I", init.toString(), "jbangPrintClasspath");
			return BuildTools.markedClassPath(BuildTools.run(command, dir), buildFile);
		} finally {
			Files.deleteIfExists(init);
		}
	}
}
