package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.anExistingDirectory;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.hamcrest.io.FileMatchers.anExistingFileOrDirectory;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.util.Util;

import picocli.CommandLine;

public class TestExport extends BaseTest {

	@Test
	void testExportFile() throws Exception {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		String outFile = cwdDir.resolve("subdir/helloworld.jar").toString();
		CaptureResult result = checkedRun(null, "export", "local", "-O", outFile, src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*helloworld.jar.*"));
		assertThat(new File(outFile), anExistingFile());
	}

	@Test
	void testExportFileName() throws Exception {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		String outFile = cwdDir.resolve("helloworld").toString();
		CaptureResult result = checkedRun(null, "export", "local", "-O", outFile, src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*helloworld.jar.*"));
		assertThat(new File(outFile + ".jar"), anExistingFile());
	}

	@Test
	void testExportNoFileName() throws Exception {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		String outFile = cwdDir.resolve("helloworld.jar").toString();
		CaptureResult result = checkedRun(null, "export", "local", src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*helloworld.jar.*"));
		assertThat(new File(outFile), anExistingFile());
	}

	@Test
	void testExportPortableNoclasspath() throws Exception {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		String outFile = cwdDir.resolve("helloworld.jar").toString();
		CaptureResult result = checkedRun(null, "export", "portable", "-O", outFile, src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*helloworld.jar.*"));
		assertThat(new File(outFile), anExistingFile());
		assertThat(cwdDir.resolve(ExportPortable.LIB).toFile(), not(anExistingFileOrDirectory()));
	}

	@Test
	void testExportPortableWithClasspath() throws Exception {
		String src = examplesTestFolder.resolve("classpath_log.java").toString();
		String outFile = cwdDir.resolve("classpath_log.jar").toString();
		CaptureResult result = checkedRun(null, "export", "portable", "-O", outFile, src);
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
	void testExportWithClasspath() throws Exception {
		String src = examplesTestFolder.resolve("classpath_log.java").toString();
		String outFile = cwdDir.resolve("classpath_log.jar").toString();
		CaptureResult result = checkedRun(null, "export", "local", "-O", outFile, src);
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
	void testExportMavenPublishNoclasspath() throws Exception {
		File outFile = jbangTempDir.resolve("target").toFile();
		outFile.mkdirs();
		CaptureResult result = checkedRun(null, "export", "mavenrepo", "-O", outFile.toString(),
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
	void testExportMavenPublishNoOutputdir() throws Exception {
		File outFile = jbangTempDir.resolve("target").toFile();
		// outFile.mkdirs();
		CaptureResult result = checkedRun(null, "export", "mavenrepo", "-O", outFile.toString(),
				"--group=my.thing.right", examplesTestFolder.resolve("helloworld.java").toString());
		assertThat(result.result, equalTo(BaseCommand.EXIT_INVALID_INPUT));

	}

	@Test
	void testExportMavenPublishNoGroup() throws Exception {
		File outFile = jbangTempDir.resolve("target").toFile();
		outFile.mkdirs();
		CaptureResult result = checkedRun(null, "export", "mavenrepo", "--force", "-O",
				outFile.toString(), examplesTestFolder.resolve("helloworld.java").toString());
		assertThat(result.result, equalTo(BaseCommand.EXIT_INVALID_INPUT));
		assertThat(result.err, containsString("Add --group=<group id> and run again"));
	}

	@Test
	void testExportMavenPublishWithClasspath() throws Exception {
		Path outFile = mavenTempDir;
		CaptureResult result = checkedRun(null, "export", "mavenrepo", "--force",
				"--group=g.a.v", examplesTestFolder.resolve("classpath_log.java").toString());
		assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
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
	void testExportMavenPublishWithGAV() throws Exception {
		File outFile = jbangTempDir.resolve("target").toFile();
		outFile.mkdirs();
		CaptureResult result = checkedRun(null, "export", "mavenrepo", "-O", outFile.toString(),
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

	@Test
	void testExportFatJar() throws Exception {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		String outFile = cwdDir.resolve("subdir/helloworld.jar").toString();
		CaptureResult result = checkedRun(null, "export", "fatjar", "-O", outFile, src);
		assertThat(result.err, matchesPattern("(?s).*Exported to.*helloworld.jar.*"));
		assertThat(new File(outFile), anExistingFile());
	}

	@Test
	void testExportGradleProject() throws Exception {
		String src = examplesTestFolder.resolve("classpath_log.java").toString();
		File outFile = jbangTempDir.resolve("target").toFile();
		outFile.mkdirs();
		CaptureResult result = checkedRun(null, "export", "gradle", "--force", "-O", outFile.toString(), src);
		assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		Path targetSrcPath = outFile.toPath()
									.resolve("src/main/java/org/example/project/classpath_log/classpath_log.java");
		assertThat(targetSrcPath.toFile(), anExistingFile());
		String targetSrc = Util.readString(targetSrcPath);
		assertThat(targetSrc, containsString("package org.example.project.classpath_log;"));
		Path buildPath = outFile.toPath().resolve("build.gradle");
		assertThat(buildPath.toFile(), anExistingFile());
		String build = Util.readString(buildPath);
		assertThat(build, containsString("implementation 'log4j:log4j:1.2.17'"));
		assertThat(build, containsString("mainClass = 'org.example.project.classpath_log.classpath_log'"));
	}

	@Test
	void testExportGradleProjectWithGAV() throws Exception {
		String src = examplesTestFolder.resolve("classpath_log.java").toString();
		File outFile = jbangTempDir.resolve("target").toFile();
		outFile.mkdirs();
		CaptureResult result = checkedRun(null, "export", "gradle", "--force", "-O", outFile.toString(),
				"-g", "dev.jbang.test", "-a", "app", "-v", "1.2.3", src);
		assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		Path targetSrcPath = outFile.toPath()
									.resolve("src/main/java/dev/jbang/test/app/classpath_log.java");
		assertThat(targetSrcPath.toFile(), anExistingFile());
		String targetSrc = Util.readString(targetSrcPath);
		assertThat(targetSrc, containsString("package dev.jbang.test.app;"));
		Path buildPath = outFile.toPath().resolve("build.gradle");
		assertThat(buildPath.toFile(), anExistingFile());
		String build = Util.readString(buildPath);
		assertThat(build, containsString("implementation 'log4j:log4j:1.2.17'"));
		assertThat(build, containsString("mainClass = 'dev.jbang.test.app.classpath_log'"));
	}

	@Test
	void testExportGradleProjectWithBOM() throws Exception {
		String src = examplesTestFolder.resolve("classpath_log_bom.java").toString();
		File outFile = jbangTempDir.resolve("target").toFile();
		outFile.mkdirs();
		CaptureResult result = checkedRun(null, "export", "gradle", "--force", "-O", outFile.toString(), src);
		assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		Path targetSrcPath = outFile.toPath()
									.resolve(
											"src/main/java/org/example/project/classpath_log_bom/classpath_log_bom.java");
		assertThat(targetSrcPath.toFile(), anExistingFile());
		String targetSrc = Util.readString(targetSrcPath);
		assertThat(targetSrc, containsString("package org.example.project.classpath_log_bom;"));
		Path buildPath = outFile.toPath().resolve("build.gradle");
		assertThat(buildPath.toFile(), anExistingFile());
		String build = Util.readString(buildPath);
		assertThat(build, containsString("implementation platform ('org.apache.logging.log4j:log4j-bom:2.24.3')"));
		assertThat(build, containsString("implementation 'org.apache.logging.log4j:log4j-api'"));
		assertThat(build, containsString("implementation 'org.apache.logging.log4j:log4j-core'"));
		assertThat(build, containsString("mainClass = 'org.example.project.classpath_log_bom.classpath_log_bom'"));
	}

	@Test
	void testExportMavenProject() throws Exception {
		String src = examplesTestFolder.resolve("classpath_log.java").toString();
		File outFile = jbangTempDir.resolve("target").toFile();
		outFile.mkdirs();
		CaptureResult result = checkedRun(null, "export", "maven", "--force", "-O", outFile.toString(), src);
		assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		Path targetSrcPath = outFile.toPath()
									.resolve("src/main/java/org/example/project/classpath_log/classpath_log.java");
		assertThat(targetSrcPath.toFile(), anExistingFile());
		String targetSrc = Util.readString(targetSrcPath);
		assertThat(targetSrc, containsString("package org.example.project.classpath_log;"));
		Path pomPath = outFile.toPath().resolve("pom.xml");
		assertThat(pomPath.toFile(), anExistingFile());
		String pom = Util.readString(pomPath);
		assertThat(pom, stringContainsInOrder(
				"<groupId>org.example.project</groupId>",
				"<artifactId>classpath_log</artifactId>",
				"<version>999-SNAPSHOT</version>",
				"<dependencies>",
				"<groupId>log4j</groupId>",
				"<artifactId>log4j</artifactId>",
				"<version>1.2.17</version>"));
		assertThat(pom, not(containsString("<properties>")));
		assertThat(pom, not(containsString("<dependencyManagement>")));
		assertThat(pom, not(containsString("<repositories>")));
	}

	@Test
	void testExportMavenProjectWithGAV() throws Exception {
		String src = examplesTestFolder.resolve("classpath_log.java").toString();
		File outFile = jbangTempDir.resolve("target").toFile();
		outFile.mkdirs();
		CaptureResult result = checkedRun(null, "export", "maven", "--force", "-O", outFile.toString(),
				"-g", "dev.jbang.test", "-a", "app", "-v", "1.2.3", src);
		assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		Path targetSrcPath = outFile.toPath()
									.resolve("src/main/java/dev/jbang/test/app/classpath_log.java");
		assertThat(targetSrcPath.toFile(), anExistingFile());
		String targetSrc = Util.readString(targetSrcPath);
		assertThat(targetSrc, containsString("package dev.jbang.test.app;"));
		Path pomPath = outFile.toPath().resolve("pom.xml");
		assertThat(pomPath.toFile(), anExistingFile());
		String pom = Util.readString(pomPath);
		assertThat(pom, stringContainsInOrder(
				"<groupId>dev.jbang.test</groupId>",
				"<artifactId>app</artifactId>",
				"<version>1.2.3</version>",
				"<dependencies>",
				"<groupId>log4j</groupId>",
				"<artifactId>log4j</artifactId>",
				"<version>1.2.17</version>"));
		assertThat(pom, not(containsString("<properties>")));
		assertThat(pom, not(containsString("<dependencyManagement>")));
		assertThat(pom, not(containsString("<repositories>")));
	}

	@Test
	void testExportMavenProjectWithBOM() throws Exception {
		String src = examplesTestFolder.resolve("classpath_log_bom.java").toString();
		File outFile = jbangTempDir.resolve("target").toFile();
		outFile.mkdirs();
		CaptureResult result = checkedRun(null, "export", "maven", "--force", "-O", outFile.toString(), src);
		assertThat(result.result, equalTo(BaseCommand.EXIT_OK));
		Path targetSrcPath = outFile.toPath()
									.resolve(
											"src/main/java/org/example/project/classpath_log_bom/classpath_log_bom.java");
		assertThat(targetSrcPath.toFile(), anExistingFile());
		String targetSrc = Util.readString(targetSrcPath);
		assertThat(targetSrc, containsString("package org.example.project.classpath_log_bom;"));
		Path pomPath = outFile.toPath().resolve("pom.xml");
		assertThat(pomPath.toFile(), anExistingFile());
		String pom = Util.readString(pomPath);
		assertThat(pom, stringContainsInOrder(
				"<groupId>org.example.project</groupId>",
				"<artifactId>classpath_log_bom</artifactId>",
				"<version>999-SNAPSHOT</version>",
				"<dependencyManagement>",
				"<groupId>org.apache.logging.log4j</groupId>",
				"<artifactId>log4j-bom</artifactId>",
				"<version>2.24.3</version>",
				"<dependencies>",
				"<groupId>org.apache.logging.log4j</groupId>",
				"<artifactId>log4j-api</artifactId>",
				"<groupId>org.apache.logging.log4j</groupId>",
				"<artifactId>log4j-core</artifactId>"));
		assertThat(pom, not(containsString("<properties>")));
		assertThat(pom, not(containsString("<repositories>")));
	}
}
