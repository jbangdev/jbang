package dev.jbang.cli;

import static dev.jbang.cli.BaseScriptCommand.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import org.hamcrest.MatcherAssert;
import org.hamcrest.collection.IsCollectionWithSize;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.ExitException;
import dev.jbang.Script;
import dev.jbang.Util;

import picocli.CommandLine;

public class TestRun {

	public static final String EXAMPLES_FOLDER = "examples";
	static File examplesTestFolder;

	@BeforeAll
	static void init() throws URISyntaxException, IOException {
		URL examplesUrl = TestRun.class.getClassLoader().getResource(EXAMPLES_FOLDER);
		examplesTestFolder = new File(new File(examplesUrl.toURI()).getAbsolutePath());
	}

	@Rule
	public static final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Test
	void testHelloWorld() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		Jbang jbang = new Jbang();
		String arg = new File(examplesTestFolder, "helloworld.java").getAbsolutePath();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		String result = run.generateCommandLine(
				new Script(new File("helloworld.java"), "", run.userParams, run.properties));

		assertThat(result, startsWith("java "));
		assertThat(result, containsString("helloworld.java"));
		// assertThat(result, containsString("--source 11"));
	}

	@Test
	void testHelloWorldShell() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		Jbang jbang = new Jbang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "a");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		String result = run.generateCommandLine(
				new Script(new File("helloworld.jsh"), "", run.userParams, run.properties));

		assertThat(result, matchesPattern("^.*jshell(.exe)? --startup.*$"));
		assertThat(result, not(containsString("  ")));
		assertThat(result, containsString("helloworld.jsh"));
		assertThat(result, not(containsString("--source 11")));
		assertThat(result, containsString("--startup=DEFAULT"));
		assertThat(result, matchesPattern(".*--startup=[^ ]*helloworld.jsh.*"));
		assertThat(result, not(containsString("blah")));
		assertThat(result, containsString("jbang_exit_"));
	}

	@Test
	void testHelloWorldJar() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		Jbang jbang = new Jbang();

		String jar = new File(examplesTestFolder, "helloworld.jar").getAbsolutePath();

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		Script s = prepareScript(jar, run.userParams, run.properties);

		String result = run.generateCommandLine(s);
		assertThat(result, matchesPattern("^.*java(.exe)?.*"));
		assertThat(result, containsString("-jar"));
		assertThat(result, containsString("helloworld.jar"));

		assertThat(s.getBackingFile().toString(), equalTo(jar));
		assertThat(s.forJar(), equalTo(true));

		run.doCall();
	}

	@Test
	void testHelloWorldGAV() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		Jbang jbang = new Jbang();

		String jar = "info.picocli:picocli-codegen:4.5.0";

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		Script result = prepareScript(jar, run.userParams, run.properties);

		assertThat(result.getBackingFile().toString(), matchesPattern(".*\\.m2.*codegen-4.5.0.jar"));

		String cmd = run.generateCommandLine(result);

		assertThat(cmd, containsString(Run.escapeArgument("-jar")));

	}

	@Test
	void testHelloWorldGAVWithMainClass() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		Jbang jbang = new Jbang();

		String jar = "info.picocli:picocli-codegen:4.5.0";

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--main",
				"picocli.codegen.aot.graalvm.ReflectionConfigGenerator", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		Script result = prepareScript(jar, run.userParams, run.properties);

		String cmd = run.generateCommandLine(result);

		assertThat(result.getMainClass(), equalTo("picocli.codegen.aot.graalvm.ReflectionConfigGenerator"));

		assertThat(result.getBackingFile().toString(), matchesPattern(".*\\.m2.*codegen-4.5.0.jar"));

		assertThat(cmd, matchesPattern(".* -classpath .*picocli-4.5.0.jar.*"));
		assertThat(cmd, not(containsString(" -jar ")));

	}

	@Test
	void testHelloWorldShellNoExit() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		Jbang jbang = new Jbang();
		String arg = new File(examplesTestFolder, "helloworld.jsh").getAbsolutePath();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--interactive", arg, "blah");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		String result = run.generateCommandLine(
				new Script(new File("helloworld.jsh"), "", run.userParams, run.properties));

		assertThat(result, startsWith("jshell"));
		assertThat(result, not(containsString("  ")));
		assertThat(result, containsString("helloworld.jsh"));
		assertThat(result, not(containsString("--source 11")));
		assertThat(result, containsString("--startup=DEFAULT"));
		assertThat(result, matchesPattern(".*--startup=[^ ]*helloworld.jsh.*"));
		assertThat(result, not(containsString("blah")));
		assertThat(result, not(containsString("jbang_exit_")));
	}

	@Test
	void testDebug() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		Jbang jbang = new Jbang();
		String arg = new File(examplesTestFolder, "helloworld.java").getAbsolutePath();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--debug", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		String result = run.generateCommandLine(
				new Script(new File("helloworld.java"), "", run.userParams, run.properties));

		assertThat(result, startsWith("java "));
		assertThat(result, containsString("helloworld.java"));
