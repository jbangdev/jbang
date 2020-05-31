package dk.xam.jbang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
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
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import org.hamcrest.collection.IsCollectionWithSize;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

public class TestMain {

	public static final String EXAMPLES_FOLDER = "examples";
	static File examplesTestFolder;

	@BeforeAll
	static void init() throws URISyntaxException {
		URL examplesUrl = TestMain.class.getClassLoader().getResource(EXAMPLES_FOLDER);
		examplesTestFolder = new File(new File(examplesUrl.toURI()).getAbsolutePath());
	}

	@Test
	void testHelloWorld() throws IOException {

		Main main = new Main();
		String arg = new File(examplesTestFolder, "helloworld.java").getAbsolutePath();
		new CommandLine(main).parseArgs(arg);

		String result = main.generateCommandLine(new Script(new File("helloworld.java"), ""));

		assertThat(result, startsWith("java"));
		assertThat(result, containsString("helloworld.java"));
		// assertThat(result, containsString("--source 11"));
	}

	@Rule
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@Test
	void testHelloWorldShell() throws IOException {

		environmentVariables.clear("JAVA_HOME");

		Main main = new Main();
		String arg = new File(examplesTestFolder, "helloworld.jsh").getAbsolutePath();
		new CommandLine(main).parseArgs(arg, "blah");

		String result = main.generateCommandLine(new Script(new File("helloworld.jsh"), ""));

		assertThat(result, startsWith("jshell"));
		assertThat(result, not(containsString("  ")));
		assertThat(result, containsString("helloworld.jsh"));
		assertThat(result, not(containsString("--source 11")));
		assertThat(result, containsString("--startup=DEFAULT --startup="));
		assertThat(result, not(containsString("blah")));
		assertThat(result, containsString("jbang_exit_"));
	}

	@Test
	void testHelloWorldShellNoExit() throws IOException {

		environmentVariables.clear("JAVA_HOME");

		Main main = new Main();
		String arg = new File(examplesTestFolder, "helloworld.jsh").getAbsolutePath();
		new CommandLine(main).parseArgs("--interactive", arg, "blah");

		String result = main.generateCommandLine(new Script(new File("helloworld.jsh"), ""));

		assertThat(result, startsWith("jshell"));
		assertThat(result, not(containsString("  ")));
		assertThat(result, containsString("helloworld.jsh"));
		assertThat(result, not(containsString("--source 11")));
		assertThat(result, containsString("--startup=DEFAULT --startup="));
		assertThat(result, not(containsString("blah")));
		assertThat(result, not(containsString("jbang_exit_")));
	}

	@Test
	void testDebug() throws IOException {

		environmentVariables.clear("JAVA_HOME");

		Main main = new Main();
		String arg = new File(examplesTestFolder, "helloworld.java").getAbsolutePath();
		new CommandLine(main).parseArgs("--debug", arg);

		String result = main.generateCommandLine(new Script(new File("helloworld.java"), ""));

		assertThat(result, startsWith("java "));
		assertThat(result, containsString("helloworld.java"));
//		assertThat(result, containsString(" --source 11 "));
		assertThat(result, containsString("jdwp"));
		assertThat(result, not(containsString("  ")));
		assertThat(result, not(containsString("classpath")));
	}

	@Test
	void testDependencies() throws IOException {

		Main main = new Main();
		String arg = new File(examplesTestFolder, "classpath_example.java").getAbsolutePath();
		new CommandLine(main).parseArgs(arg);

		String result = main.generateCommandLine(new Script(new File(arg)));

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
		Main main = new Main();
		String arg = new File(examplesTestFolder, "classpath_example.java").getAbsolutePath();
		new CommandLine(main)	.setStopAtPositional(true)
								.parseArgs("-Dwonka=panda", "-Dquoted=\"see this\"", arg, "-Dafter=wonka");

		assertThat(main.userParams.size(), is(1));

		assertThat(main.properties.size(), is(2));

		String result = main.generateCommandLine(new Script(new File(arg)));

		assertThat(result, startsWith("java "));
		assertThat(result, containsString("-Dwonka=panda"));
		assertThat(result, containsString("-Dquoted=\"see this\""));
		String[] split = result.split("example.java");
		assertEquals(split.length, 2);
		assertThat(split[0], not(containsString("after=wonka")));
		assertThat(split[1], containsString("after=wonka"));

	}

