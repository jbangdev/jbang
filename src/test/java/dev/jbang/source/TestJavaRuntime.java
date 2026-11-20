package dev.jbang.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.jbang.BaseTest;
import dev.jbang.cli.TestJdk;
import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkManager;
import dev.jbang.devkitman.jdkproviders.BaseJdkProvider;
import dev.jbang.resources.ResourceRef;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

class TestJavaRuntime extends BaseTest {

	private Project project(String requestedVersion, boolean includeJdk) throws IOException {
		Path jreHome = Files.createDirectory(cwdDir.resolve("jre"));
		Path bin = Files.createDirectory(jreHome.resolve("bin"));
		Util.writeString(jreHome.resolve("release"), "JAVA_VERSION=\"11.0.1\"");
		Path java = Files.createFile(bin.resolve(Util.isWindows() ? "java.exe" : "java"));
		java.toFile().setExecutable(true);

		Path jdkHome = cwdDir.resolve("jdk");
		if (includeJdk) {
			TestJdk.initMockJdkDir(jdkHome, "17.0.1");
		}
		BaseJdkProvider provider = new BaseJdkProvider() {
			@Override
			public String name() {
				return "test";
			}

			@Override
			public String description() {
				return "Test Java installations";
			}

			@Override
			public boolean isValidId(String id) {
				return "runtime".equals(id) || "compiler".equals(id);
			}

			@Override
			public Stream<Jdk.InstalledJdk> listInstalled() {
				Stream<Jdk.InstalledJdk> runtimes = Stream.of(createJdk("runtime", jreHome));
				return includeJdk
						? Stream.concat(runtimes, Stream.of(createJdk("compiler", jdkHome)))
						: runtimes;
			}
		};
		JdkManager manager = JdkManager.builder().providers(provider).defaultJavaVersion(17).build();
		Project project = new Project(ResourceRef.forFile(cwdDir.resolve("app.jar")));
		project.setJdkManager(manager);
		project.setJavaVersion(requestedVersion);
		return project;
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "11", "11+", "runtime" })
	void runsOnJreWithoutCompiler(String requestedVersion) throws IOException {
		Project project = project(requestedVersion, false);
		assertEquals(cwdDir.resolve("jre"), project.projectJava().home());
		assertSame(project.projectJava(), project.projectJava());
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "11+", "17", "compiler" })
	void compilationSelectsJdkFromMixedProvider(String requestedVersion) throws IOException {
		Project project = project(requestedVersion, true);
		assertEquals(cwdDir.resolve("jdk"), project.projectJdk().home());
		assertSame(project.projectJdk(), project.projectJava());
	}

	@Test
	void compilationDoesNotReuseRuntimeSelection() throws IOException {
		Project project = project(null, true);
		assertEquals(cwdDir.resolve("jre"), project.projectJava().home());
		assertEquals(cwdDir.resolve("jdk"), project.projectJdk().home());
		assertSame(project.projectJdk(), project.projectJava());
	}

	@ParameterizedTest
	@ValueSource(strings = { "11", "runtime" })
	void cannotCompileWithRequestedJreOnly(String requestedVersion) throws IOException {
		Project project = project(requestedVersion, true);
		assertThrows(IllegalArgumentException.class, project::projectJdk);
	}

	@Test
	void installsJdkWhenOnlyJreIsAvailable() throws IOException {
		Project project = project(null, false);
		environmentVariables.set("JAVA_HOME", cwdDir.resolve("jre").toString());
		project.setJdkManager(new JavaUtil.JdkManBuilder()
			.provider("javahome", "jbang")
			.installer("mock;versions=17.0.1")
			.build());

		assertEquals(cwdDir.resolve("jre"), project.projectJava().home());
		assertEquals(17, project.projectJdk().majorVersion());
		assertSame(project.projectJdk(), project.projectJava());
	}
}
