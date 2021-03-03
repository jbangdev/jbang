package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.anExistingDirectory;
import static org.hamcrest.io.FileMatchers.anExistingFileOrDirectory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

public class TestExport extends BaseTest {

	@Test
	void testExportFile() throws IOException {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		String outFile = cwdDir.resolve("helloworld.jar").toString();
		ExecutionResult result = checkedRun(null, "export", "-O", outFile, src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*helloworld.jar.*"));
	}

	@Test
	void testExportPortableNoclasspath() throws IOException {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		String outFile = cwdDir.resolve("helloworld.jar").toString();
		ExecutionResult result = checkedRun(null, "export", "--portable", "-O", outFile, src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*helloworld.jar.*"));
		assertThat(new File("libs"), not(anExistingFileOrDirectory()));

	}

	@Test
	void testExportPortableWithClasspath() throws IOException {
		String src = examplesTestFolder.resolve("classpath_log.java").toString();
		String outFile = cwdDir.resolve("classpath_log.jar").toString();
		ExecutionResult result = checkedRun(null, "export", "--portable", "-O", outFile, src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*classpath_log.jar.*"));
		assertThat(cwdDir.resolve("libs").toFile(), anExistingDirectory());
		assertThat(cwdDir.resolve("libs").toFile().listFiles().length, Matchers.equalTo(1));

		File jar = new File(outFile);

		try (JarInputStream jarStream = new JarInputStream(new FileInputStream(jar))) {
			Manifest mf = jarStream.getManifest();

			String cp = mf.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
			assertThat(cp, not(containsString("m2")));
		}

		Files.delete(jar.toPath());
	}

	@Test
	void testExportWithClasspath() throws IOException {
		String src = examplesTestFolder.resolve("classpath_log.java").toString();
		String outFile = cwdDir.resolve("classpath_log.jar").toString();
		ExecutionResult result = checkedRun(null, "export", "-O", outFile, src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*classpath_log.jar.*"));
		assertThat(new File("libs"), not(anExistingDirectory()));

		File jar = new File(outFile);

		try (JarInputStream jarStream = new JarInputStream(new FileInputStream(jar))) {
			Manifest mf = jarStream.getManifest();

			String cp = mf.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
			assertThat(cp, containsString("m2"));
		}
		Files.delete(jar.toPath());

	}

}
