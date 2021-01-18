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
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

import dev.jbang.BaseTest;

public class TestExport extends BaseTest {

	@Rule
	public final TemporaryFolder testTempDir = new TemporaryFolder();

	private Path out;

	@BeforeEach
	public void init() throws IOException {
		testTempDir.create();
		out = testTempDir.getRoot().toPath();
	}

	@AfterEach
	public void cleanup() {
		testTempDir.delete();
	}

	@Test
	void testExportFile() throws IOException {
		String outFile = out.resolve("helloworld.jar").toString();
		ExecutionResult result = checkedRun(null, "export", "-O", outFile, "itests/helloworld.java");
		assertThat(result.err, matchesPattern("(?s).*Exported to.*helloworld.jar.*"));
	}

	@Test
	void testExportPortableNoclasspath() throws IOException {
		String outFile = out.resolve("helloworld.jar").toString();
		ExecutionResult result = checkedRun(null, "export", "--portable", "-O", outFile, "itests/helloworld.java");
		assertThat(result.err, matchesPattern("(?s).*Exported to.*helloworld.jar.*"));
		assertThat(new File("libs"), not(anExistingFileOrDirectory()));

	}

	@Test
	void testExportPortableWithClasspath() throws IOException {
		String outFile = out.resolve("classpath_log.jar").toString();
		ExecutionResult result = checkedRun(null, "export", "--portable", "-O", outFile, "itests/classpath_log.java");
		assertThat(result.err, matchesPattern("(?s).*Exported to.*classpath_log.jar.*"));
		assertThat(new File(out.toFile(), "libs"), anExistingDirectory());
		assertThat(new File(out.toFile(), "libs").listFiles().length, Matchers.equalTo(1));

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
		String outFile = out.resolve("classpath_log.jar").toString();
		ExecutionResult result = checkedRun(null, "export", "-O", outFile, "itests/classpath_log.java");
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
