package dev.jbang.dependencies;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.ExitException;
import dev.jbang.source.Project;
import dev.jbang.util.Util;

public class TestBuildSystemClassPaths extends BaseTest {

	@Test
	void pomUsesMavenCompileClasspath() throws Exception {
		assumeFalse(Util.isWindows());
		Path dep = buildFile("pom.xml");
		writeWrapper("mvnw",
				"out=; compile=; for arg in \"$@\"; do case \"$arg\" in -Dmdep.outputFile=*) out=${arg#-Dmdep.outputFile=};; -DincludeScope=compile) compile=yes;; esac; done; test \"$compile\" = yes && echo '"
						+ dep + "' > \"$out\"");

		assertClasspath("pom.xml", dep);
	}

	@Test
	void gradleUsesCompileClasspath() throws Exception {
		assumeFalse(Util.isWindows());
		Path dep = buildFile("build.gradle");
		writeWrapper("gradlew", "grep -q compileClasspath \"$3\" && echo 'JBANG_CLASSPATH=" + dep + "'");

		assertClasspath("build.gradle", dep);
	}

	@Test
	void sbtUsesCompileDependencyClasspath() throws Exception {
		assumeFalse(Util.isWindows());
		Path dep = buildFile("build.sbt");
		// The real command runs `sbt --error --batch "export Compile /
		// dependencyClasspath"`, which prints the classpath as its last stdout
		// line. The stub emulates that shape: some info noise followed by the
		// classpath.
		writeWrapper("sbt", "echo 'loading project' && echo '" + dep + "'");

		assertClasspath("build.sbt", dep);
	}

	@Test
	void millUsesRootCompileClasspath() throws Exception {
		assumeFalse(Util.isWindows());
		Path dep = buildFile("build.mill");
		// Real mill wraps paths as `qref:v1:HASH:PATH` and prints the array with
		// `[` on its own line. Include a leading log line to exercise the
		// noise-tolerant JSON extractor.
		writeWrapper("mill",
				"test \"$2\" = show && test \"$3\" = compileClasspath && "
						+ "printf '[build.mill] [info] compiling\\n[\\n  \"qref:v1:abc123:"
						+ dep + "\"\\n]\\n'");

		assertClasspath("build.mill", dep);
	}

	@Test
	void gavStringIsNotABuildFileDependency() {
		// GAV coordinates contain colons that Windows refuses to parse as
		// paths; the classifier must not blow up on them.
		assertThat(BuildSystemClassPaths.isBuildFileDependency("info.picocli:picocli:4.6.3"), is(false));
		assertThat(BuildSystemClassPaths.isBuildFileDependency("org.openjfx:javafx-graphics:17:mac"), is(false));
		assertThat(BuildSystemClassPaths.dependencies(
				Arrays.asList("info.picocli:picocli:4.6.3", "pom.xml")),
				contains("info.picocli:picocli:4.6.3"));
	}

	@Test
	void remoteScriptRejectsEmbeddedBuildFileDep() throws Exception {
		assumeFalse(Util.isWindows());
		// Simulate a remote/URL-based script: a ResourceRef whose original
		// resource is an http URL but whose file lives in a cache-like dir.
		Path cache = cwdDir.resolve("cache/urls/abc");
		Files.createDirectories(cache);
		Path cachedFile = cache.resolve("remote.java");
		Files.write(cachedFile, Collections.singletonList("//DEPS build.gradle\nclass remote {}"));
		dev.jbang.resources.ResourceRef ref = dev.jbang.resources.ResourceRef.forResolvedResource(
				"https://example.com/remote.java", cachedFile);

		ExitException error = assertThrows(ExitException.class, () -> Project.builder().build(ref));
		assertThat(error.getMessage(), containsString("remote script"));
		assertThat(error.getMessage(), containsString("build.gradle"));
	}

	@Test
	void cliDepIsResolvedFromCwdNotScript() throws Exception {
		assumeFalse(Util.isWindows());
		// Script lives in a cache-like directory that has no build.gradle next to it.
		Path cacheLike = cwdDir.resolve("cache/urls/abc");
		Files.createDirectories(cacheLike);
		Path src = cacheLike.resolve("properties.java");
		Files.write(src, Collections.singletonList("class properties {}"));
		// The build file lives in CWD.
		Path dep = buildFile("build.gradle");
		writeWrapper("gradlew", "echo 'JBANG_CLASSPATH=" + dep + "'");

		dev.jbang.source.ProjectBuilder builder = dev.jbang.source.Project.builder();
		builder.additionalDependencies(Collections.singletonList("build.gradle"));
		dev.jbang.source.Project prj = builder.build(src);

		assertThat(prj.getMainSourceSet().getClassPaths(), contains(dep.toString()));
	}

