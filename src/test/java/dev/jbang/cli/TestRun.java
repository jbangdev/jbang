package dev.jbang.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.jbang.source.Project.ATTR_AGENT_CLASS;
import static dev.jbang.source.Project.ATTR_PREMAIN_CLASS;
import static dev.jbang.util.Util.writeString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.collection.IsCollectionWithSize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import dev.jbang.BaseTest;
import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.catalog.Catalog;
import dev.jbang.net.TrustedSources;
import dev.jbang.source.Builder;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.source.Source;
import dev.jbang.source.buildsteps.JarBuildStep;
import dev.jbang.source.generators.JshCmdGenerator;
import dev.jbang.source.resolvers.LiteralScriptResourceResolver;
import dev.jbang.source.sources.JavaSource;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

import picocli.CommandLine;

public class TestRun extends BaseTest {

	@Test
	void testHelloWorld() throws IOException {
		testHelloWorld(true);
	}

	@Test
	void testHelloWorldTwice() throws IOException {
		testHelloWorld(true);
		testHelloWorld(false);
	}

	void testHelloWorld(boolean first) throws IOException {
		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();
		String arg = examplesTestFolder.resolve("helloworld.java").toAbsolutePath().toString();
		String extracp = examplesTestFolder.resolve("hellojar.jar").toAbsolutePath().toString();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--deps", "info.picocli:picocli:4.6.3",
				"--cp", extracp, arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project code = pb.build(arg);

		assertThat(Files.exists(code.getJarFile()), equalTo(!first));

		code = code.builder().build();

		String result = code.cmdGenerator().generate();

		assertThat(result, startsWith("java "));
		assertThat(result, endsWith("helloworld"));
		assertThat(result, containsString("classpath"));
		assertThat(result, matchesRegex(".*helloworld\\.java\\.[a-z0-9]+\\.jar.*"));
		assertThat(result, containsString("picocli-4.6.3.jar"));
		assertThat(result, containsString("hellojar.jar"));
		assertThat(result, containsString("-Dfoo=bar"));
		assertThat(result, containsString(CommandBuffer.escapeShellArgument("-Dbar=aap noot mies", Util.getShell())));
		// Make sure the opts only appear once
		assertThat(result.replaceFirst(Pattern.quote("-Dfoo=bar"), ""),
				not(containsString("-Dfoo=bar")));
		assertThat(result.replaceFirst(Pattern.quote("-Dbar=aap noot mies"), ""),
				not(containsString("-Dbar=aap noot mies")));
		// Make sure the opts only appear unquoted
		assertThat(result,
				not(containsString(
						CommandBuffer.escapeShellArgument("-Dfoo=bar -Dbar=aap noot mies", Util.getShell()))));
		// assertThat(result, containsString("--source 11"));
	}

	@Test
	void testHelloWorldAlias() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		Path cat = examplesTestFolder.resolve("jbang-catalog.json").toAbsolutePath();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--catalog", cat.toString(), "helloworld");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.catalog(cat.toFile());
		Project code = pb.build("helloworld");

		code = code.builder().build();
		String result = code.cmdGenerator().generate();

