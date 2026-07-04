package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestJBangInstallation {
	@Test
	void findsFlatInstallation(@TempDir Path dir) throws IOException {
		createScripts(dir);
		Path jar = Files.createFile(dir.resolve(Wrapper.JAR_NAME));

		JBangInstallation installation = JBangInstallation.find(jar);

		assertThat(installation.getScriptsDir(), equalTo(dir));
		assertThat(installation.getJarDir(), equalTo(dir));
	}

	@Test
	void findsWrapperInstallation(@TempDir Path dir) throws IOException {
		createScripts(dir);
		Path jarDir = Files.createDirectory(dir.resolve(Wrapper.DIR_NAME));
		Path jar = Files.createFile(jarDir.resolve(Wrapper.JAR_NAME));

		JBangInstallation installation = JBangInstallation.find(jar);

		assertThat(installation.getScriptsDir(), equalTo(dir));
		assertThat(installation.getJarDir(), equalTo(jarDir));
	}

	@Test
	void acceptsNativeArtifacts(@TempDir Path dir) throws IOException {
		createScripts(dir);
		Files.createFile(dir.resolve(Wrapper.JAR_NAME));

		for (String name : Arrays.asList("jbang.bin", "jbang.bin.exe", "jbang.bin-linux-x64",
				"jbang.bin-windows-x64.exe")) {
			JBangInstallation installation = JBangInstallation.find(Files.createFile(dir.resolve(name)));
			assertThat(installation.getScriptsDir(), equalTo(dir));
			assertThat(installation.getJarDir(), equalTo(dir));
		}
	}

	@Test
	void rejectsUnsupportedArtifact(@TempDir Path dir) throws IOException {
		Path location = Files.createFile(dir.resolve("classes"));

		assertThrows(ExitException.class, () -> JBangInstallation.find(location));
	}

	@Test
	void rejectsIncompleteInstallation(@TempDir Path dir) throws IOException {
		Path jar = Files.createFile(dir.resolve(Wrapper.JAR_NAME));

		assertThrows(ExitException.class, () -> JBangInstallation.find(jar));
	}

	private static void createScripts(Path dir) throws IOException {
		for (String name : Wrapper.SCRIPT_NAMES) {
			Files.createFile(dir.resolve(name));
		}
	}
}
