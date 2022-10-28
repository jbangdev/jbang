package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.anExistingDirectory;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.hamcrest.io.FileMatchers.anExistingFileOrDirectory;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Settings;

import picocli.CommandLine;

public class TestExport extends BaseTest {

	@Test
	void testExportFile() throws IOException {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		String outFile = cwdDir.resolve("helloworld.jar").toString();
		ExecutionResult result = checkedRun(null, "export", "local", "-O", outFile, src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*helloworld.jar.*"));
		assertThat(new File(outFile), anExistingFile());
	}

	@Test
	void testExportFileName() throws IOException {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		String outFile = cwdDir.resolve("helloworld").toString();
		ExecutionResult result = checkedRun(null, "export", "local", "-O", outFile, src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*helloworld.jar.*"));
		assertThat(new File(outFile + ".jar"), anExistingFile());
	}

	@Test
	void testExportNoFileName() throws IOException {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		String outFile = cwdDir.resolve("helloworld.jar").toString();
		ExecutionResult result = checkedRun(null, "export", "local", src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*helloworld.jar.*"));
		assertThat(new File(outFile), anExistingFile());
	}

	@Test
	void testExportPortableNoclasspath() throws IOException {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		String outFile = cwdDir.resolve("helloworld.jar").toString();
		ExecutionResult result = checkedRun(null, "export", "portable", "-O", outFile, src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*helloworld.jar.*"));
		assertThat(new File(outFile), anExistingFile());
		assertThat(cwdDir.resolve(ExportPortable.LIB).toFile(), not(anExistingFileOrDirectory()));
	}

	@Test
	void testExportPortableWithClasspath() throws IOException {
		String src = examplesTestFolder.resolve("classpath_log.java").toString();
		String outFile = cwdDir.resolve("classpath_log.jar").toString();
		ExecutionResult result = checkedRun(null, "export", "portable", "-O", outFile, src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*classpath_log.jar.*"));
		assertThat(new File(outFile), anExistingFile());
		assertThat(cwdDir.resolve(ExportPortable.LIB).toFile(), anExistingDirectory());
		assertThat(cwdDir.resolve(ExportPortable.LIB).toFile().listFiles().length, Matchers.equalTo(1));

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
		ExecutionResult result = checkedRun(null, "export", "local", "-O", outFile, src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*classpath_log.jar.*"));
		assertThat(new File(outFile), anExistingFile());
		assertThat(cwdDir.resolve(ExportPortable.LIB).toFile(), not(anExistingDirectory()));

		File jar = new File(outFile);

		try (JarInputStream jarStream = new JarInputStream(new FileInputStream(jar))) {
			Manifest mf = jarStream.getManifest();

			String cp = mf.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
			assertThat(cp, containsString("jbang_tests_maven"));
		}
		Files.delete(jar.toPath());

	}

	@Test
	void testExportMavenPublishNoclasspath() throws IOException {
		File outFile = jbangTempDir.resolve("target").toFile();
		outFile.mkdirs();
		ExecutionResult result = checkedRun(null, "export", "mavenrepo", "-O", outFile.toString(),
				"--group=my.thing.right", examplesTestFolder.resolve("helloworld.java").toString());
		assertThat(result.err, matchesPattern("(?s).*Exported to.*target.*"));
		assertThat(
				outFile.toPath().resolve("my/thing/right/helloworld/999-SNAPSHOT/helloworld-999-SNAPSHOT.jar").toFile(),
				anExistingFile());
		assertThat(
				outFile.toPath().resolve("my/thing/right/helloworld/999-SNAPSHOT/helloworld-999-SNAPSHOT.pom").toFile(),
				anExistingFile());

	}

	@Test
	void testExportMavenPublishNoOutputdir() throws IOException {
		File outFile = jbangTempDir.resolve("target").toFile();
		// outFile.mkdirs();
		ExecutionResult result = checkedRun(null, "export", "mavenrepo", "-O", outFile.toString(),
				"--group=my.thing.right", examplesTestFolder.resolve("helloworld.java").toString());
		assertThat(result.exitCode, equalTo(BaseCommand.EXIT_INVALID_INPUT));

	}

	@Test
	void testExportMavenPublishNoGroup() throws IOException {
		File outFile = jbangTempDir.resolve("target").toFile();
		outFile.mkdirs();
		ExecutionResult result = checkedRun(null, "export", "mavenrepo", "--force", "-O",
				outFile.toString(), examplesTestFolder.resolve("helloworld.java").toString());
		assertThat(result.exitCode, equalTo(BaseCommand.EXIT_INVALID_INPUT));
		assertThat(result.err, containsString("Add --group=<group id> and run again"));
	}

	@Test
	void testExportMavenPublishWithClasspath() throws IOException {
		Path outFile = Settings.getLocalMavenRepo();
		ExecutionResult result = checkedRun(null, "export", "mavenrepo", "--force",
				"--group=g.a.v", examplesTestFolder.resolve("classpath_log.java").toString());
		assertThat(result.exitCode, equalTo(BaseCommand.EXIT_OK));
		assertThat(outFile.resolve("g/a/v/classpath_log/999-SNAPSHOT/classpath_log-999-SNAPSHOT.jar").toFile(),
				anExistingFile());
		assertThat(outFile.resolve("g/a/v/classpath_log/999-SNAPSHOT/classpath_log-999-SNAPSHOT.pom").toFile(),
				anExistingFile());

		Files	.walk(outFile.resolve("g"))
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);

	}

	@Test
	void testExportMavenPublishWithGAV() throws IOException {
		File outFile = jbangTempDir.resolve("target").toFile();
		outFile.mkdirs();
		ExecutionResult result = checkedRun(null, "export", "mavenrepo", "-O", outFile.toString(),
				examplesTestFolder.resolve("quote.java").toString());
		assertThat(result.err, matchesPattern("(?s).*Exported to.*target.*"));
		assertThat(
				outFile.toPath().resolve("dev/jbang/itests/quote/999-SNAPSHOT/quote-999-SNAPSHOT.jar").toFile(),
				anExistingFile());
		assertThat(
				outFile.toPath().resolve("dev/jbang/itests/quote/999-SNAPSHOT/quote-999-SNAPSHOT.pom").toFile(),
				anExistingFile());

	}

	@Test
	void testExportMissingScript() {
		assertThrows(IllegalArgumentException.class, () -> {
			CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("export", "local");
			ExportLocal export = (ExportLocal) pr.subcommand().subcommand().commandSpec().userObject();
			export.doCall();
		});
	}
}