		assertThat(result, startsWith("java "));
		assertThat(result, endsWith("helloworld"));
		assertThat(result, containsString("classpath"));
		assertThat(result, containsString(".jar"));
		assertThat(result, containsString("-Dfoo=bar"));
		assertThat(result, containsString(CommandBuffer.escapeShellArgument("-Dbar=aap noot mies", Util.getShell())));
		assertThat(result, containsString("-showversion"));
		// Make sure the opts only appear once
		assertThat(result.replaceFirst(Pattern.quote("-Dfoo=bar"), ""),
				not(containsString("-Dfoo=bar")));
		assertThat(result.replaceFirst(Pattern.quote("-Dbar=aap noot mies"), ""),
				not(containsString("-Dbar=aap noot mies")));
		assertThat(result.replaceFirst(Pattern.quote("-showversion"), ""),
				not(containsString("-showversion")));
		// Make sure the opts only appear unquoted
		assertThat(result,
				not(containsString(
						CommandBuffer.escapeShellArgument("-Dfoo=bar -Dbar=aap noot mies", Util.getShell()))));
		// assertThat(result, containsString("--source 11"));
	}

	@Test
	void testHelloWorldShell() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();
		String arg = examplesTestFolder.resolve("helloworld.jsh").toAbsolutePath().toString();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(arg);

		String result = prj.cmdGenerator().generate();

		assertThat(result,
				matchesPattern("^.*jshell(.exe)? --execution=local -J--add-modules=ALL-SYSTEM --startup.*$"));
		assertThat(result, not(containsString("  ")));
		assertThat(result, containsString(arg));
		assertThat(result.split(Pattern.quote(arg), -1).length, equalTo(2));
		assertThat(result, not(containsString("--source 11")));
		assertThat(result, containsString("--startup=DEFAULT"));
		assertThat(result, matchesPattern(".*--startup=[^ ]*helloworld.jsh.*"));
		assertThat(result, not(containsString("blah")));
		assertThat(result, containsString("jbang_exit_"));
	}

	@Test
	void testNestedDeps() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();
		String arg = examplesTestFolder.resolve("ec.jsh").toAbsolutePath().toString();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "-s", arg, "-c", "Collector2.class");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(
				LiteralScriptResourceResolver.stringToResourceRef(null, "Collector2.class"));

		String result = prj.cmdGenerator().generate();

		assertThat(result,
				matchesPattern(
						"^.*jshell(.exe)? --execution=local -J--add-modules=ALL-SYSTEM (\\^\\\")?--class-path=.*(\\^\\\")? (\\^\\\")?-J--class-path=.*(\\^\\\")? --startup=DEFAULT (\\^\\\")?--startup.*$"));
		assertThat(result, containsString("eclipse-collections-api"));
	}

	@Test
	void testCodeWithArgs() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		Path hw = examplesTestFolder.resolve("helloworld.java");
		String hwtxt = Util.readFileContent(hw);
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "-c", hwtxt, "firstarg");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");
		Project prj = pb.build(
				LiteralScriptResourceResolver.stringToResourceRef(null, hwtxt));

		String result = prj.cmdGenerator().generate();

		assertThat(result, matchesPattern("^.*java(.exe)?.*$"));
		assertThat(result, containsString("firstarg"));
	}

	@Test
	void testMarkdown() throws IOException {

		JBang jbang = new JBang();
		String arg = examplesTestFolder.resolve("readme.md").toAbsolutePath().toString();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(arg);

		String result = prj.cmdGenerator().generate();

		assertThat(result, matchesPattern("^.*jshell(.exe)?.+--class-path=.*figlet.*? --startup.*$"));
	}

	@Test
	void testRemoteMarkdown() throws IOException {

		wms.stubFor(WireMock.get(urlEqualTo("/readme.md"))
							.willReturn(aResponse()
													.withHeader("Content-Type", "text/plain")
													.withBodyFile("readme.md")
													.withBody(
															Util.readString(examplesTestFolder.resolve("readme.md")))));

		wms.start();
		JBang jbang = new JBang();
		String arg = "http://localhost:" + wms.port() + "/readme.md";
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(arg);

		String result = prj.cmdGenerator().generate();

		assertThat(result, matchesPattern("^.*jshell(.exe)?.+--class-path=.*figlet.*? --startup.*$"));
	}

	@Test
	void testEmptyInteractiveShell(@TempDir File dir) throws IOException {

		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "a");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		File empty = new File(dir, "empty.jsh");
		empty.createNewFile();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(empty.toString());

		String result = prj.cmdGenerator().generate();

		assertThat(result,
				matchesPattern("^.*jshell(.exe)? --execution=local -J--add-modules=ALL-SYSTEM --startup.*$"));
		assertThat(result, not(containsString("  ")));
		assertThat(result, containsString("empty.jsh"));
		assertThat(result, not(containsString("--source 11")));
		assertThat(result, containsString("--startup=DEFAULT"));
		assertThat(result, matchesPattern(".*--startup=[^ ]*empty.jsh.*"));
	}

	@Test
	void testForceShell() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();
		String arg = examplesTestFolder.resolve("hellojsh").toAbsolutePath().toString();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--jsh", arg, "helloworld");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(arg);

		String result = prj.cmdGenerator().generate();

		assertThat(result,
				matchesPattern("^.*jshell(.exe)? --execution=local -J--add-modules=ALL-SYSTEM --startup.*$"));
		assertThat(result, not(containsString("  ")));
		assertThat(result, containsString("hellojsh"));
		assertThat(result, not(containsString("--source 11")));
		assertThat(result, containsString("--startup=DEFAULT"));
		assertThat(result, matchesPattern(".*--startup=[^ ]*hellojsh.*"));
		assertThat(result, containsString("jbang_exit_"));
	}

	@Test
	void testHelloWorldJar() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();

		String jar = examplesTestFolder.resolve("hellojar.jar").toAbsolutePath().toString();

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--deps", "info.picocli:picocli:4.6.3",
				"--cp", "dummy.jar", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project code = pb.build(jar);

		String result = code.cmdGenerator().generate();
		assertThat(result, matchesPattern("^.*java(.exe)?.*"));
		assertThat(code.getMainClass(), not(nullValue()));

		assertThat(result, containsString("picocli-4.6.3.jar"));
		assertThat(result, containsString("dummy.jar"));
		assertThat(result, containsString("hellojar.jar"));

		assertThat(code.getResourceRef().getFile().toString(), equalTo(jar));
		assertThat(code.isJar(), equalTo(true));

		run.doCall();
	}

	@Test
	void testJarViaHttps(@TempDir Path tdir) throws IOException {

		String jar = "https://repo1.maven.org/maven2/io/joshworks/runnable-jar/0.2/runnable-jar-0.2.jar";

		try {
			TrustedSources.instance().add(jar, tdir.resolve("test.trust").toFile());

			environmentVariables.clear("JAVA_HOME");
			JBang jbang = new JBang();

			CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", jar);
			Run run = (Run) pr.subcommand().commandSpec().userObject();

			ProjectBuilder pb = run.createProjectBuilder();
			Project code = pb.build(jar);

			String cmdline = code.cmdGenerator().generate();

			assertThat(cmdline, not(containsString("https")));

			assertThat(cmdline, not(containsString(".jar.java")));

		} finally {
			TrustedSources.instance().remove(Collections.singletonList(jar), tdir.resolve("test.trust").toFile());
		}
	}

	@Test
	void testHelloWorldGAVWithNoMain() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();

		String jar = "info.picocli:picocli-codegen:4.6.3";

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project code = pb.build(jar);

		assertThat(code.getResourceRef().getFile().toString(),
				matchesPattern(".*jbang_tests_maven.*codegen-4.6.3.jar"));

		ExitException e = Assertions.assertThrows(ExitException.class,
				() -> code.cmdGenerator().generate());

		assertThat(e.getMessage(), startsWith("no main class"));

	}

	@Test
	void testHelloWorldGAVInteractiveWithNoMain() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();

		String jar = "info.picocli:picocli-codegen:4.6.3";

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", jar, "-i");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project code = pb.build(jar);

		assertThat(code.getResourceRef().getFile().toString(),
				matchesPattern(".*jbang_tests_maven.*codegen-4.6.3.jar"));

		String result = code.cmdGenerator().generate();
		assertThat(result, matchesPattern("^.*jshell(.exe)?.*"));
		assertThat(code.getMainClass(), nullValue());

		assertThat(code.isJar(), equalTo(true));

		run.doCall();
	}

	@Test
	void testHelloWorldGAVWithAMainViaAlias(@TempDir File jbangTempDir, @TempDir File testTempDir) throws IOException {

		final String aliases = "{\n" +
				"  \"aliases\": {\n" +
				"    \"qcli\": {\n" +
				"      \"script-ref\": \"io.quarkus:quarkus-cli:1.9.0.Final:runner\"\n" +
				"    }\n" +
				"  }\n" +
				"}";

		environmentVariables.set("JBANG_DIR", jbangTempDir.getPath());
		Files.write(jbangTempDir.toPath().resolve(Catalog.JBANG_CATALOG_JSON), aliases.getBytes());

		environmentVariables.clear("JAVA_HOME");

		JBang jbang = new JBang();

		String jar = "qcli";

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project code = pb.build(jar);

		assertThat(code.getResourceRef().getFile().toString(), matchesPattern(".*.jar"));

		String cmd = code.cmdGenerator().generate();

		if (Util.getShell() == Util.Shell.bash) {
			assertThat(cmd, matchesPattern(".*quarkus-cli-1.9.0.Final-runner.jar.*"));
		} else {
			// TODO On Windows the command is using an @file, we should parse
			// the name, read the file and assert against it contents.
		}

		assertThat(code.getMainClass(), equalTo("io.quarkus.runner.GeneratedMain"));

	}

	@Test
	void testHelloWorldGAVWithAMain() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();

		String jar = "org.eclipse.jgit:org.eclipse.jgit.pgm:5.9.0.202009080501-r";

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project code = pb.build(jar);

		assertThat(code.getResourceRef().getFile().toString(),
				matchesPattern(".*jbang_tests_maven.*eclipse.jgit.pgm.*.jar"));

		code.cmdGenerator().generate();

		assertThat(code.getMainClass(), equalTo("org.eclipse.jgit.pgm.Main"));

	}

	@Test
	void testHelloWorldGAVWithExplicitMainClass() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();

		String jar = "info.picocli:picocli-codegen:4.6.3";

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--main",
				"picocli.codegen.aot.graalvm.ReflectionConfigGenerator", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project code = pb.build(jar);

		String cmd = code.cmdGenerator().generate();

		assertThat(code.getMainClass(), equalTo("picocli.codegen.aot.graalvm.ReflectionConfigGenerator"));

		assertThat(code.getResourceRef().getFile().toString(),
				matchesPattern(".*jbang_tests_maven.*codegen-4.6.3.jar"));

		assertThat(cmd, matchesPattern(".* -classpath .*picocli-4.6.3.jar.*"));
		assertThat(cmd, not(containsString(" -jar ")));

	}

	@Test
	void testAliasWithRepo(@TempDir File output) throws IOException {
		final String aliases = "{\n" +
				"  \"aliases\": {\n" +
				"    \"aliaswithrepo\": {\n" +
				"      \"script-ref\": \"dummygroup:dummyart:0.1\",\n" +
				"      \"repositories\": [ \"http://dummyrepo\" ]\n" +
				"    }\n" +
				"  }\n" +
				"}";

		environmentVariables.set("JBANG_DIR", jbangTempDir.toString());
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), aliases.getBytes());

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "aliaswithrepo");

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();

		try {
			pb.build("aliaswithrepo");
			fail("Should have thrown exception");
		} catch (ExitException ex) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			assertThat(sw.toString(), containsString(
					"Could not transfer artifact dummygroup:dummyart:pom:0.1 from/to http://dummyrepo"));
		}
	}

	void testGAVWithExtraDeps() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();

		String jar = "org.eclipse.jgit:org.eclipse.jgit.pgm:5.9.0.202009080501-r";
		String extracp = examplesTestFolder.resolve("hellojar.jar").toAbsolutePath().toString();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--deps", "info.picocli:picocli:4.6.3",
				"--cp", extracp, jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project code = pb.build(jar);

		assertThat(code.getResourceRef().getFile().toString(), matchesPattern(".*\\.m2.*eclipse.jgit.pgm.*.jar"));

		String result = code.cmdGenerator().generate();

		assertThat(result, containsString("picocli-4.6.3.jar"));
		assertThat(result, containsString("hellojar.jar"));
	}

	@Test
	void testHelloWorldShellNoExit() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();
		String arg = examplesTestFolder.resolve("helloworld.jsh").toAbsolutePath().toString();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--interactive", arg,
				"blah");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(arg);

		String result = prj.cmdGenerator().generate();

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
	void testWithSourcesShell() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("main.jsh").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(arg);

		String result = prj.cmdGenerator().generate();

		assertThat(result,
				matchesPattern("^.*jshell(.exe)? --execution=local -J--add-modules=ALL-SYSTEM --startup.*$"));
		assertThat(result, containsString("funcs.jsh"));
		assertThat(result, containsString("main.jsh"));
		assertThat(result, containsString("--startup=DEFAULT"));
		assertThat(result, containsString("jbang_exit_"));
	}

	@Test
	void testDebug() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("helloworld.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--debug", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");
		Project prj = pb.build(arg);

		String result = prj.cmdGenerator().generate();

		assertThat(result, startsWith("java "));
		assertThat(result, containsString("helloworld.java"));
		assertThat(result, containsString("classpath"));
//		assertThat(result, containsString(" --source 11 "));
		assertThat(result, containsString("jdwp"));
		assertThat(result, not(containsString("  ")));
	}

	@Test
	void testDependencies() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();
		String arg = examplesTestFolder.resolve("classpath_example.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");

		Project prj = pb.build(arg);

		String result = prj.cmdGenerator().generate();

		assertThat(result, startsWith("java "));
		assertThat(result, containsString("classpath_example.java"));
