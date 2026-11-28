package dev.jbang.cli;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.aReadableFile;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.jbang.BaseTest;
import dev.jbang.ExitException;
import dev.jbang.ai.AIProvider;
import dev.jbang.ai.AIProviderFactory;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.RefTarget;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public class TestInit extends BaseTest {

	@Test
	@Disabled("just for quick testing. requires key to be present")
	void testGPT() throws IOException {
		String prompt = "write me a helloworld with StringUtils from commons-lang3";
		AIProvider provider = new AIProviderFactory().createProvider();
		if (provider != null) {
			String result = provider.generateCode("test", "java", prompt, "25");
			Assertions.assertNotNull(result);
			Assertions.assertNotEquals(result, "");
		} else {
			Assertions.fail("No AI provider available. Set OPENAI_API_KEY or OPENROUTER_API_KEY");
		}
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
		int result = JBang.execute("init", "--verbose", "--template=cli", s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		MatcherAssert.assertThat(Util.readString(x), Matchers.containsString("picocli"));
	}

	@Test
	void testInitWithExistingFileAsParent(@TempDir Path outputDir) throws Exception {
		Path existing = Files.createFile(outputDir.resolve("existing.java"));
		Path x = existing.resolve("wonka.java");
		CaptureResult<Integer> result = checkedRun("init", x.toString());
		assertThat(result.result, is(ExitException.EXIT_GENERIC_ERROR));
		assertThat(result.err, containsString(existing + " is not a directory"));
		assertThat(Files.isRegularFile(existing), is(true));
		assertThat(Files.exists(x), is(false));
	}

	@Test
	void testInitWithExistingFileAsAncestor(@TempDir Path outputDir) throws Exception {
		Path existing = Files.createFile(outputDir.resolve("existing.java"));
		Path x = existing.resolve("sub").resolve("wonka.java");
		CaptureResult<Integer> result = checkedRun("init", x.toString());
		assertThat(result.result, is(ExitException.EXIT_GENERIC_ERROR));
		assertThat(result.err, containsString(existing + " is not a directory"));
		assertThat(Files.isRegularFile(existing), is(true));
	}

	@Test
	void testInitChecksTargetBeforeCallingAI(@TempDir Path outputDir) throws Exception {
		Path existing = Files.createFile(outputDir.resolve("existing.java"));
		Path x = existing.resolve("wonka.java");
		// closed port: if the AI step ran, init would fail with EXIT_INTERNAL_ERROR
		// first
		CaptureResult<Integer> result = checkedRun("init", "--ai-provider=openai", "--ai-api-key=dummy",
				"--ai-endpoint=http://127.0.0.1:1/v1", "--ai-model=x", x.toString(), "a hello world");
		assertThat(result.result, is(ExitException.EXIT_GENERIC_ERROR));
		assertThat(result.err, containsString(existing + " is not a directory"));
	}

	@Test
	void testInitWithDanglingSymlinkParentChecksBeforeCallingAI(@TempDir Path outputDir) throws Exception {
		Path link = outputDir.resolve("link");
		assumeTrue(createSymlink(link, outputDir.resolve("missing")), "symlinks not supported");
		Path x = link.resolve("wonka.java");
		// closed port: if the AI step ran, init would fail with EXIT_INTERNAL_ERROR
		// first
		CaptureResult<Integer> result = checkedRun("init", "--ai-provider=openai", "--ai-api-key=dummy",
				"--ai-endpoint=http://127.0.0.1:1/v1", "--ai-model=x", x.toString(), "a hello world");
		assertThat(result.result, is(ExitException.EXIT_GENERIC_ERROR));
		assertThat(result.err, containsString(link + " is not a directory"));
	}

	private static boolean createSymlink(Path link, Path target) {
		try {
			Files.createSymbolicLink(link, target);
			return true;
		} catch (IOException | UnsupportedOperationException e) {
			return false;
		}
	}

	@Test
	void testOldInit(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("nonexist.java");
		String s = x.toString();
		assertEquals(JBang.execute("--init", s), 2);
	}

	@Test
	void testMissingTemplate(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		int result = JBang.execute("init", "--template=bogus", s);
		assertThat(result, not(0));
		assertThat(new File(s).exists(), is(false));
	}

	@Test
	void testDepsInit(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		int result = JBang.execute("init", "--deps", "a.b.c:mydep:1.0", s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		assertThat(Util.readString(x), Matchers.containsString("//DEPS a.b.c:mydep:1.0"));
	}

	@Test
	void testJava25Plus(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("java25.java");
		String s = x.toString();
		int result = JBang.execute("init", "--java", "25", s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		assertThat(Util.readString(x), Matchers.containsString("\nvoid main(String... args)"));
	}

	@Test
	void testMultiDepsInit(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		int result = JBang.execute("init", "--deps", "a.b.c:mydep:1.0", "--deps", "q.z:rrr:2.0", s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		assertThat(Util.readString(x), Matchers.containsString("//DEPS a.b.c:mydep:1.0"));
		assertThat(Util.readString(x), Matchers.containsString("//DEPS q.z:rrr:2.0"));
	}

	@Test
	void testMultiDepsInitUsingCommas(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("sqlline.java");
		String s = x.toString();
		int result = JBang.execute(
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

	/**
	 * The default template uses a compact source file (no class) when running on
	 * Java 25+, so what we expect depends on the Java version running the tests.
	 */
	private static String expectedDefaultInit(String className) {
		return JavaUtil.getCurrentMajorJavaVersion() >= 25 ? "void main(" : "class " + className;
	}

	@Test
	void testDefaultInit(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("edit.java");
		String s = x.toString();
		int result = JBang.execute("init", s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		assertThat(Util.readString(x), Matchers.containsString(expectedDefaultInit("edit")));
	}

	@ParameterizedTest
	@CsvSource({ "kt, public fun main()", "groovy, println" })
	void testDefaultLanguageInit(String extension, String expectedContent, @TempDir Path outputDir) throws IOException {
		Path out = outputDir.resolve("edit." + extension);
		int result = JBang.execute("init", out.toString());
		assertThat(result, is(0));
		assertThat(out.toFile().exists(), is(true));
		assertThat(Util.readString(out), Matchers.containsString(expectedContent));
	}

	@Test
	void testInitExtensionlessKebab(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("xyz-plug");
		String s = x.toString();
		int result = JBang.execute("init", s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		assertThat(Util.readString(x), Matchers.containsString(expectedDefaultInit("XyzPlug")));
	}

	@Test
	void testInitExtensionless(@TempDir Path outputDir) throws IOException {
		Path x = outputDir.resolve("xyzplug");
		String s = x.toString();
		int result = JBang.execute("init", s);
		assertThat(result, is(0));
		assertThat(new File(s).exists(), is(true));
		assertThat(Util.readString(x), Matchers.containsString(expectedDefaultInit("xyzplug")));
	}

	@Test
	void testCatalog() throws IOException {
//		int result = JBang.execute("run", "jget@quintesse");
//		int result = JBang.execute("catalog", "update");
		int result = JBang.execute("catalog", "list", "quintesse");
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

	@Test
	void testInitMultipleFilesWrongName2() throws IOException {
		testFailMultipleFiles("{basename}.java", "edit.md", "edit.java", true, 2);
	}

	void testInitMultipleFiles(String targetName, String initName, String outName, boolean abs) throws IOException {
		Path outFile = setupInitMultipleFiles(targetName, initName, abs);
		int result = JBang.execute("init", "-t=name", outFile.toString());
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
		int result = JBang.execute("init", "-t=name", outFile.toString());
		assertThat(result, is(expectedResult));
	}

	@Test
	void testInitFailureKeepsPreexistingFiles() throws Exception {
		Path outFile = setupInitMultipleFiles("{filename}", "edit.java", false);
		// pre-existing file that --force overwrites; a failed init must not delete it
		Path edit = Files.write(outFile, "mine".getBytes());
		// a non-empty directory where the qute template file2.java goes makes rendering
		// fail
		Path file2 = Files.createDirectory(outFile.resolveSibling("file2.java"));
		Path keep = Files.createFile(file2.resolve("keep.txt"));

		int result = JBang.execute("init", "--force", "-t=name", outFile.toString());

		assertThat(result, is(ExitException.EXIT_GENERIC_ERROR));
		assertThat(Files.exists(edit), is(true));
		assertThat(Files.exists(keep), is(true));
	}

	@Test
	void testInitFailureRemovesCreatedFilesWhenCopyFails() throws Exception {
		Path outFile = setupInitMultipleFiles("{filename}", "edit.java", false);
		// a non-empty dir where the plain file3.md goes makes RefTarget.copy() fail
		Path file3 = Files.createDirectory(outFile.resolveSibling("file3.md"));
		Files.createFile(file3.resolve("keep.txt"));

		CaptureResult<Integer> result = captureOutput(
				() -> JBang.execute("init", "--force", "-t=name", outFile.toString()));

		assertThat(result.result, not(0));
		assertThat(result.err, containsString("Removing partially initialized files:"));
		assertThat(result.err, containsString("  " + outFile));
		assertThat(Files.exists(file3.resolve("keep.txt")), is(true));
		// files written by this run before the failure must be removed again
		assertThat(Files.exists(outFile), is(false));
		assertThat(Files.exists(outFile.resolveSibling("file2.java")), is(false));
	}

	@Test
	void testInitFailureKeepsPreexistingDanglingSymlinkTarget() throws Exception {
		Path outFile = setupInitMultipleFiles("{filename}", "edit.java", false);
		// a dangling symlink "exists" too: --force replaces it, but a failed init must
		// not then delete what is there
		assumeTrue(createSymlink(outFile, outFile.resolveSibling("missing.java")), "symlinks not supported");
		Path file3 = Files.createDirectory(outFile.resolveSibling("file3.md"));
		Files.createFile(file3.resolve("keep.txt"));

		int result = JBang.execute("init", "--force", "-t=name", outFile.toString());

		assertThat(result, not(0));
		assertThat(Files.exists(outFile, LinkOption.NOFOLLOW_LINKS), is(true));
		// file2.java was created by this run and is removed again
		assertThat(Files.exists(outFile.resolveSibling("file2.java")), is(false));
	}

	@Test
	void testWriteFilesNeitherOverwritesNorRemovesFileItDidNotCreate() throws Exception {
		Path cwd = Util.getCwd();
		Path src1 = Files.write(cwd.resolve("src1.txt"), "one".getBytes());
		Path src2 = Files.write(cwd.resolve("src2.txt"), "two".getBytes());
		Path outDir = Files.createDirectory(cwd.resolve("out"));
		List<RefTarget> refTargets = Arrays.asList(
				RefTarget.create(ResourceRef.forFile(src1), Paths.get("first.txt")),
				RefTarget.create(ResourceRef.forFile(src2), Paths.get("second.txt")));
		// appeared after init's own "already exists" check, e.g. while waiting for the
		// AI
		Path second = Files.write(outDir.resolve("second.txt"), "theirs".getBytes());
		// BaseTest runs verbose, which keeps partial files for inspection
		Util.setVerbose(false);

		ExitException e = assertThrows(ExitException.class,
				() -> new Init().writeFiles(refTargets, outDir, Collections.emptyMap()));

		assertThat(e.getMessage(), containsString(second + " already exists"));
		assertThat(Util.readString(second), is("theirs"));
		// first.txt was created by this call and is removed again
		assertThat(Files.exists(outDir.resolve("first.txt")), is(false));
	}

	@Test
	void testInitTemplateRenderFailureLeavesNothingBehind() throws Exception {
		Path outFile = setupInitMultipleFiles("{filename}", "edit.java", false);
		Path tpl = outFile.getParent().getParent().resolve("tpl").resolve("file2.java.qute");
		// fails while rendering (not while parsing), after other files were already
		// written
		Util.writeString(tpl, "{#include nothere /}");

		int result = JBang.execute("init", "-t=name", outFile.toString());

		assertThat(result, not(0));
		// nothing left behind, so a plain retry (no --force) is not refused with
		// "already exists"
		try (Stream<Path> files = Files.list(outFile.getParent())) {
			assertThat(files.count(), is(0L));
		}
	}

	@Test
	void testInitFailureKeepsCreatedFilesWhenVerbose() throws Exception {
		Path outFile = setupInitMultipleFiles("{filename}", "edit.java", false);
		Path file3 = Files.createDirectory(outFile.resolveSibling("file3.md"));
		Files.createFile(file3.resolve("keep.txt"));

		CaptureResult<Integer> result = captureOutput(
				() -> JBang.execute("--verbose", "init", "--force", "-t=name", outFile.toString()));

		assertThat(result.result, not(0));
		assertThat(result.err, containsString("Keeping partially initialized files for inspection (--verbose):"));
		assertThat(result.err, containsString("  " + outFile));
		assertThat(Files.exists(outFile), is(true));
	}

	@Test
	void testRenderErrorDoesNotCreateOrTruncateFile() throws Exception {
		Path cwd = Util.getCwd();
		Path tpl = Files.write(cwd.resolve("broken.java.qute"), "{#include nothere /}".getBytes());
		ResourceRef ref = ResourceRef.forFile(tpl);
		Map<String, Object> props = Collections.emptyMap();

		Path newFile = cwd.resolve("fresh.java");
		assertThrows(RuntimeException.class,
				() -> new Init().renderQuteTemplate(newFile, ref, props));
		assertThat(Files.exists(newFile), is(false));

		Path existing = Files.write(cwd.resolve("existing.java"), "mine".getBytes());
		assertThrows(RuntimeException.class,
				() -> new Init().renderQuteTemplate(existing, ref, props));
		assertThat(Util.readString(existing), is("mine"));
	}

	Path setupInitMultipleFiles(String targetName, String initName, boolean abs) throws IOException {
		Path cwd = Util.getCwd();
		Path tplDir = Files.createDirectory(cwd.resolve("tpl"));
		Path f1 = Files.createFile(tplDir.resolve("file1.java"));
		Path f2 = Files.createFile(tplDir.resolve("file2.java.qute"));
		Util.writeString(f2, "// {baseName} with {scriptref}");
		Path f3 = Files.createFile(tplDir.resolve("file3.md"));
		if (abs) {
			int addResult = JBang.execute("template", "add", "-f", cwd.toString(), "--name=name",
					targetName + "=" + f1.toString(), f2.toString(), f3.toString());
			assertThat(addResult, is(0));
		} else {
			int addResult = JBang.execute("template", "add", "-f", cwd.toString(), "--name=name",
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

		JBang.execute("template", "add", "-f", cwd.toString(), "--name=name",
				"{filename}" + "=" + f1.toAbsolutePath().toString());

		assertThat(out.toFile().exists(), not(true));

		int result = JBang.execute("init", "--verbose", "--template=name", "-Dprop1=propvalue", "-Dprop2=rocks",
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

		JBang.execute("template", "add", "-f", cwd.toString(), "--name=name", "-P=prop1::my-test-default-value",
				"{filename}" + "=" + f1.toAbsolutePath().toString());

		assertThat(out.toFile().exists(), not(true));

		int result = JBang.execute("init", "--verbose", "--template=name",
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

		JBang.execute("template", "add", "-f", cwd.toString(), "--name=name", "-P=prop1::",
				"{filename}" + "=" + f1.toAbsolutePath().toString());

		assertThat(out.toFile().exists(), not(true));

		int result = JBang.execute("init", "--verbose", "--template=name",
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

		JBang.execute("template", "add", "-f", cwd.toString(), "--name=name", "-P=prop1::my-test-default-value",
				"{filename}" + "=" + f1.toAbsolutePath().toString());

		assertThat(out.toFile().exists(), not(true));

	}

	@Test
	void testInitUsingTemplateWithFilenameAndBasename() throws IOException {
		Path cwd = Util.getCwd();
		Path javaFileQute = Files.write(cwd.resolve("file1.java.qute"), "Hello World".getBytes());
		Path tfFileQute = Files.write(cwd.resolve("file2.tf.qute"), "Hello World from .tf file".getBytes());
		Path outJava = cwd.resolve("result.java");
		Path outTf = cwd.resolve("prefixed-result.tf");

		JBang.execute("template", "add", "-f", cwd.toString(), "--name=template-with-more-files",
				"{filename}" + "=" + javaFileQute.toAbsolutePath().toString(),
				"prefixed-{basename}.tf" + "=" + tfFileQute.toAbsolutePath().toString());

		assertThat(outJava.toFile().exists(), not(true));

		int result = JBang.execute("init", "--verbose", "--template=template-with-more-files",
				outJava.toAbsolutePath().toString());

		assertThat(result, is(0));
		assertThat(outJava.toFile().exists(), is(true));
		assertThat(outTf.toFile().exists(), is(true));

		String javaContent = Util.readString(outJava);
		String tfContent = Util.readString(outTf);

		assertThat(javaContent, containsString("Hello World"));
		assertThat(tfContent, containsString("Hello World from .tf file"));

	}

	@Test
	void testResolveBaseNameShouldResolveFilenameOrBasename() {
		// Use case when the {filename} should be resolved to the input name -
		// result.java
		String scriptName = "result.java";
		Path path = Init.resolveBaseName("{filename}", "template-for-script.java", scriptName);
		assertThat(path.toString(), containsString("result.java"));

		// Use case when the {filename} can not be resolved because the input is
		// result.java but the reference source has .tf extension not .java
		ExitException exitException = assertThrows(ExitException.class,
				() -> Init.resolveBaseName("{filename}", "template-for-script.tf", scriptName));
		String exceptionMessage = "Template expects tf extension, not java";
		assertThat(exitException.getMessage(), containsString(exceptionMessage));

		// Use case when the {basename} is being used with prefix and with not .java
		// extension, and the reference soure also has the .tf extension
		path = Init.resolveBaseName("template-for-{basename}.tf", "template-for-script.tf", scriptName);
		assertThat(path.toString(), containsString("template-for-result.tf"));
	}
}
