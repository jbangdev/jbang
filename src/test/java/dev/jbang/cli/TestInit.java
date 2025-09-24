package dev.jbang.cli;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.aReadableFile;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.jbang.BaseTest;
import dev.jbang.resources.ResourceRef;
import dev.jbang.util.Util;

public class TestInit extends BaseTest {

	@Test
	@Disabled("just for quick testing. requires key to be present")
	void testGPT() {
		String prompt = "write me a helloworld with StringUtils from commons-lang3";
		String result = Init.fetchGptResponse("test", "java", prompt, System.getenv("OPENAI_API_KEY"));
		Assertions.assertEquals(result, "magic");
	}

	@Test
	void testInit(@TempDir Path outputDir) throws IOException {
		Path out = outputDir.resolve("test.java");
		HashMap<String, Object> props = new HashMap<>();
		props.put("baseName", "test");
		new Init().renderQuteTemplate(out, ResourceRef.forResource("classpath:/init-hello.java.qute"), props);
		assertThat(Util.readString(out), Matchers.containsString("class test"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "bad.name.java", "Bad-Name.java" })
	void testInvalidInit(String filename) {
		Exception ex = assertThrows(ExitException.class,
				() -> new Init().renderQuteTemplate(Paths.get(filename),
						ResourceRef.forResource("classpath:/init-hello.java.qute"), Collections.emptyMap()));
		assertThat(ex.getMessage(), Matchers.containsString("is not a valid class name in java."));
	}

	@Test
	void testCli(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		int result = JBang.getCommandLine().execute("init", "--verbose", "--template=cli", s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		MatcherAssert.assertThat(Util.readString(x), Matchers.containsString("picocli"));
	}

	@Test
	void testOldInit(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("nonexist.java");
		String s = x.toString();
		assertEquals(JBang.getCommandLine().execute("--init", s), 2);
	}

	@Test
	void testMissingTemplate(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		int result = JBang.getCommandLine().execute("init", "--template=bogus", s);
		assertThat(result, not(0));
		assertThat(new File(s).exists(), is(false));
	}

	@Test
	void testDepsInit(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		int result = JBang.getCommandLine().execute("init", "--deps", "a.b.c:mydep:1.0", s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		assertThat(Util.readString(x), Matchers.containsString("//DEPS a.b.c:mydep:1.0"));
	}

	@Test
	void testJava25Plus(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("java25.java");
		String s = x.toString();
		int result = JBang.getCommandLine().execute("init", "--java", "25", s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		assertThat(Util.readString(x), Matchers.containsString("\nvoid main(String... args)"));
	}

	@Test
	void testMultiDepsInit(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		int result = JBang.getCommandLine().execute("init", "--deps", "a.b.c:mydep:1.0", "--deps", "q.z:rrr:2.0", s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		assertThat(Util.readString(x), Matchers.containsString("//DEPS a.b.c:mydep:1.0"));
		assertThat(Util.readString(x), Matchers.containsString("//DEPS q.z:rrr:2.0"));
	}

	@Test
	void testMultiDepsInitUsingCommas(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("sqlline.java");
		String s = x.toString();
		int result = JBang.getCommandLine()
			.execute(
					"init",
					"--deps", "org.hsqldb:hsqldb:2.5.0,net.hydromatic:foodmart-data-hsqldb:0.4",
					"--deps", "org.another.company:dep:0.1",
					s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		final String fileContent = Util.readString(x);
		assertThat(fileContent, Matchers.containsString("//DEPS org.hsqldb:hsqldb:2.5.0"));
		assertThat(fileContent, Matchers.containsString("//DEPS net.hydromatic:foodmart-data-hsqldb:0.4"));
		assertThat(fileContent, Matchers.containsString("//DEPS org.another.company:dep:0.1"));
	}

	@Test
	void testDefaultInit(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		int result = JBang.getCommandLine().execute("init", s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		assertThat(Util.readString(x), Matchers.containsString("class edit"));
	}

	@Test
	void testInitExtensionlessKebab(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("xyz-plug");
		String s = x.toString();
		int result = JBang.getCommandLine().execute("init", s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		assertThat(Util.readString(x), Matchers.containsString("class XyzPlug"));
	}

	@Test
	void testInitExtensionless(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("xyzplug");
		String s = x.toString();
		int result = JBang.getCommandLine().execute("init", s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		assertThat(Util.readString(x), Matchers.containsString("class xyzplug"));
	}

	@Test
	void testCatalog() throws IOException {
//		int result = JBang.getCommandLine().execute("run", "jget@quintesse");
//		int result = JBang.getCommandLine().execute("catalog", "update");
		int result = JBang.getCommandLine().execute("catalog", "list", "quintesse");
		assertThat(result, is(0));
	}

	@Test
	void testInitMultipleFilesWithExt() throws IOException {
		testInitMultipleFiles("{filename}", "edit.java", "edit.java", false);
	}

	@Test
	void testInitMultipleFilesNoExt() throws IOException {
		testInitMultipleFiles("{filename}", "edit", "edit", false);
	}

	@Test
	void testInitMultipleFilesImplicitExt() throws IOException {
		testInitMultipleFiles("{basename}.java", "edit", "edit.java", false);
	}

	@Test
	void testInitMultipleFilesOutSub() throws IOException {
		testInitMultipleFiles("{basename}.java", "sub/edit", "edit.java", false);
	}

	@Test
	void testInitMultipleFilesAbsPaths() throws IOException {
		testInitMultipleFiles("{filename}", "edit.java", "edit.java", true);
	}

	@Test
	void testInitMultipleFilesWrongName() throws IOException {
		testFailMultipleFiles("{filename}", "edit.md", "edit.java", true, 2);
	}

	void testInitMultipleFiles(String targetName, String initName, String outName, boolean abs) throws IOException {
		Path outFile = setupInitMultipleFiles(targetName, initName, abs);
		int result = JBang.getCommandLine().execute("init", "-t=name", outFile.toString());
		assertThat(result, is(0));
		assertThat(outFile.resolveSibling(outName).toFile(), aReadableFile());
		Path f2 = outFile.resolveSibling("file2.java");
		assertThat(f2.toFile(), aReadableFile());
		String baseName = Util.getBaseName(Paths.get(initName).getFileName().toString());
		assertThat(Util.readString(f2), Matchers.containsString("// " + baseName + " with " + outFile));
		assertThat(outFile.resolveSibling("file3.md").toFile(), aReadableFile());

	}

	void testFailMultipleFiles(String targetName, String initName, String outName, boolean abs, int expectedResult)
			throws IOException {
		Path outFile = setupInitMultipleFiles(targetName, initName, abs);
		int result = JBang.getCommandLine().execute("init", "-t=name", outFile.toString());
		assertThat(result, is(expectedResult));
	}

	Path setupInitMultipleFiles(String targetName, String initName, boolean abs) throws IOException {
		Path cwd = Util.getCwd();
		Path tplDir = Files.createDirectory(cwd.resolve("tpl"));
		Path f1 = Files.createFile(tplDir.resolve("file1.java"));
		Path f2 = Files.createFile(tplDir.resolve("file2.java.qute"));
		Util.writeString(f2, "// {baseName} with {scriptref}");
		Path f3 = Files.createFile(tplDir.resolve("file3.md"));
		if (abs) {
			int addResult = JBang.getCommandLine()
				.execute("template", "add", "-f", cwd.toString(), "--name=name",
						targetName + "=" + f1.toString(), f2.toString(), f3.toString());
			assertThat(addResult, is(0));
		} else {
			int addResult = JBang.getCommandLine()
				.execute("template", "add", "-f", cwd.toString(), "--name=name",
						targetName + "=tpl/file1.java", "tpl/file2.java.qute", "tpl/file3.md");
			assertThat(addResult, is(0));
		}
		Path appDir = Files.createDirectory(cwd.resolve("app"));
		Util.setCwd(appDir);
		return appDir.resolve(initName);
	}

	@Test
	void testProperties() throws IOException {
		Path cwd = Util.getCwd();
		Files.write(cwd.resolve("file1.java.qute"), "{prop1}{prop2}".getBytes());

		Path out = cwd.resolve("result.java");
		Map<String, Object> m = new HashMap<>();
		m.put("prop1", "propvalue");
		m.put("prop2", "rocks");

		ResourceRef ref = ResourceRef.forFile(cwd.resolve("file1.java.qute"));
		new Init().renderQuteTemplate(out, ref, m);

		String outcontent = Util.readString(out);

		assertThat(outcontent, containsString("propvaluerocks"));
	}

	@Test
	void testInitProperties() throws IOException {
		Path cwd = Util.getCwd();
		Path f1 = Files.write(cwd.resolve("file1.java.qute"), "{prop1}{prop2}".getBytes());
		Path out = cwd.resolve("result.java");

		JBang.getCommandLine()
			.execute("template", "add", "-f", cwd.toString(), "--name=name",
					"{filename}" + "=" + f1.toAbsolutePath().toString());

		assertThat(out.toFile().exists(), not(true));

		int result = JBang.getCommandLine()
			.execute("init", "--verbose", "--template=name", "-Dprop1=propvalue", "-Dprop2=rocks",
					out.toAbsolutePath().toString());

		assertThat(result, is(0));
		assertThat(out.toFile().exists(), is(true));

		String outcontent = Util.readString(out);

		assertThat(outcontent, containsString("propvaluerocks"));
	}

	@Test
	void testInitPropertiesWithDefaults() throws IOException {
		Path cwd = Util.getCwd();
		Path f1 = Files.write(cwd.resolve("file1.java.qute"), "{prop1}".getBytes());
		Path out = cwd.resolve("result.java");

		JBang.getCommandLine()
			.execute("template", "add", "-f", cwd.toString(), "--name=name", "-P=prop1::my-test-default-value",
					"{filename}" + "=" + f1.toAbsolutePath().toString());

		assertThat(out.toFile().exists(), not(true));

		int result = JBang.getCommandLine()
			.execute("init", "--verbose", "--template=name",
					out.toAbsolutePath().toString());

		assertThat(result, is(0));
		assertThat(out.toFile().exists(), is(true));

		String outcontent = Util.readString(out);

		assertThat(outcontent, containsString("my-test-default-value"));
	}

	@Test
	void testInitPropertiesWithPropertyWithoutDefaultValue() throws IOException {
		Path cwd = Util.getCwd();
		Path f1 = Files.write(cwd.resolve("file1.java.qute"), "{prop1}".getBytes());
		Path out = cwd.resolve("result.java");

		JBang.getCommandLine()
			.execute("template", "add", "-f", cwd.toString(), "--name=name", "-P=prop1::",
					"{filename}" + "=" + f1.toAbsolutePath().toString());

		assertThat(out.toFile().exists(), not(true));

		int result = JBang.getCommandLine()
			.execute("init", "--verbose", "--template=name",
					out.toAbsolutePath().toString());

		assertThat(result, is(0));
		assertThat(out.toFile().exists(), is(true));

		String outcontent = Util.readString(out);

		assertThat(outcontent, containsString("NOT_FOUND"));
	}

	@Test
	void testInitPropertiesIgnoringPropertyDefaults() throws IOException {
		Path cwd = Util.getCwd();
		Path f1 = Files.write(cwd.resolve("file1.java.qute"), "{prop1}".getBytes());
		Path out = cwd.resolve("result.java");

		JBang.getCommandLine()
			.execute("template", "add", "-f", cwd.toString(), "--name=name", "-P=prop1::my-test-default-value",
					"{filename}" + "=" + f1.toAbsolutePath().toString());

		assertThat(out.toFile().exists(), not(true));

	}

}