//		assertThat(result, containsString(" --source 11 "));
		assertThat(result, not(containsString("  ")));
		assertThat(result, containsString("classpath"));
		assertThat(result, containsString("log4j"));
	}

	@Test
	void testDependenciesInteractive() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();
		String arg = examplesTestFolder.resolve("classpath_example.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--interactive", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(arg);

		String result = prj.cmdGenerator().generate();

		assertThat(result, startsWith("jshell "));
		assertThat(result, (containsString("classpath_example.java")));
//		assertThat(result, containsString(" --source 11 "));
		assertThat(result, not(containsString("  ")));
		assertThat(result, containsString("classpath"));
		assertThat(result, containsString("log4j"));
		assertThat(result, not(endsWith(arg)));
	}

	@Test
	void testProperties() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		JBang jbang = new JBang();
		String arg = examplesTestFolder.resolve("classpath_example.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = new CommandLine(jbang)	.setStopAtPositional(true)
															.parseArgs("run", "-Dwonka=panda", "-Dquoted=see this",
																	arg, "-Dafter=wonka");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.userParams.size(), is(1));

		assertThat(run.dependencyInfoMixin.getProperties().size(), is(2));

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");
		Project prj = pb.build(arg);

		String result = prj.cmdGenerator().generate();

		assertThat(result, startsWith("java "));
		assertThat(result, containsString("-Dwonka=panda"));
		if (Util.isWindows()) {
			assertThat(result, containsString("^\"-Dquoted=see^ this^\""));
		} else {
			assertThat(result, containsString("'-Dquoted=see this'"));
		}
		String[] split = result.split("example.java");
		assertEquals(2, split.length);
		assertThat(split[0], not(containsString("after=wonka")));
		assertThat(split[1], containsString("after=wonka"));
	}

	@Test
	void testURLPrepare() throws IOException {

		String url = examplesTestFolder.resolve("classpath_example.java").toFile().toURI().toString();

		ProjectBuilder ppb = ProjectBuilder.create();
		Project pre = ppb.build(url);

		MatcherAssert.assertThat(Util.readString(pre.getResourceRef().getFile()),
				containsString("Logger.getLogger(classpath_example.class);"));

		JBang jbang = new JBang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", url);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");

		Project prj = pb.build(url);

		String s = prj.cmdGenerator().generate();

		assertThat(s, not(containsString("file:")));
	}

	@Test
	public void testMetaCharacters() throws IOException {
		String url = examplesTestFolder.resolve("classpath_example.java").toFile().toURI().toString();
		JBang jbang = new JBang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", url, " ~!@#$%^&*()-+\\:;'`<>?/,.{}[]\"");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");

		Project prj = pb.build(url);

		String s = prj.cmdGenerator().generate();
		if (Util.isWindows()) {
			assertThat(s, containsString("^\"^ ~^!@#$^%^^^&*^(^)-+\\:;'`^<^>?/,.{}[]\\^\"^\""));
		} else {
			assertThat(s, containsString("' ~!@#$%^&*()-+\\:;'\\''`<>?/,.{}[]\"'"));
		}
	}

	@Test
	void testDependenciesWithRanges() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("classpath_log_grab_with_ranges.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");
		Project prj = pb.build(arg);
		String result = prj.cmdGenerator().generate();

		assertThat(result, containsString("reload4j-1.2.18.5.jar"));
	}

	@Test
	void testURLPrepareDoesNotExist() throws IOException {

		String url = examplesTestFolder.resolve("classpath_example.java.dontexist").toFile().toURI().toString();

		assertThrows(ExitException.class, () -> ProjectBuilder.create().build(url));
	}

	@Test
	void testCreateJar(@TempDir Path rootdir) throws IOException {

		Path dir = rootdir.resolve("content");

		File basedir = dir.resolve("a/b/c").toFile();
		boolean mkdirs = basedir.mkdirs();
		assert (mkdirs);
		File classfile = new File(basedir, "mymain.class");
		classfile.setLastModified(System.currentTimeMillis());
		classfile.createNewFile();
		assert (classfile.exists());

		Path out = rootdir.resolve("content.jar");

		ProjectBuilder pb = ProjectBuilder.create();
		Source src = new JavaSource("", null);
		Project prj = src.createProject();
		prj.setMainClass("wonkabear");

		JarBuildStep.createJar(prj, dir, out);

		try (JarFile jf = new JarFile(out.toFile())) {

			assertThat(Collections.list(jf.entries()), IsCollectionWithSize.hasSize(5));

			assertThat(jf.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS), equalTo("wonkabear"));

			assert (Files.exists(out));
		}

	}

	@Test
	void testGenArgs() {

		Map<String, String> properties = new HashMap<>();

		assertThat(JshCmdGenerator.generateArgs(Collections.emptyList(), properties),
				equalTo("String[] args = {  }"));

		assertThat(JshCmdGenerator.generateArgs(Collections.singletonList("one"), properties),
				equalTo("String[] args = { \"one\" }"));

		assertThat(JshCmdGenerator.generateArgs(Arrays.asList("one", "two"), properties),
				equalTo("String[] args = { \"one\", \"two\" }"));

		assertThat(JshCmdGenerator.generateArgs(Arrays.asList("one", "two", "three \"quotes\""), properties),
				equalTo("String[] args = { \"one\", \"two\", \"three \\\"quotes\\\"\" }"));

		properties.put("value", "this value");
		assertThat(JshCmdGenerator.generateArgs(Collections.emptyList(), properties),
				equalTo("String[] args = {  }\nSystem.setProperty(\"value\",\"this value\");"));

	}

	@Test
	void testBuildPom(@TempDir File output) throws IOException, ParserConfigurationException, SAXException {

		String base = "///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n" +
				"//DEPS info.picocli:picocli:4.6.3\n" +
				"//GAV dev.jbang.tests:aclass\n" +
				"\n" +
				"import static java.lang.System.*;\n" +
				"\n" +
				"public class aclass {\n" +
				"\n" +
				"    public static void main(String... args) {\n" +
				"        out.println(\"Hello \" + (args.length>0?args[0]:\"World\"));\n" +
				"    }\n" +
				"}\n";

		Path f = output.toPath().resolve("aclass.java");

		writeString(f, base);

		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(f);
		prj = prj.builder().build();

		assertThat(prj.getMainClass(), equalTo("aclass"));

		try (FileSystem fileSystem = FileSystems.newFileSystem(prj.getJarFile(), (ClassLoader) null)) {
			Path fileToExtract = fileSystem.getPath("META-INF/maven/dev/jbang/tests/pom.xml");

			ByteArrayOutputStream s = new ByteArrayOutputStream();

			Files.copy(fileToExtract, s);

			String xml = s.toString("UTF-8");

			assertThat(xml, not(containsString("NOT")));

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(new InputSource(new StringReader(xml)));

			assertThat(doc, hasXPath("/project/dependencies/dependency"));
		}

	}

	@Test
	void testDualClasses(@TempDir File output) throws IOException, ParserConfigurationException, SAXException {

		String base = "///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n" +
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

		Path f = output.toPath().resolve("dualclass.java");

		Util.writeString(f, base);

		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(f);

		prj = prj.builder().build();

		assertThat(prj.getMainClass(), equalTo("dualclass"));

	}

	@Test
	void testShExtension(@TempDir File output) throws IOException {
		String base = "///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n" +
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

		File f = new File(output, "dualclass.sh");

		Util.writeString(f.toPath(), base);

		JBang jbang = new JBang();
		String arg = f.getAbsolutePath();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", arg);

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(arg);

		prj = prj.builder().build();

		assertThat(prj.getMainClass(), equalTo("dualclass"));

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
	 * "https://gitlab.com/maxandersen/jbang-gitlab/-/raw/HEAD/helloworld.java",
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
	void testFetchFromGistWithFilename(@TempDir Path dir) throws IOException {

		String u = Util.swizzleURL(
				"https://gist.github.com/tivrfoa/503c88fb5123b1000c37a3f2832d4773#file-file2-java");

		Path x = Util.downloadFile(u, dir.toFile());
		assertEquals(x.getFileName().toString(), "file2.java");
	}

	@Test
	void testFetchFromGistWithDashInFilename(@TempDir Path dir) throws IOException {

		String u = Util.swizzleURL(
				"https://gist.github.com/tivrfoa/503c88fb5123b1000c37a3f2832d4773#file-dash-test-java");

		Path x = Util.downloadFile(u, dir.toFile());
		assertEquals(x.getFileName().toString(), "dash-test.java");
	}

	// TODO It doesn't work because it's not from a trusted source.
	// FIX: This test should be added to JBang
	// @Test
	// void testFetchFromGistReferencingAnotherURL(@TempDir Path dir) throws
	// IOException {

	// Script script = BaseScriptCommand.prepareScript(
	// "https://gist.github.com/tivrfoa/39a0dee0ef32a75a064fe9c59c2bd68a",
	// new ArrayList<>(), new HashMap<>(), new ArrayList<>(), new ArrayList<>());

	// assertEquals(script.getResolvedSourcePaths().size(), 1);
	// assertEquals(script.getResolvedSourcePaths().get(0).getFileName().toString(),
	// "t3.java");
	// }

	@Test
	void testFetchjshFromGist(@TempDir Path dir) throws IOException {

		String u = Util.swizzleURL("https://gist.github.com/maxandersen/d4fa63eb16d8fc99448d37b10c7d8980");

		Path x = Util.downloadFile(u,
				dir.toFile());
		assertEquals(x.getFileName().toString(), "hello.jsh");
	}

	@Test
	void testFetchjshFromGistWithFilename(@TempDir Path dir) throws IOException {

		String u = Util.swizzleURL(
				"https://gist.github.com/tivrfoa/503c88fb5123b1000c37a3f2832d4773#file-file3-jsh");

		Path x = Util.downloadFile(u,
				dir.toFile());
		assertEquals(x.getFileName().toString(), "file3.jsh");
	}

	@Test
	void testFetchjshFromGistWithDashInFilename(@TempDir Path dir) throws IOException {

		String u = Util.swizzleURL(
				"https://gist.github.com/tivrfoa/503c88fb5123b1000c37a3f2832d4773#file-java-shell-script-jsh");

		Path x = Util.downloadFile(u, dir.toFile());
		assertEquals(x.getFileName().toString(), "java-shell-script.jsh");
	}

	@Test
	void testNotThere() throws IOException {

	}

	@Test
	void testFetchFromRedirected(@TempDir Path dir) throws IOException {
		String url = "https://git.io/JLyV8";
		Path x = Util.swizzleContent(url, Util.downloadFile(url, dir.toFile()));
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
	@Disabled("twitter stopped supporting non-javascript get")
	void testFetchFromTwitter(@TempDir Path dir) throws IOException {

		verifyHello("https://twitter.com/maxandersen/status/1266329490927616001", dir);
	}

	@Test
	void testFetchFromMastdon(@TempDir Path dir) throws IOException {
		verifyHello("https://mastodon.social/@maxandersen/109361828562755062", dir);
		verifyHello("https://fosstodon.org/@jbangdev/109367735752497165", dir);
	}

	/*
	 * carbon gist rate limited so it fails
	 *
	 * @Test void testFetchFromCarbon(@TempDir Path dir) throws IOException {
	 *
	 * verifyHello("https://carbon.now.sh/ae51bf967c98f31a13cba976903030d5", dir); }
	 */

	private void verifyHello(String url, Path dir) throws IOException {
		String u = Util.swizzleURL(url);
		Path x = Util.swizzleContent(u, Util.downloadFile(u, dir.toFile()));
		assertEquals("hello.java", x.getFileName().toString());
		String java = Util.readString(x);
		assertThat(java, startsWith("//DEPS"));
		assertThat(java, not(containsString("&gt;")));
		assertThat(java, containsString("\n"));
	}

	@Test
	@Disabled("twitter stopped supporting non-javascript get")
	void testTwitterjsh(@TempDir Path dir) throws IOException {
		String u = Util.swizzleURL("https://twitter.com/maxandersen/status/1266904846239752192");
		Path x = Util.swizzleContent(u, Util.downloadFile(u, dir.toFile()));
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
				Util.swizzleURL("https://github.com/jbangdev/jbang/blob/HEAD/examples/helloworld.java"),
				equalTo("https://raw.githubusercontent.com/jbangdev/jbang/HEAD/examples/helloworld.java"));

		assertThat(
				Util.swizzleURL("https://gitlab.com/jbangdev/jbang-gitlab/-/blob/HEAD/helloworld.java"),
				equalTo("https://gitlab.com/jbangdev/jbang-gitlab/-/raw/HEAD/helloworld.java"));

		assertThat(
				Util.swizzleURL("https://bitbucket.org/Shoeboom/test/src/HEAD/helloworld.java"),
				equalTo("https://bitbucket.org/Shoeboom/test/raw/HEAD/helloworld.java"));

	}

	@Test
	void testCDSNotPresent() {
		JBang jbang = new JBang();
		String arg = examplesTestFolder.resolve("helloworld.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assert (run.cds == null);
	}

	@Test
	void testCDSPresentOnCli() throws IOException {
		JBang jbang = new JBang();
		String arg = examplesTestFolder.resolve("helloworld.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--cds", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");
		Project code = pb.build(Paths.get(arg));

		String commandLine = code.cmdGenerator().generate();
		assertThat(commandLine, containsString("-XX:ArchiveClassesAtExit="));

		run.doCall();

		commandLine = code.cmdGenerator().generate();
		assertThat(commandLine, containsString("-XX:SharedArchiveFile="));

		assert (run.cds != null);
		assert (run.cds);
	}

	@Test
	void testCDSPresentInSource(@TempDir Path output) throws IOException {
		String source = "//CDS\nclass cds { }";
		Path p = output.resolve("cds.java");
		writeString(p, source);

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", p.toString());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");
		Project code = pb.build(p);

		String commandLine = code.cmdGenerator().generate();
		assertThat(commandLine, containsString("-XX:ArchiveClassesAtExit="));
	}

	@Test
	void testNoCDSPresent() {
		JBang jbang = new JBang();
		String arg = examplesTestFolder.resolve("helloworld.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", arg, "--no-cds");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assert (run.cds != null);
		assert (!run.cds);
	}

	String agent = "//JAVAAGENT Can-Redefine-Classes=false Can-Retransform-Classes\n" +
			"public class Agent {\n" +
			"public static void premain(String xyz) { };\n" +
			"public static void agentmain(String xyz, java.lang.instrument.Instrumentation inst) { };" +
			"" +
			"}";

	String preagent = "//JAVAAGENT\n" +
			"public class Agent {\n" +
			"public static void premain(String xyz) { };\n" +
			"" +
			"}";

	@Test
	void testAgent(@TempDir Path output) throws IOException {

		JBang jbang = new JBang();
		Path p = output.resolve("Agent.java");
		writeString(p, agent);

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("build", p.toFile().getAbsolutePath());
		Build build = (Build) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = build.createProjectBuilder();
		Project prj = pb.build(p.toFile().getAbsolutePath());

		prj.builder().build();

		assertThat(prj.getMainSource().isAgent(), is(true));

		assertThat(prj.getManifestAttributes().get(ATTR_AGENT_CLASS), is("Agent"));
		assertThat(prj.getManifestAttributes().get(ATTR_PREMAIN_CLASS), is("Agent"));

		try (JarFile jf = new JarFile(prj.getJarFile().toFile())) {
			Attributes attrs = jf.getManifest().getMainAttributes();
			assertThat(attrs.getValue("Premain-class"), equalTo("Agent"));
			assertThat(attrs.getValue("Can-Retransform-Classes"), equalTo("true"));
			assertThat(attrs.getValue("Can-Redefine-Classes"), equalTo("false"));
		}

	}

	@Test
	void testpreAgent(@TempDir Path output) throws IOException {

		JBang jbang = new JBang();
		Path p = output.resolve("Agent.java");
		writeString(p, preagent);

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("build", p.toFile().getAbsolutePath());
		Build build = (Build) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = build.createProjectBuilder();
		Project prj = pb.build(p.toFile().getAbsolutePath());

		prj.builder().build();

		assertThat(prj.getMainSource().isAgent(), is(true));

		assertThat(prj.getManifestAttributes().get(ATTR_AGENT_CLASS), is(nullValue()));
		assertThat(prj.getManifestAttributes().get(ATTR_PREMAIN_CLASS), is("Agent"));

	}

	@Test
	void testJavaAgentWithOptionParsing(@TempDir File output) throws IOException {

		String base = "///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n" +
				"// //DEPS <dependency1> <dependency2>\n" +
				"//JAVAAGENT\n" +
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

		Path agentfile = output.toPath().resolve("agent.java");
		Util.writeString(agentfile, base.replace("dualclass", "agent"));

		Path mainfile = output.toPath().resolve("main.java");
		Util.writeString(mainfile, base.replace("dualclass", "main"));

		JBang jbang = new JBang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run",
				"--javaagent=" + agentfile.toAbsolutePath() + "=optionA",
				"--javaagent=org.jboss.byteman:byteman:4.0.13", mainfile.toAbsolutePath().toString());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.javaAgentSlots.containsKey(agentfile.toAbsolutePath().toString()), is(true));
		assertThat(run.javaAgentSlots.get(agentfile.toAbsolutePath().toString()), equalTo("optionA"));

		ProjectBuilder pb = run.createProjectBuilder();
		Project code = pb.build(mainfile);
		Project ass = pb.build(agentfile);

		assertThat(ass.getMainSource().isAgent(), is(true));

		code = code.builder().build();

		String result = code.cmdGenerator().generate();

		assertThat(result, containsString("-javaagent"));
		assertThat(result, containsString("=optionA"));
		assertThat(result, containsString("byteman"));
		assertThat(result, not(containsString("null")));
	}

	@Test
	void testJavaAgentParsing() {
		JBang jbang = new JBang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--javaagent=xyz.jar", "wonka.java");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.javaAgentSlots, hasKey("xyz.jar"));
	}

	@Test
	void testJavaAgentViaGAV() {
		JBang jbang = new JBang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run",
				"--javaagent=org.jboss.byteman:byteman:4.0.13", "wonka.java");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assertThat(run.javaAgentSlots, hasKey("org.jboss.byteman:byteman:4.0.13"));
	}

	@Test
	void testAssertions() throws IOException {
		JBang jbang = new JBang();
		File f = examplesTestFolder.resolve("resource.java").toFile();

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--ea", f.getAbsolutePath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");

		Project prj = pb.build(f.getAbsolutePath());

		String line = prj.cmdGenerator().generate();

		assertThat(line, containsString("-ea"));
	}

	@Test
	void testSystemAssertions() throws IOException {
		JBang jbang = new JBang();
		File f = examplesTestFolder.resolve("resource.java").toFile();

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--enablesystemassertions", "--main",
				"fakemain",
				f.getAbsolutePath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(f.getAbsolutePath());

		String line = prj.cmdGenerator().generate();

		assertThat(line, containsString("-esa"));
	}

	@Test
	void testFilePresentB() throws IOException {
		File f = examplesTestFolder.resolve("resource.java").toFile();

		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(f.getAbsolutePath());

		prj = prj.builder().build();

		assertThat(prj.getMainClass(), equalTo("resource"));

		try (FileSystem fileSystem = FileSystems.newFileSystem(prj.getJarFile(), (ClassLoader) null)) {

			Arrays	.asList("resource.properties", "renamed.properties", "META-INF/application.properties")
					.forEach(path -> {
						try {
							Path fileToExtract = fileSystem.getPath(path);

							ByteArrayOutputStream s = new ByteArrayOutputStream();
							Files.copy(fileToExtract, s);
							String xml = s.toString("UTF-8");
							assertThat(xml, not(containsString("message=hello")));
						} catch (Exception e) {
							fail(e);
						}
					});

		}

	}

	@Test
	void testFileNotPresent() throws IOException {
		File f = examplesTestFolder.resolve("brokenresource.java").toFile();

		ProjectBuilder pb = ProjectBuilder.create();
		ExitException root = assertThrows(ExitException.class, () -> pb.build(f.getAbsolutePath()));
		assertThat(root.getCause(), instanceOf(ResourceNotFoundException.class));
		ResourceNotFoundException rnfe = (ResourceNotFoundException) root.getCause();
		assertThat(root.toString(), containsString("'resourcethatdoesnotexist.properties"));
		assertThat(root.toString(), containsString("brokenresource.java"));

	}

	@Test
	void testMultiSources() throws IOException {
		Cache.clearCache(Cache.CacheClass.jars);
		File f = examplesTestFolder.resolve("one.java").toFile();

		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(f.getAbsolutePath());

		prj = prj.builder().build();

		assertThat(prj.getMainClass(), equalTo("one"));

		try (FileSystem fileSystem = FileSystems.newFileSystem(prj.getJarFile(), (ClassLoader) null)) {
			Arrays	.asList("one.class", "Two.class", "gh_release_stats.class", "fetchlatestgraalvm.class")
					.forEach(path -> {
						try {
							Path fileToExtract = fileSystem.getPath(path);
							ByteArrayOutputStream s = new ByteArrayOutputStream();
							Files.copy(fileToExtract, s);
							// String xml = s.toString("UTF-8");
							// assertThat(xml, );
						} catch (Exception e) {
							fail(e);
						}
					});

		}

		assertThat("duplication of deps fixed", prj.resolveClassPath().getArtifacts(), hasSize(7));
	}

	@Test
	void testMultiResources() throws IOException {
		Cache.clearCache(Cache.CacheClass.jars);
		File f = examplesTestFolder.resolve("resources.java").toFile();

		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(f.getAbsolutePath());

		prj = prj.builder().build();

		assertThat(prj.getMainClass(), equalTo("resources"));

		try (FileSystem fileSystem = FileSystems.newFileSystem(prj.getJarFile(), (ClassLoader) null)) {
			Arrays	.asList("resources.class", "resource.properties", "test.properties")
					.forEach(path -> {
						try {
							Path fileToExtract = fileSystem.getPath(path);
							ByteArrayOutputStream s = new ByteArrayOutputStream();
							Files.copy(fileToExtract, s);
						} catch (Exception e) {
							fail(e);
						}
					});

		}
	}

	@Test
	void testMultiResourcesMounted() throws IOException {
		Cache.clearCache(Cache.CacheClass.jars);
		File f = examplesTestFolder.resolve("resourcesmnt.java").toFile();

		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(f.getAbsolutePath());

		prj = prj.builder().build();

		assertThat(prj.getMainClass(), equalTo("resourcesmnt"));

		try (FileSystem fileSystem = FileSystems.newFileSystem(prj.getJarFile(), (ClassLoader) null)) {
			Arrays	.asList("resourcesmnt.class", "somedir/resource.properties", "somedir/test.properties")
					.forEach(path -> {
						try {
							Path fileToExtract = fileSystem.getPath(path);
							ByteArrayOutputStream s = new ByteArrayOutputStream();
							Files.copy(fileToExtract, s);
						} catch (Exception e) {
							fail(e);
						}
					});

		}
	}

	@Test
	void multiSourcesHttp() throws IOException {

		wms.stubFor(WireMock.get(urlEqualTo("/sub/one.java"))
							.willReturn(aResponse()
													.withHeader("Content-Type", "text/plain")
													.withBody("//SOURCES two.java\n" +
															"public class one {" +
															"public static void main(String... args) {" +
															"System.out.println(new two());" +
															"}" +
															"}")));

		wms.stubFor(WireMock.get(urlEqualTo("/sub/two.java"))
							.willReturn(aResponse()
													.withHeader("Content-Type", "text/plain")
													.withBody("//SOURCES three.java\n" +
															"public class two {" +
															" static { three.hi(); }" +
															" public String toString() { return \"two for two\"; }" +
															"}")));

		wms.stubFor(WireMock.get(urlEqualTo("/sub/three.java"))
							.willReturn(aResponse()
													.withHeader("Content-Type", "text/plain")
													.withBody("//SOURCES **/*.java\n" +
															"public class three {" +
															" public static void hi() { System.out.println(\"hi\"); }" +
															"}")));
		wms.start();

		String url = "http://localhost:" + wms.port() + "/sub/one.java";
		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(url);

		prj.builder().build();

	}

	String script = "" +
			"class One {\n" +
			"}" +
			"class Two {\n" +
			"public static void main(String... args) { };\n" +
			"}" +
			"class Three {\n" +
			"}";

	@Test
	void testMultiSourcesNonPublic(@TempDir Path output) throws IOException {
		JBang jbang = new JBang();
		Path p = output.resolve("script");
		writeString(p, script);

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("build", p.toFile().getAbsolutePath());
		Build run = (Build) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(p.toFile().getAbsolutePath());

		prj.builder().build();

		assertThat(prj.getMainClass(), equalTo("Two"));

	}

	String ambigiousScript = "" +
			"class One {\n" +
			"public static void main(String... args) { };\n" +
			"}" +
			"class Two {\n" +
			"public static void main(String... args) { };\n" +
			"}" +
			"class Three {\n" +
			"public static void main(String... args) { };\n" +
			"}";

	@Test
	void testMultiSourcesNonPublicAmbigious(@TempDir Path output) throws IOException {
		JBang jbang = new JBang();
		Path p = output.resolve("script");
		writeString(p, ambigiousScript);

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("build", p.toFile().getAbsolutePath());
		Build run = (Build) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(p.toFile().getAbsolutePath());

		prj.builder().build();

		assertThat(prj.getMainClass(), equalTo("One"));

	}

	@Test
	void testMultiSourcesNonPublicMakeNonAmbigious(@TempDir Path output) throws IOException {
		JBang jbang = new JBang();
		Path p = output.resolve("script");
		writeString(p, ambigiousScript);

		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("build", "-m", "Three",
				p.toFile().getAbsolutePath());
		Build run = (Build) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(p.toFile().getAbsolutePath());

		prj.builder().build();

		assertThat(prj.getMainClass(), equalTo("Three"));

	}

	@Test
	void testAdditionalSources() throws IOException {
		String mainFile = examplesTestFolder.resolve("foo.java").toString();
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("build", "-s", incFile, mainFile);
		Build build = (Build) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = build.createProjectBuilder();
		Project prj = pb.build(mainFile);

		new JavaSource.JavaAppBuilder(prj) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItem(mainFile));
						assertThat(optionList, hasItem(incFile));
						// Skip the compiler
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testAdditionalResources() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path mainFile = Paths.get("foo.java");
		Path resFile = Paths.get("res/resource.properties");

		CommandLine.ParseResult pr = JBang	.getCommandLine()
											.parseArgs("build", "--files", resFile.toString(), mainFile.toString());
		Build build = (Build) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = build.createProjectBuilder();
		Project prj = pb.build(mainFile.toString());

		new JavaSource.JavaAppBuilder(prj) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItem(endsWith(File.separator + "foo.java")));
						// Skip the compiler
					}
				};
			}

			@Override
			protected Builder<Project> getJarBuildStep() {
				return new JarBuildStep(project) {
					@Override
					public Project build() {
						assertThat(project.getMainSourceSet().getResources().size(), is(1));
						List<String> ps = project	.getMainSourceSet()
													.getResources()
													.stream()
													.map(r -> r.getSource().getFile().toString())
													.collect(Collectors.toList());
						assertThat(ps, hasItem(endsWith("resource.properties")));
						return project;
					}
				};
			}
		}.setFresh(true).build();
	}

	WireMockServer wms;

	@BeforeEach
	void setupMock() {
		wms = new WireMockServer(options().dynamicPort());
	}

	@AfterEach
	void tearDown() {
		wms.stop();
	}

	@Test
	void filesHttp() throws IOException {

		wms.stubFor(WireMock.get(urlEqualTo("/sub/one.java"))
							.willReturn(aResponse()
													.withHeader("Content-Type", "text/plain")
													.withBody("//FILES index.html\n" +
															"public class one {" +
															"public static void main(String... args) {" +
															"System.out.println(\"Hello\");" +
															"}" +
															"}")));

		wms.stubFor(WireMock.get(urlEqualTo("/sub/index.html"))
							.willReturn(aResponse()
													.withHeader("Content-Type", "text/plain")
													.withBody("<h1>Yay!</hi>")));

		wms.start();

		String url = "http://localhost:" + wms.port() + "/sub/one.java";
		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(url);

		prj.builder().build();

		try (FileSystem fileSystem = FileSystems.newFileSystem(prj.getJarFile(), (ClassLoader) null)) {
			Arrays	.asList("one.class", "index.html")
					.forEach(path -> {
						try {
							Path fileToExtract = fileSystem.getPath(path);
							ByteArrayOutputStream s = new ByteArrayOutputStream();
							Files.copy(fileToExtract, s);
							// String xml = s.toString("UTF-8");
							// assertThat(xml, );
						} catch (Exception e) {
							fail(e);
						}
					});

		}
	}

	@Test
	void testExtensionlessHttp() throws IOException {

		wms.stubFor(WireMock.get(urlEqualTo("/sub/one"))
							.willReturn(aResponse()
													.withHeader("Content-Type", "text/plain")
													.withBody("\n" +
															"public class one {" +
															"public static void main(String... args) {" +
															"System.out.println(new one());" +
															"}" +
															"}")));

		wms.start();

		String url = "http://localhost:" + wms.port() + "/sub/one";
		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(url);

		prj.builder().build();
	}

	@Test
	void testDefaultApp(@TempDir File dir) throws IOException {

		String base = "///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n" +
				"import static java.lang.System.*;\n" +
				"\n" +
				"public class main {\n" +
				"\n" +
				"    public static void main(String... args) {\n" +
				"        out.println(\"Default app\");\n" +
				"    }\n" +
				"}\n";

		File f = new File(dir, "main.java");

		Util.writeString(f.toPath(), base);

		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(dir.toPath().toString());

		prj.builder().build();

	}

	@Test
	void testNoDefaultApp(@TempDir File dir) throws IOException {

		ExitException e = assertThrows(ExitException.class,
				() -> ProjectBuilder.create().build(dir.toPath().toString()));

		assertThat(e.getMessage(), containsString("is a directory and no default application"));

	}

	@Test
	void testDefaultHttpApp() throws IOException {

		wms.stubFor(WireMock.get(urlEqualTo("/sub/one/main.java"))
							.willReturn(aResponse()
													.withHeader("Content-Type", "text/plain")
													.withBody("\n" +
															"public class main {" +
															"public static void main(String... args) {" +
															"System.out.println(new main());" +
															"}" +
															"}")));

		wms.start();

		String url = "http://localhost:" + wms.port() + "/sub/one/";
		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(url);

		prj.builder().build();
	}

	@Test
	void testNoDefaultHttpApp() throws IOException {

		wms.stubFor(WireMock.get(urlEqualTo("/sub/one/other.java"))
							.willReturn(aResponse()
													.withHeader("Content-Type", "text/plain")
													.withBody("\n" +
															"public class main {" +
															"public static void main(String... args) {" +
															"System.out.println(new main());" +
															"}" +
															"}")));

		wms.start();
		assertThrows(ExitException.class,
				() -> ProjectBuilder.create().build("http://localhost:" + wms.port() + "/sub/one/"));

	}

	@Test
	void testJFRPresent() throws IOException {
		String arg = new File(examplesTestFolder.toFile(), "helloworld.java").getAbsolutePath();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--jfr", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");

		String line = pb.build(arg).cmdGenerator().generate();

		assertThat(line, containsString("helloworld.jfr"));
		// testing it does not go to cache by accident
		assertThat(line.split("helloworld.jfr")[0], not(containsString(Settings.getCacheDir().toString())));

	}

	@Test
	void testJVMOpts() throws IOException {
		JBang jbang = new JBang();
		String arg = new File(examplesTestFolder.toFile(), "helloworld.java").getAbsolutePath();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--runtime-option=--show-version", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");

		String line = pb.build(arg).cmdGenerator().generate();

		assertThat(line, containsString(" --show-version "));
	}

	@Test
	void testJavaFXViaFileDeps() throws IOException {
		JBang jbang = new JBang();

		Path fileref = examplesTestFolder.resolve("SankeyPlotTestWithDeps.java").toAbsolutePath();

		// todo fix so --deps can use system properties
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run",
				fileref.toString());

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");
		String line = pb.build(fileref.toString()).cmdGenerator().generate();

		assertThat(line, containsString("org/openjfx".replace("/", File.separator)));
	}

	@Test
	void testJavaFXViaCommandLineDeps() throws IOException {
		JBang jbang = new JBang();

		Path fileref = examplesTestFolder.resolve("SankeyPlotTest.java").toAbsolutePath();

		// todo fix so --deps can use system properties
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run",
				"--deps", "org.openjfx:javafx-graphics:17:mac",
				"--deps", "org.openjfx:javafx-controls:17:mac",
				"--deps", "eu.hansolo.fx:charts:RELEASE",
				fileref.toString());

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");
		String line = pb.build(fileref.toString()).cmdGenerator().generate();

		assertThat(line, containsString("org/openjfx".replace("/", File.separator)));
	}

	@Test
	void testJavaFXViaCommandLineDepsUsingCommas() throws IOException {
		JBang jbang = new JBang();

		Path fileref = examplesTestFolder.resolve("SankeyPlotTest.java").toAbsolutePath();

		// todo fix so --deps can use system properties
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run",
				"--deps",
				"org.openjfx:javafx-graphics:17:mac,org.openjfx:javafx-controls:17:mac,eu.hansolo.fx:charts:RELEASE",
				fileref.toString());

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");
		String line = pb.build(fileref.toString()).cmdGenerator().generate();

		assertThat(line, containsString("org/openjfx".replace("/", File.separator)));
	}

	@Test
	void testJavaFXMagicPropertyViaCommandline() throws IOException {

		JBang jbang = new JBang();

		Path fileref = examplesTestFolder.resolve("SankeyPlotTest.java").toAbsolutePath();

		// todo fix so --deps can use system properties
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run",
				"--java", "11",
				"--deps", "org.openjfx:javafx-graphics:17:${os.detected.jfxname}",
				"--deps", "org.openjfx:javafx-controls:17:${os.detected.jfxname}",
				"--deps", "eu.hansolo.fx:charts:RELEASE",
				fileref.toString());

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		pb.mainClass("fakemain");
		String line = pb.build(fileref.toString()).cmdGenerator().generate();

		assertThat(line, containsString(" --module-path "));

	}

	@Test
	void testScriptCliReposAndDeps(@TempDir File output) throws IOException {
		String base = "" +
				"public class test {\n" +
				"    public static void main(String... args) {\n" +
				"        System.out.println(\"Hello World\"));\n" +
				"    }\n" +
				"}\n";

		File f = new File(output, "test.java");

		Util.writeString(f.toPath(), base);

		JBang jbang = new JBang();
		String arg = f.getAbsolutePath();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--repos", "http://dummyrepo", "--deps",
				"dummygroup:dummyart:0.1", arg);

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(arg);

		try {
			prj.builder().build();
			fail("Should have thrown exception");
		} catch (ExitException ex) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			assertThat(sw.toString(), containsString(
					"Could not transfer artifact dummygroup:dummyart:pom:0.1 from/to http://dummyrepo"));
		}
	}

	@Test
	void testGAVCliReposAndDepsSingleRepo(@TempDir File output) throws IOException {
		String jar = "info.picocli:picocli-codegen:4.6.3";

		JBang jbang = new JBang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--repos", "http://dummyrepo", "--deps",
				"dummygroup:dummyart:0.1", jar);

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();

		try {
			pb.build(jar);
			fail("Should have thrown exception");
		} catch (ExitException ex) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			assertThat(sw.toString(), containsString(
					"Could not transfer artifact info.picocli:picocli-codegen:pom:4.6.3 from/to http://dummyrepo"));
		}
	}

	@Test
	void testGAVCliReposAndDepsTwoRepos(@TempDir File output) throws IOException {
		String jar = "info.picocli:picocli-codegen:4.6.3";

		JBang jbang = new JBang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--repos", "mavencentral", "--repos",
				"http://dummyrepo", "--deps",
				"dummygroup:dummyart:0.1", jar);

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();

		try {
			pb.build(jar);
			fail("Should have thrown exception");
		} catch (ExitException ex) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			assertThat(sw.toString(), containsString(
					"Could not transfer artifact dummygroup:dummyart:pom:0.1 from/to http://dummyrepo"));
		}
	}

	@Test
	void testMissingSource() throws IOException {
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "-s", "missing.jsh", "-i");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		try {
			Project prj = pb.build(LiteralScriptResourceResolver.stringToResourceRef(null, ""));
			fail("Should have thrown exception");
		} catch (ExitException ex) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			assertThat(sw.toString(), containsString(
					"Script or alias could not be found or read: 'missing.jsh'"));
		}
	}

	@Test
	void testBuildTwiceWithCliDeps(@TempDir Path output) throws IOException {
		String script = "" +
				"//REPOS acme=https://repo1.maven.org/maven2/\n" +
				"public class script { public static void main(String... args) {} }";
		JBang jbang = new JBang();
		Path p = output.resolve("script.java");
		writeString(p, script);

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("build", p.toFile().getAbsolutePath());
		Build build = (Build) pr.subcommand().commandSpec().userObject();
		build.call();

		pr = JBang.getCommandLine().parseArgs("run", "--deps", "org.example:dummy:1", p.toFile().getAbsolutePath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project code = pb.build(p.toString());

		try {
			code = code.builder().build();
			code.cmdGenerator().generate();
			fail("Should have thrown exception");
		} catch (ExitException e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			assertThat(sw.toString(), containsString("in acme"));
			assertThat(sw.toString(), not(containsString("in mavencentral=")));
		}
	}

	@Test
	void testBuildMissingScript() {
		assertThrows(IllegalArgumentException.class, () -> {
			CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("build");
			Build build = (Build) pr.subcommand().commandSpec().userObject();
			build.doCall();
		});
	}

	@Test
	void testRunMissingScript() {
		assertThrows(IllegalArgumentException.class, () -> {
			CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run");
			Run run = (Run) pr.subcommand().commandSpec().userObject();
			run.doCall();
		});
	}

	@Test
	void testReposWorksWithFresh() throws IOException {
		File f = Util.getCwd().resolve("classpath_example.java").toFile();

		String content = ""
				+ "///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n"
				+ "\n"
				+ "//DEPS log4j:log4j:1.2.17\n"
				+ "\n"
				+ "import org.apache.log4j.Logger;\n"
				+ "import org.apache.log4j.BasicConfigurator;\n"
				+ "\n"
				+ "import java.util.Arrays;\n"
				+ "\n"
				+ "class classpath_example {\n"
				+ "\n"
				+ "	static final Logger logger = Logger.getLogger(classpath_example.class);\n"
				+ "\n"
				+ "	public static void main(String[] args) {\n"
				+ "		BasicConfigurator.configure();\n"
				+ "		logger.info(\"Welcome to jbang\");\n"
				+ "	}\n"
				+ "}";
		Util.writeString(f.toPath(), content);

		CommandLine.ParseResult pr = JBang	.getCommandLine()
											.parseArgs("run", "--fresh", "--repos", "mavencentral", f.getPath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project code = pb.build(f.getPath());
		code = code.builder().build();

		String result = code.cmdGenerator().generate();

		assertThat(result, containsString("log4j-1.2.17.jar"));
	}

	@Test
	void testForceJavaVersion() throws IOException {
		int v = JavaUtil.determineJavaVersion();
		String arg = examplesTestFolder.resolve("java4321.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--java", "" + v, arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project code = pb.build(arg);
		assertThat(code.getJavaVersion(), equalTo("" + v));
	}

	@Test
	void testBuildJbangProject() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("build.jbang").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("--preview", "run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(arg);

		new JavaSource.JavaAppBuilder(prj) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) throws IOException {
						// Make sure the file "build.jbang" isn't passed to the compiler
						assertThat(optionList.stream().filter(o -> o.contains("build.jbang")).count(), equalTo(1L));
						super.runCompiler(optionList);
					}
				};
			}
		}.setFresh(true).build();

		String result = prj.cmdGenerator().generate();

		assertThat(result, startsWith("java "));
		assertThat(result, endsWith("quote_notags"));
		assertThat(result, containsString("classpath"));
		assertThat(result, matchesRegex(".*build\\.jbang\\.[a-z0-9]+\\.jar.*"));
		assertThat(result, containsString("picocli-4.6.3.jar"));
		assertThat(result, containsString("-Dfoo=bar"));
		assertThat(result, containsString(CommandBuffer.escapeShellArgument("-Dbar=aap noot mies", Util.getShell())));
		// Make sure the opts only appear once
		assertThat(result.replaceFirst(Pattern.quote("-Dfoo=bar"), ""),
				not(containsString("-Dfoo=bar")));
		assertThat(result.replaceFirst(Pattern.quote("-Dbar=aap noot mies"), ""),
				not(containsString("-Dbar=aap noot mies")));
		// Make sure the opts only appear unquoted
		assertThat(result,
				not(containsString(
						CommandBuffer.escapeShellArgument("-Dfoo=bar -Dbar=aap noot mies", Util.getShell()))));
		// assertThat(result, containsString("--source 11"));
	}
}
