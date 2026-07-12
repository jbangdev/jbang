package dev.jbang.dependencies;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Settings;
import dev.jbang.source.Project;
import dev.jbang.util.Util;

public class TestBuildSystemClassPaths extends BaseTest {

	@Test
	void explicitPomClasspathUsesMavenWrapper() throws Exception {
		assumeFalse(Util.isWindows());
		Path dep = cwdDir.resolve("dep.jar");
		Path pom = cwdDir.resolve("pom.xml");
		Files.write(dep, new byte[0]);
		Files.write(pom, Collections.singletonList("<project/>"));
		writeMvnw(cwdDir, dep.toString());
		Path src = cwdDir.resolve("main.java");
		Files.write(src, Collections.singletonList("class main {}"));

		Project prj = Project.builder()
			.additionalDependencies(Collections.singletonList(pom.toString()))
			.build(src);

		assertThat(prj.getMainSourceSet().getClassPaths(), contains(dep.toString()));
	}

	@Test
	void caretPomFindsNearestUpwardsFromSource() throws Exception {
		assumeFalse(Util.isWindows());
		Path project = cwdDir.resolve("project");
		Path nested = project.resolve("src/main/java");
		Files.createDirectories(nested);
		Path dep = project.resolve("dep.jar");
		Path pom = project.resolve("pom.xml");
		Files.write(dep, new byte[0]);
		Files.write(pom, Collections.singletonList("<project/>"));
		writeMvnw(project, dep.toString());
		Path src = nested.resolve("main.java");
		Files.write(src, Collections.singletonList("class main {}"));

		Project prj = Project.builder()
			.additionalDependencies(Collections.singletonList("^pom.xml"))
			.build(src);

		assertThat(prj.getMainSourceSet().getClassPaths(), contains(dep.toString()));
	}

	@Test
	void depsDirectiveWithPomXml() throws Exception {
		assumeFalse(Util.isWindows());
		Path dep = cwdDir.resolve("dep.jar");
		Path pom = cwdDir.resolve("pom.xml");
		Files.write(dep, new byte[0]);
		Files.write(pom, Collections.singletonList("<project/>"));
		writeMvnw(cwdDir, dep.toString());
		Path src = cwdDir.resolve("main.java");
		Files.write(src, Collections.singletonList("//DEPS pom.xml\nclass main {}"));

		Project prj = Project.builder().build(src);

		assertThat(prj.getMainSourceSet().getClassPaths(), contains(dep.toString()));
	}

	private static void writeMvnw(Path dir, String classpath) throws Exception {
		Path mvnw = dir.resolve("mvnw");
		Files.write(mvnw,
				Collections
					.singletonList("#!/bin/sh\nfor arg in \"$@\"; do case \"$arg\" in -Dmdep.outputFile=*) echo '"
							+ classpath + "' > \"${arg#-Dmdep.outputFile=}\" ;; esac; done\n"));
		mvnw.toFile().setExecutable(true);
		Files.createDirectories(Settings.getCacheDir(dev.jbang.Cache.CacheClass.deps));
	}
}