	@Test
	void testURLPrepare() throws IOException {

		String url = new File(examplesTestFolder, "classpath_example.java").toURI().toString();

		Script result = Main.prepareScript(url);

		assertThat(result.toString(), not(containsString(url)));

		assertThat(Util.readString(result.backingFile.toPath()),
				containsString("Logger.getLogger(classpath_example.class);"));

		Main main = new Main();
		new CommandLine(main).parseArgs(url);

		String s = main.generateCommandLine(Main.prepareScript(url));

		assertThat(s, not(containsString("file:")));
	}

	@Test
	void testURLPrepareDoesNotExist() throws IOException {

		String url = new File(examplesTestFolder, "classpath_example.java.dontexist").toURI().toString();

		assertThrows(ExitException.class, () -> Main.prepareScript(url));
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

		assertEquals(Main.findMainClass(dir, classfile.toPath()), "a.b.c.mymain");

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

		Main.createJarFile(dir, out, "wonkabear");

		try (JarFile jf = new JarFile(out)) {

			assertThat(Collections.list(jf.entries()), IsCollectionWithSize.hasSize(5));

			assertThat(jf.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS), equalTo("wonkabear"));

			assert (out.exists());
		}

	}

	@Test
	void testGenArgs() {

		Map<String, String> properties = new HashMap<>();

		assertThat(new Main().generateArgs(Collections.emptyList(), properties), equalTo("String[] args = {  }"));

		assertThat(new Main().generateArgs(Arrays.asList("one"), properties), equalTo("String[] args = { \"one\" }"));

		assertThat(new Main().generateArgs(Arrays.asList("one", "two"), properties),
				equalTo("String[] args = { \"one\", \"two\" }"));

		assertThat(new Main().generateArgs(Arrays.asList("one", "two", "three \"quotes\""), properties),
				equalTo("String[] args = { \"one\", \"two\", \"three \\\"quotes\\\"\" }"));

		properties.put("value", "this value");
		assertThat(new Main().generateArgs(Collections.emptyList(), properties),
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

		Main m = new Main();

		Script script = new Script(f);
		m.build(script);

		assertThat(script.getMainClass(), equalTo("dualclass"));
	}

	// started failing 403 when run in github...
	@Test
	@DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*")
	void testFetchFromGitLab(@TempDir Path dir) throws IOException {

		Path x = Util.downloadFile("https://gitlab.com/maxandersen/jbang-gitlab/-/raw/master/helloworld.java",
				dir.toFile());
		assertEquals(x.getFileName().toString(), "helloworld.java");
	}

	@Test
	void testFetchFromGist(@TempDir Path dir) throws IOException {

		String u = Main.swizzleURL("https://gist.github.com/maxandersen/590b8a0e824faeb3ee7ddfad741ce842");

		Path x = Util.downloadFile(u,
				dir.toFile());
		assertEquals(x.getFileName().toString(), "checklabeler.java");
	}

	@Test
	void testFetchFromGistWithoutUsername(@TempDir Path dir) throws IOException {

		String u = Main.swizzleURL("https://gist.github.com/590b8a0e824faeb3ee7ddfad741ce842");

		Path x = Util.downloadFile(u,
				dir.toFile());
		assertEquals("checklabeler.java", x.getFileName().toString());
	}

	@Test
	void testFetchFromTwitter(@TempDir Path dir) throws IOException {

		verifyHello("https://twitter.com/maxandersen/status/1266329490927616001", dir);
	}

	@Test
	void testFetchFromCarbon(@TempDir Path dir) throws IOException {

		verifyHello("https://carbon.now.sh/ae51bf967c98f31a13cba976903030d5", dir);
	}

	private void verifyHello(String url, Path dir) throws IOException {
		String u = Main.swizzleURL(url);

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
		String u = Main.swizzleURL("https://twitter.com/maxandersen/status/1266904846239752192");

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
				Main.swizzleURL("https://github.com/jbangdev/jbang/blob/master/examples/helloworld.java"),
				equalTo("https://raw.githubusercontent.com/jbangdev/jbang/master/examples/helloworld.java"));

		assertThat(
				Main.swizzleURL("https://gitlab.com/jbangdev/jbang-gitlab/-/blob/master/helloworld.java"),
				equalTo("https://gitlab.com/jbangdev/jbang-gitlab/-/raw/master/helloworld.java"));

		assertThat(
				Main.swizzleURL("https://bitbucket.org/Shoeboom/test/src/master/helloworld.java"),
				equalTo("https://bitbucket.org/Shoeboom/test/raw/master/helloworld.java"));

	}

}