//		assertThat(result, containsString(" --source 11 "));
		assertThat(result, containsString("jdwp"));
		assertThat(result, not(containsString("  ")));
		assertThat(result, not(containsString("classpath")));
	}

	@Test
	void testDependencies() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		Jbang jbang = new Jbang();
		String arg = new File(examplesTestFolder, "classpath_example.java").getAbsolutePath();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		String result = run.generateCommandLine(new Script(new File(arg), run.userParams, run.properties));

		assertThat(result, startsWith("java "));
		assertThat(result, containsString("classpath_example.java"));
//		assertThat(result, containsString(" --source 11 "));
		assertThat(result, not(containsString("  ")));
		assertThat(result, containsString("classpath"));
		assertThat(result, containsString("log4j"));
	}

	@Test
	void testProperties() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		Jbang jbang = new Jbang();
		String arg = new File(examplesTestFolder, "classpath_example.java").getAbsolutePath();
		CommandLine.ParseResult pr = new CommandLine(jbang)	.setStopAtPositional(true)
															.parseArgs("run", "-Dwonka=panda", "-Dquoted=see this",
																	arg, "-Dafter=wonka");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.userParams.size(), is(1));

		assertThat(run.properties.size(), is(2));

		String result = run.generateCommandLine(new Script(new File(arg), run.userParams, run.properties));

		assertThat(result, startsWith("java "));
		assertThat(result, containsString("-Dwonka=panda"));
		if (Util.isWindows()) {
			assertThat(result, containsString("^\"-Dquoted=see^ this^\""));
		} else {
			assertThat(result, containsString("'-Dquoted=see this'"));
		}
		String[] split = result.split("example.java");
		assertEquals(split.length, 2);
		assertThat(split[0], not(containsString("after=wonka")));
		assertThat(split[1], containsString("after=wonka"));

	}

	@Test
	void testURLPrepare() throws IOException {

		String url = new File(examplesTestFolder, "classpath_example.java").toURI().toString();

		Script result = prepareScript(url, null, null);

		assertThat(result.toString(), not(containsString(url)));

		MatcherAssert.assertThat(Util.readString(result.getBackingFile().toPath()),
				containsString("Logger.getLogger(classpath_example.class);"));

		Jbang jbang = new Jbang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", url);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		String s = run.generateCommandLine(prepareScript(url, run.userParams, run.properties));

		assertThat(s, not(containsString("file:")));
	}

	@Test
	public void testMetaCharacters() throws IOException {
		String url = new File(examplesTestFolder, "classpath_example.java").toURI().toString();
		Jbang jbang = new Jbang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", url, " ~!@#$%^&*()-+\\:;'`<>?/,.{}[]\"");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		String s = run.generateCommandLine(prepareScript(url, run.userParams, run.properties));
		if (Util.isWindows()) {
			assertThat(s, containsString("^\"^ ~^!@#$^%^^^&*^(^)-+\\:;'`^<^>?/,.{}[]\\^\"^\""));
		} else {
			assertThat(s, containsString("' ~!@#$%^&*()-+\\:;'\\''`<>?/,.{}[]\"'"));
		}
	}

	@Test
	void testURLPrepareDoesNotExist() throws IOException {

		String url = new File(examplesTestFolder, "classpath_example.java.dontexist").toURI().toString();

		assertThrows(ExitException.class, () -> prepareScript(url, null, null));
	}

	@Test
	void testFindMain(@TempDir Path dir) throws IOException {

		File basedir = dir.resolve("a/b/c").toFile();
		boolean mkdirs = basedir.mkdirs();
		assert (mkdirs);
		File classfile = new File(basedir, "mymain.class");
		classfile.setLastModified(System.currentTimeMillis());
		classfile.createNewFile();
		assert (classfile.exists());

		assertEquals(Run.findMainClass(dir, classfile.toPath()), "a.b.c.mymain");

	}

	@Test
	void testCreateJar(@TempDir Path rootdir) throws IOException {

		File dir = new File(rootdir.toFile(), "content");

		File basedir = dir.toPath().resolve("a/b/c").toFile();
		boolean mkdirs = basedir.mkdirs();
		assert (mkdirs);
		File classfile = new File(basedir, "mymain.class");
		classfile.setLastModified(System.currentTimeMillis());
		classfile.createNewFile();
		assert (classfile.exists());

		File out = new File(rootdir.toFile(), "content.jar");

		Script s = new Script("", null, null);
		s.setMainClass("wonkabear");
		s.createJarFile(dir, out);

		try (JarFile jf = new JarFile(out)) {

			assertThat(Collections.list(jf.entries()), IsCollectionWithSize.hasSize(5));

			assertThat(jf.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS), equalTo("wonkabear"));

			assert (out.exists());
		}

	}

	@Test
	void testGenArgs() {

		Map<String, String> properties = new HashMap<>();

		assertThat(new Run().generateArgs(Collections.emptyList(), properties), equalTo("String[] args = {  }"));

		assertThat(new Run().generateArgs(Arrays.asList("one"), properties),
				equalTo("String[] args = { \"one\" }"));

		assertThat(new Run().generateArgs(Arrays.asList("one", "two"), properties),
				equalTo("String[] args = { \"one\", \"two\" }"));

		assertThat(new Run().generateArgs(Arrays.asList("one", "two", "three \"quotes\""), properties),
				equalTo("String[] args = { \"one\", \"two\", \"three \\\"quotes\\\"\" }"));

		properties.put("value", "this value");
		assertThat(new Run().generateArgs(Collections.emptyList(), properties),
				equalTo("String[] args = {  }\nSystem.setProperty(\"value\",\"this value\");"));

	}

	@Test
	void testDualClasses(@TempDir File output) throws IOException {

		String base = "//usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n" +
				"// //DEPS <dependency1> <dependency2>\n" +
				"\n" +
				"import static java.lang.System.*;\n" +
				"\n" +
				"class firstclass {\n" +
				"\n" +
				"}\n" +
				"\n" +
				"public class dualclass {\n" +
				"\n" +
				"    public static void main(String... args) {\n" +
				"        out.println(\"Hello \" + (args.length>0?args[0]:\"World\"));\n" +
				"    }\n" +
				"}\n";

		File f = new File(output, "dualclass.java");

		Util.writeString(f.toPath(), base);

		Run m = new Run();

		Script script = new Script(f, null, null);
		m.build(script);

		assertThat(script.getMainClass(), equalTo("dualclass"));
	}

	// started failing 403 when run in github...
	/*
	 * @Test
	 * 
	 * @DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*")
	 * 
	 * @Ignore("started failing when running locally - 403") void
	 * testFetchFromGitLab(@TempDir Path dir) throws IOException {
	 * 
	 * Path x = Util.downloadFile(
	 * "https://gitlab.com/maxandersen/jbang-gitlab/-/raw/master/helloworld.java",
	 * dir.toFile()); assertEquals(x.getFileName().toString(), "helloworld.java"); }
	 */

	@Test
	void testFetchFromGist(@TempDir Path dir) throws IOException {

		String u = Util.swizzleURL("https://gist.github.com/maxandersen/590b8a0e824faeb3ee7ddfad741ce842");

		Path x = Util.downloadFile(u,
				dir.toFile());
		assertEquals(x.getFileName().toString(), "checklabeler.java");
	}

	@Test
	void testFetchjshFromGist(@TempDir Path dir) throws IOException {

		String u = Util.swizzleURL("https://gist.github.com/maxandersen/d4fa63eb16d8fc99448d37b10c7d8980");

		Path x = Util.downloadFile(u,
				dir.toFile());
		assertEquals(x.getFileName().toString(), "hello.jsh");
	}

	@Test
	void testNotThere() throws IOException {

	}

	@Test
	void testFetchFromRedirected(@TempDir Path dir) throws IOException {
		Path x = Util.downloadFileSwizzled("https://git.io/JfQYC",
				dir.toFile());
		assertEquals(x.getFileName().toString(), "helloworld.java");

		String s = Util.readString(x);

		assertThat("should be redirect thus no html tag", s, not(containsString("html>")));

	}

	@Test
	void testFetchFromGistWithoutUsername(@TempDir Path dir) throws IOException {

		String u = Util.swizzleURL("https://gist.github.com/590b8a0e824faeb3ee7ddfad741ce842");

		Path x = Util.downloadFile(u,
				dir.toFile());
		assertEquals("checklabeler.java", x.getFileName().toString());
	}

	@Test
	void testFetchFromTwitter(@TempDir Path dir) throws IOException {

		verifyHello("https://twitter.com/maxandersen/status/1266329490927616001", dir);
	}

	/* carbon gist rate limited so it fails 
	@Test
	void testFetchFromCarbon(@TempDir Path dir) throws IOException {

		verifyHello("https://carbon.now.sh/ae51bf967c98f31a13cba976903030d5", dir);
	}
    */

	private void verifyHello(String url, Path dir) throws IOException {
		String u = Util.swizzleURL(url);

		Path x = Util.downloadFileSwizzled(u,
				dir.toFile());
		assertEquals("hello.java", x.getFileName().toString());
		String java = Util.readString(x);
		assertThat(java, startsWith("//DEPS"));
		assertThat(java, not(containsString("&gt;")));
		assertThat(java, containsString("\n"));
	}

	@Test
	void testTwitterjsh(@TempDir Path dir) throws IOException {
		String u = Util.swizzleURL("https://twitter.com/maxandersen/status/1266904846239752192");

		Path x = Util.downloadFileSwizzled(u,
				dir.toFile());
		assertEquals("1266904846239752192.jsh", x.getFileName().toString());
		String java = Util.readString(x);
		assertThat(java, startsWith("//DEPS"));
		assertThat(java, not(containsString("&gt;")));
		assertThat(java, containsString("\n"));
		assertThat(java, containsString("/exit"));

	}

	@Test
	void testSwizzle(@TempDir Path dir) throws IOException {

		assertThat(
				Util.swizzleURL("https://github.com/jbangdev/jbang/blob/master/examples/helloworld.java"),
				equalTo("https://raw.githubusercontent.com/jbangdev/jbang/master/examples/helloworld.java"));

		assertThat(
				Util.swizzleURL("https://gitlab.com/jbangdev/jbang-gitlab/-/blob/master/helloworld.java"),
				equalTo("https://gitlab.com/jbangdev/jbang-gitlab/-/raw/master/helloworld.java"));

		assertThat(
				Util.swizzleURL("https://bitbucket.org/Shoeboom/test/src/master/helloworld.java"),
				equalTo("https://bitbucket.org/Shoeboom/test/raw/master/helloworld.java"));

	}

	@Test
	void testCDSNotPresent() {
		Jbang jbang = new Jbang();
		String arg = new File(examplesTestFolder, "helloworld.java").getAbsolutePath();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assert (!run.cds().isPresent());
	}

	@Test
	void testCDSPresent() {
		Jbang jbang = new Jbang();
		String arg = new File(examplesTestFolder, "helloworld.java").getAbsolutePath();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", arg, "--cds");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assert (run.cds().isPresent());
		assert (run.cds().get().booleanValue());
	}

	@Test
	void testCDSPresentButNo() {
		Jbang jbang = new Jbang();
		String arg = new File(examplesTestFolder, "helloworld.java").getAbsolutePath();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", arg, "--no-cds");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assert (run.cds().isPresent());
		assert (!run.cds().get().booleanValue());
	}

	@Test
	void testOptionActive() {
		assert (Run.optionActive(Optional.empty(), true));
		assert (!Run.optionActive(Optional.empty(), false));

		assert (Run.optionActive(Optional.of(Boolean.TRUE), true));
		assert (Run.optionActive(Optional.of(Boolean.TRUE), false));

		assert (!Run.optionActive(Optional.of(Boolean.FALSE), true));
		assert (!Run.optionActive(Optional.of(Boolean.FALSE), false));

	}

}