	@Test
	void caretFindsNearestBuildFile() throws Exception {
		assumeFalse(Util.isWindows());
		Path project = cwdDir.resolve("project");
		Path nested = project.resolve("src/main/java");
		Files.createDirectories(nested);
		Path dep = project.resolve("dep.jar");
		Files.write(dep, new byte[0]);
		Files.write(project.resolve("pom.xml"), Collections.singletonList("<project/>"));
		writeWrapper(project, "mvnw",
				"for arg in \"$@\"; do case \"$arg\" in -Dmdep.outputFile=*) echo '" + dep
						+ "' > \"${arg#-Dmdep.outputFile=}\";; esac; done");
		Path src = nested.resolve("main.java");
		Files.write(src, Collections.singletonList("//DEPS ^pom.xml\nclass main {}"));

		Project prj = Project.builder().build(src);

		assertThat(prj.getMainSourceSet().getClassPaths(), contains(dep.toString()));
	}

	@Test
	void buildFailurePropagatesOutput() throws Exception {
		assumeFalse(Util.isWindows());
		buildFile("pom.xml");
		writeWrapper("mvnw", "echo 'boom: something exploded' >&2; exit 3");
		Path src = cwdDir.resolve("src/main.java");
		Files.createDirectories(src.getParent());
		Files.write(src, Collections.singletonList("//DEPS ../pom.xml\nclass main {}"));

		ExitException error = assertThrows(ExitException.class, () -> Project.builder().build(src));
		assertThat(error.getMessage(), containsString("Could not resolve build classpath"));
	}

	@Test
	void missingMarkerReportsError() throws Exception {
		assumeFalse(Util.isWindows());
		buildFile("build.gradle");
		writeWrapper("gradlew", "echo 'nothing to see here'");
		Path src = cwdDir.resolve("src/main.java");
		Files.createDirectories(src.getParent());
		Files.write(src, Collections.singletonList("//DEPS ../build.gradle\nclass main {}"));

		ExitException error = assertThrows(ExitException.class, () -> Project.builder().build(src));
		assertThat(error.getMessage(), containsString("JBANG_CLASSPATH="));
	}

	@Test
	void resultIsCachedByPathAndMtime() throws Exception {
		assumeFalse(Util.isWindows());
		Path dep = buildFile("pom.xml");
		Path counter = cwdDir.resolve("calls.txt");
		writeWrapper("mvnw",
				"echo x >> \"" + counter + "\"; "
						+ "out=; for arg in \"$@\"; do case \"$arg\" in -Dmdep.outputFile=*) out=${arg#-Dmdep.outputFile=};; esac; done; "
						+ "echo '" + dep + "' > \"$out\"");
		Path src = cwdDir.resolve("src/main.java");
		Files.createDirectories(src.getParent());
		Files.write(src, Collections.singletonList("//DEPS ../pom.xml\nclass main {}"));

		Project.builder().build(src);
		Project.builder().build(src);

		assertThat(Files.readAllLines(counter).size(), is(1));
	}

	@Test
	void caretStopsAtUserHome() throws Exception {
		Path home = cwdDir.resolve("home");
		Path src = home.resolve("project/src/main.java");
		Files.createDirectories(src.getParent());
		Files.write(src, Collections.singletonList("//DEPS ^pom.xml\nclass main {}"));
		Files.write(cwdDir.resolve("pom.xml"), Collections.singletonList("<project/>"));
		environmentVariables.set("JBANG_LOCAL_ROOT", home.toString());
		ExitException error = assertThrows(ExitException.class, () -> Project.builder().build(src));
		assertThat(error.getMessage(), is("Build file not found: ^pom.xml"));
	}

	private Path buildFile(String name) throws Exception {
		Path dep = cwdDir.resolve(name + ".jar");
		Files.write(dep, new byte[0]);
		Files.write(cwdDir.resolve(name), Collections.singletonList(""));
		return dep;
	}

	private void assertClasspath(String buildFile, Path dep) throws Exception {
		Path src = cwdDir.resolve("src/main.java");
		Files.createDirectories(src.getParent());
		Files.write(src, Collections.singletonList("//DEPS ../" + buildFile + "\nclass main {}"));
		Project prj = Project.builder().build(src);
		assertThat(prj.getMainSourceSet().getClassPaths(), contains(dep.toString()));
	}

	private void writeWrapper(String name, String command) throws Exception {
		writeWrapper(cwdDir, name, command);
	}

	private static void writeWrapper(Path dir, String name, String command) throws Exception {
		Path wrapper = dir.resolve(name);
		Files.write(wrapper, Collections.singletonList("#!/bin/sh\n" + command));
		wrapper.toFile().setExecutable(true);
	}
}
