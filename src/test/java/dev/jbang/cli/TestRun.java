package dev.jbang.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.jbang.cli.BaseCommand.EXIT_EXECUTE;
import static dev.jbang.source.Project.ATTR_AGENT_CLASS;
import static dev.jbang.source.Project.ATTR_PREMAIN_CLASS;
import static dev.jbang.util.JavaUtil.defaultJdkManager;
import static dev.jbang.util.Util.writeString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

import org.hamcrest.Matchers;
import org.hamcrest.io.FileMatchers;
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
import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.net.TrustedSources;
import dev.jbang.source.BuildContext;
import dev.jbang.source.Builder;
import dev.jbang.source.CmdGenerator;
import dev.jbang.source.CmdGeneratorBuilder;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.source.ResourceNotFoundException;
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
		String arg = examplesTestFolder.resolve("helloworld.java").toAbsolutePath().toString();
		String extracp = examplesTestFolder.resolve("hellojar.jar").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--deps", "info.picocli:picocli:4.6.3",
					"--cp", extracp, arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(arg);
		BuildContext ctx = BuildContext.forProject(code);

		org.assertj.core.api.Assertions.assertThat(Files.exists(ctx.getJarFile())).isEqualTo(!first);

		String result = Project.codeBuilder(ctx).build().build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*java(.exe)? .*$");
		org.assertj.core.api.Assertions.assertThat(result).endsWith("helloworld");
		org.assertj.core.api.Assertions.assertThat(result).contains("classpath");
		org.assertj.core.api.Assertions.assertThat(result).matches(".*helloworld\\.java\\.[a-z0-9]+.helloworld\\.jar.*");
		org.assertj.core.api.Assertions.assertThat(result).contains("picocli-4.6.3.jar");
		org.assertj.core.api.Assertions.assertThat(result).contains("hellojar.jar");
		org.assertj.core.api.Assertions.assertThat(result).contains("-Dfoo=bar");
		org.assertj.core.api.Assertions.assertThat(result).contains(CommandBuffer.escapeShellArgument("-Dbar=aap noot mies", Util.getShell()));
		// Make sure the opts only appear once
		org.assertj.core.api.Assertions.assertThat(result.replaceFirst(Pattern.quote("-Dfoo=bar"), "")).doesNotContain("-Dfoo=bar");
		org.assertj.core.api.Assertions.assertThat(result.replaceFirst(Pattern.quote("-Dbar=aap noot mies"), "")).doesNotContain("-Dbar=aap noot mies");
		// Make sure the opts only appear unquoted
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain(CommandBuffer.escapeShellArgument("-Dfoo=bar -Dbar=aap noot mies", Util.getShell()));
		// assertThat(result, containsString("--source 11"));
	}

	@Test
	void testHelloWorldAlias() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		Path cat = examplesTestFolder.resolve("jbang-catalog.json").toAbsolutePath();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--catalog", cat.toString(), "helloworld");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.catalog(cat.toFile());
		String result = run.updateGeneratorForRun(pb.build("helloworld").codeBuilder().build()).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*java(.exe)? .*$");
		org.assertj.core.api.Assertions.assertThat(result).endsWith("helloworld");
		org.assertj.core.api.Assertions.assertThat(result).contains("classpath");
		org.assertj.core.api.Assertions.assertThat(result).contains(".jar");
		org.assertj.core.api.Assertions.assertThat(result).contains("-Dfoo=bar");
		org.assertj.core.api.Assertions.assertThat(result).contains(CommandBuffer.escapeShellArgument("-Dbar=aap noot mies", Util.getShell()));
		org.assertj.core.api.Assertions.assertThat(result).contains("-showversion");
		// Make sure the opts only appear once
		org.assertj.core.api.Assertions.assertThat(result.replaceFirst(Pattern.quote("-Dfoo=bar"), "")).doesNotContain("-Dfoo=bar");
		org.assertj.core.api.Assertions.assertThat(result.replaceFirst(Pattern.quote("-Dbar=aap noot mies"), "")).doesNotContain("-Dbar=aap noot mies");
		org.assertj.core.api.Assertions.assertThat(result.replaceFirst(Pattern.quote("-showversion"), "")).doesNotContain("-showversion");
		// Make sure the opts only appear unquoted
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain(CommandBuffer.escapeShellArgument("-Dfoo=bar -Dbar=aap noot mies", Util.getShell()));
		// assertThat(result, containsString("--source 11"));
	}

	@Test
	void testHelloWorldShell() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("helloworld.jsh").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(arg);
		BuildContext ctx = BuildContext.forProject(prj);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*jshell(.exe)? --execution=local -J--add-modules=ALL-SYSTEM --startup.*$");
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("  ");
		org.assertj.core.api.Assertions.assertThat(result).contains(arg);
		org.assertj.core.api.Assertions.assertThat(result.split(Pattern.quote(arg), -1).length).isEqualTo(2);
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("--source 11");
		org.assertj.core.api.Assertions.assertThat(result).contains("--startup=DEFAULT");
		org.assertj.core.api.Assertions.assertThat(result).matches(".*--startup=[^ ]*helloworld.jsh.*");
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("blah");
		org.assertj.core.api.Assertions.assertThat(result).contains("jbang_exit_");
	}

	@Test
	void testNestedDeps() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("ec.jsh").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "-s", arg, "-c", "Collector2.class");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(
				LiteralScriptResourceResolver.stringToResourceRef(null, "Collector2.class", null));
		BuildContext ctx = BuildContext.forProject(prj);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*jshell(.exe)? --execution=local -J--add-modules=ALL-SYSTEM (\\^\\\")?--class-path=.*(\\^\\\")? (\\^\\\")?-J--class-path=.*(\\^\\\")? --startup=DEFAULT (\\^\\\")?--startup.*$");
		org.assertj.core.api.Assertions.assertThat(result).contains("eclipse-collections-api");
	}

	@Test
	void testCodeWithArgs() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		Path hw = examplesTestFolder.resolve("helloworld.java");
		String hwtxt = Util.readFileContent(hw);
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "-c", hwtxt, "firstarg");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");
		Project prj = pb.build(
				LiteralScriptResourceResolver.stringToResourceRef(null, hwtxt, null));

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*java(.exe)?.*$");
		org.assertj.core.api.Assertions.assertThat(result).contains("firstarg");
	}

	@Test
	void testMarkdown() throws IOException {
		String arg = examplesTestFolder.resolve("readme.md").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(arg);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*jshell(.exe)?.+--class-path=.*figlet.*? --startup.*$");
	}

	@Test
	void testMarkdownWindows() throws IOException {
		Path readmeFileOrg = examplesTestFolder.resolve("readme.md");
		String readmeText = Util.readString(readmeFileOrg);

		Path readmeFileWin = jbangTempDir.resolve("readme.md");
		Files.write(readmeFileWin, readmeText.replace("\n", "\r\n").getBytes());

		String arg = readmeFileWin.toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(arg);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*jshell(.exe)?.+--class-path=.*figlet.*? --startup.*$");
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
		String arg = "http://localhost:" + wms.port() + "/readme.md";
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(arg);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*jshell(.exe)?.+--class-path=.*figlet.*? --startup.*$");
	}

	@Test
	void testEmptyInteractiveShell(@TempDir File dir) throws IOException {

		environmentVariables.clear("JAVA_HOME");
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "a");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		File empty = new File(dir, "empty.jsh");
		empty.createNewFile();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(empty.toString());

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*jshell(.exe)? --execution=local -J--add-modules=ALL-SYSTEM --startup.*$");
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("  ");
		org.assertj.core.api.Assertions.assertThat(result).contains("empty.jsh");
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("--source 11");
		org.assertj.core.api.Assertions.assertThat(result).contains("--startup=DEFAULT");
		org.assertj.core.api.Assertions.assertThat(result).matches(".*--startup=[^ ]*empty.jsh.*");
	}

	@Test
	void testForceShell() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("hellojsh").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--jsh", arg, "helloworld");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(arg);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*jshell(.exe)? --execution=local -J--add-modules=ALL-SYSTEM --startup.*$");
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("  ");
		org.assertj.core.api.Assertions.assertThat(result).contains("hellojsh");
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("--source 11");
		org.assertj.core.api.Assertions.assertThat(result).contains("--startup=DEFAULT");
		org.assertj.core.api.Assertions.assertThat(result).matches(".*--startup=[^ ]*hellojsh.*");
		org.assertj.core.api.Assertions.assertThat(result).contains("jbang_exit_");
	}

	@Test
	void testHelloWorldJar() throws IOException {

		environmentVariables.clear("JAVA_HOME");

		String jar = examplesTestFolder.resolve("hellojar.jar").toAbsolutePath().toString();

		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--deps", "info.picocli:picocli:4.6.3",
					"--cp", "dummy.jar", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(jar);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(code)).build().generate();
		org.assertj.core.api.Assertions.assertThat(result).matches("^.*java(.exe)?.*");
		org.assertj.core.api.Assertions.assertThat(code.getMainClass()).isNotNull();

		org.assertj.core.api.Assertions.assertThat(result).contains("picocli-4.6.3.jar");
		org.assertj.core.api.Assertions.assertThat(result).contains("dummy.jar");
		org.assertj.core.api.Assertions.assertThat(result).contains("hellojar.jar");

		org.assertj.core.api.Assertions.assertThat(code.getResourceRef().getFile().toString()).isEqualTo(jar);
		org.assertj.core.api.Assertions.assertThat(code.isJar()).isEqualTo(true);

		run.doCall();
	}

	@Test
	void testJarViaHttps(@TempDir Path tdir) throws IOException {

		String jar = "https://repo1.maven.org/maven2/io/joshworks/runnable-jar/0.2/runnable-jar-0.2.jar";

		try {
			TrustedSources.instance().add(jar, tdir.resolve("test.trust").toFile());

			environmentVariables.clear("JAVA_HOME");
			CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", jar);
			Run run = (Run) pr.subcommand().commandSpec().userObject();

			ProjectBuilder pb = run.createProjectBuilderForRun();
			Project code = pb.build(jar);

			String cmdline = run.updateGeneratorForRun(CmdGenerator.builder(code)).build().generate();

			org.assertj.core.api.Assertions.assertThat(cmdline).doesNotContain("https");

			org.assertj.core.api.Assertions.assertThat(cmdline).doesNotContain(".jar.java");

		} finally {
			TrustedSources.instance().remove(Collections.singletonList(jar), tdir.resolve("test.trust").toFile());
		}
	}

	@Test
	void testHelloWorldGAVWithNoMain() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String jar = "info.picocli:picocli-codegen:4.6.3";

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(jar);

		org.assertj.core.api.Assertions.assertThat(code.getResourceRef().getFile().toString()).matches(".*jbang_tests_maven.*codegen-4.6.3.jar");

		ExitException e = Assertions.assertThrows(ExitException.class,
				() -> run.updateGeneratorForRun(CmdGenerator.builder(code)).build().generate());

		org.assertj.core.api.Assertions.assertThat(e.getMessage()).startsWith("no main class");

	}

	@Test
	void testHelloWorldGAVInteractiveWithNoMain() throws IOException {

		environmentVariables.clear("JAVA_HOME");

		String jar = "info.picocli:picocli-codegen:4.6.3";

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "-i", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(jar);

		org.assertj.core.api.Assertions.assertThat(code.getResourceRef().getFile().toString()).matches(".*jbang_tests_maven.*codegen-4.6.3.jar");

		String result = run.updateGeneratorForRun(CmdGenerator.builder(code)).build().generate();
		org.assertj.core.api.Assertions.assertThat(result).matches("^.*jshell(.exe)?.*");
		org.assertj.core.api.Assertions.assertThat(code.getMainClass()).isNull();

		org.assertj.core.api.Assertions.assertThat(code.isJar()).isEqualTo(true);

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
		String jar = "qcli";

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(jar);

		org.assertj.core.api.Assertions.assertThat(code.getResourceRef().getFile().toString()).matches(".*.jar");

		String cmd = run.updateGeneratorForRun(CmdGenerator.builder(code)).build().generate();

		if (Util.getShell() == Util.Shell.bash) {
			org.assertj.core.api.Assertions.assertThat(cmd).matches(".*quarkus-cli-1.9.0.Final-runner.jar.*");
		} else {
			// TODO On Windows the command is using an @file, we should parse
			// the name, read the file and assert against it contents.
		}

		org.assertj.core.api.Assertions.assertThat(code.getMainClass()).isEqualTo("io.quarkus.runner.GeneratedMain");

	}

	@Test
	void testHelloWorldGAVWithAMain() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String jar = "org.eclipse.jgit:org.eclipse.jgit.pgm:5.9.0.202009080501-r";

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(jar);

		org.assertj.core.api.Assertions.assertThat(code.getResourceRef().getFile().toString()).matches(".*jbang_tests_maven.*eclipse.jgit.pgm.*.jar");

		run.updateGeneratorForRun(CmdGenerator.builder(code)).build().generate();

		org.assertj.core.api.Assertions.assertThat(code.getMainClass()).isEqualTo("org.eclipse.jgit.pgm.Main");

	}

	@Test
	void testHelloWorldGAVWithExplicitMainClass() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String jar = "info.picocli:picocli-codegen:4.6.3";

		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--main",
					"picocli.codegen.aot.graalvm.ReflectionConfigGenerator", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(jar);

		String cmd = run.updateGeneratorForRun(CmdGenerator.builder(code)).build().generate();

		org.assertj.core.api.Assertions.assertThat(code.getMainClass()).isNull();
		org.assertj.core.api.Assertions.assertThat(cmd).endsWith("picocli.codegen.aot.graalvm.ReflectionConfigGenerator");

		org.assertj.core.api.Assertions.assertThat(code.getResourceRef().getFile().toString()).matches(".*jbang_tests_maven.*codegen-4.6.3.jar");

		org.assertj.core.api.Assertions.assertThat(cmd).matches(".* -classpath .*picocli-4.6.3.jar.*");
		org.assertj.core.api.Assertions.assertThat(cmd).doesNotContain(" -jar ");
	}

	@Test
	void testHelloWorldGAVWithModule() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String jar = "info.picocli:picocli-codegen:4.6.3";

		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--module", "--main",
					"picocli.codegen.aot.graalvm.ReflectionConfigGenerator", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(jar);

		String cmd = run.updateGeneratorForRun(CmdGenerator.builder(code)).build().generate();

		org.assertj.core.api.Assertions.assertThat(code.getMainClass()).isNull();
		org.assertj.core.api.Assertions.assertThat(cmd).endsWith("info.picocli.codegen/picocli.codegen.aot.graalvm.ReflectionConfigGenerator");

		org.assertj.core.api.Assertions.assertThat(code.getResourceRef().getFile().toString()).matches(".*jbang_tests_maven.*codegen-4.6.3.jar");

		org.assertj.core.api.Assertions.assertThat(cmd).matches(".* -p .*picocli-4.6.3.jar.*");
		org.assertj.core.api.Assertions.assertThat(cmd).doesNotContain(" -jar ");
	}

	@Test
	void testAliasWithRepo(@TempDir File output) throws IOException {
		final String aliases = "{\n" +
				"  \"aliases\": {\n" +
				"    \"aliaswithrepo\": {\n" +
				"      \"script-ref\": \"dummygroup:dummyart:0.1\",\n" +
				"      \"repositories\": [ \"https://dummyrepo\" ]\n" +
				"    }\n" +
				"  }\n" +
				"}";

		environmentVariables.set("JBANG_DIR", jbangTempDir.toString());
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), aliases.getBytes());

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "aliaswithrepo");

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();

		try {
			pb.build("aliaswithrepo");
			fail("Should have thrown exception");
		} catch (ExitException ex) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			org.assertj.core.api.Assertions.assertThat(sw.toString()).contains("Could not transfer artifact dummygroup:dummyart:pom:0.1 from/to https://dummyrepo");
		}
	}

	void testGAVWithExtraDeps() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String jar = "org.eclipse.jgit:org.eclipse.jgit.pgm:5.9.0.202009080501-r";
		String extracp = examplesTestFolder.resolve("hellojar.jar").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--deps", "info.picocli:picocli:4.6.3",
					"--cp", extracp, jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(jar);

		org.assertj.core.api.Assertions.assertThat(code.getResourceRef().getFile().toString()).matches(".*\\.m2.*eclipse.jgit.pgm.*.jar");

		String result = run.updateGeneratorForRun(CmdGenerator.builder(code)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).contains("picocli-4.6.3.jar");
		org.assertj.core.api.Assertions.assertThat(result).contains("hellojar.jar");
	}

	@Test
	void testHelloWorldShellNoExit() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("helloworld.jsh").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--interactive", arg,
					"blah");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(arg);
		BuildContext ctx = BuildContext.forProject(prj);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*jshell(.exe)? .*$");
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("  ");
		org.assertj.core.api.Assertions.assertThat(result).contains("helloworld.jsh");
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("--source 11");
		org.assertj.core.api.Assertions.assertThat(result).contains("--startup=DEFAULT");
		org.assertj.core.api.Assertions.assertThat(result).matches(".*--startup=[^ ]*helloworld.jsh.*");
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("blah");
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("jbang_exit_");
	}

	@Test
	void testWithSourcesShell() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("main.jsh").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(arg);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*jshell(.exe)? --execution=local -J--add-modules=ALL-SYSTEM --startup.*$");
		org.assertj.core.api.Assertions.assertThat(result).contains("funcs.jsh");
		org.assertj.core.api.Assertions.assertThat(result).contains("main.jsh");
		org.assertj.core.api.Assertions.assertThat(result).contains("--startup=DEFAULT");
		org.assertj.core.api.Assertions.assertThat(result).contains("jbang_exit_");
	}

	@Test
	void testDebug() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("helloworld.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--debug", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");
		Project prj = pb.build(arg);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*java(.exe)? .*$");
		org.assertj.core.api.Assertions.assertThat(result).contains("helloworld.java");
		org.assertj.core.api.Assertions.assertThat(result).contains("classpath");
//		assertThat(result, containsString(" --source 11 "));
		org.assertj.core.api.Assertions.assertThat(result).contains("jdwp");
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("  ");
	}

	@Test
	void testDebugHost() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("helloworld.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--debug=server=n", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");
		Project prj = pb.build(arg);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*java(.exe)? .*$");
		org.assertj.core.api.Assertions.assertThat(result).contains("helloworld.java");
		org.assertj.core.api.Assertions.assertThat(result).contains("classpath");
		org.assertj.core.api.Assertions.assertThat(result).contains("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=4004");
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("  ");
	}

	@Test
	void testDebugHostWithCustomPort() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("helloworld.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--debug=5000", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");
		Project prj = pb.build(arg);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*java(.exe)? .*$");
		org.assertj.core.api.Assertions.assertThat(result).contains("helloworld.java");
		org.assertj.core.api.Assertions.assertThat(result).contains("classpath");
		org.assertj.core.api.Assertions.assertThat(result).contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5000");
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("  ");
	}

	@Test
	void testDependencies() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("classpath_example.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");

		Project prj = pb.build(arg);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*java(.exe)? .*$");
		org.assertj.core.api.Assertions.assertThat(result).contains("classpath_example.java");
//		assertThat(result, containsString(" --source 11 "));
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("  ");
		org.assertj.core.api.Assertions.assertThat(result).contains("classpath");
		org.assertj.core.api.Assertions.assertThat(result).contains("log4j");
	}

	@Test
	void testDependenciesInteractive() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("classpath_example.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--interactive", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(arg);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*jshell(.exe)? .*$");
		assertThat(result, (containsString("classpath_example.java")));
//		assertThat(result, containsString(" --source 11 "));
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("  ");
		org.assertj.core.api.Assertions.assertThat(result).contains("classpath");
		org.assertj.core.api.Assertions.assertThat(result).contains("log4j");
		org.assertj.core.api.Assertions.assertThat(result).doesNotEndWith(arg);
	}

	@Test
	void testProperties() throws IOException {

		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("classpath_example.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine()
			.setStopAtPositional(true)
			.parseArgs("run", "-Dwonka=panda", "-Dquoted=see this",
					arg, "-Dafter=wonka");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		org.assertj.core.api.Assertions.assertThat(run.userParams.size()).isEqualTo(1);

		org.assertj.core.api.Assertions.assertThat(run.dependencyInfoMixin.getProperties().size()).isEqualTo(2);

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");
		Project prj = pb.build(arg);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*java(.exe)? .*$");
		org.assertj.core.api.Assertions.assertThat(result).contains("-Dwonka=panda");
		if (Util.isWindows()) {
			org.assertj.core.api.Assertions.assertThat(result).contains("^\"-Dquoted=see^ this^\"");
		} else {
			org.assertj.core.api.Assertions.assertThat(result).contains("'-Dquoted=see this'");
		}
		String[] split = result.split("example.java");
		assertEquals(2, split.length);
		org.assertj.core.api.Assertions.assertThat(split[0]).doesNotContain("after=wonka");
		org.assertj.core.api.Assertions.assertThat(split[1]).contains("after=wonka");
	}

	@Test
	void testURLPrepare() throws IOException {

		String url = examplesTestFolder.resolve("classpath_example.java").toFile().toURI().toString();

		ProjectBuilder ppb = Project.builder();
		Project pre = ppb.build(url);

		org.assertj.core.api.Assertions.assertThat(Util.readString(pre.getResourceRef().getFile())).contains("Logger.getLogger(classpath_example.class);");

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", url);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");

		Project prj = pb.build(url);

		String s = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(s).doesNotContain("file:");
	}

	@Test
	public void testMetaCharacters() throws IOException {
		String url = examplesTestFolder.resolve("classpath_example.java").toFile().toURI().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", url, " ~!@#$%^&*()-+\\:;'`<>?/,.{}[]\"");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");

		Project prj = pb.build(url);

		String s = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();
		if (Util.isWindows()) {
			org.assertj.core.api.Assertions.assertThat(s).contains("^\"^ ~^!@#$^%^^^&*^(^)-+\\:;'`^<^>?/,.{}[]\\^\"^\"");
		} else {
			org.assertj.core.api.Assertions.assertThat(s).contains("' ~!@#$%^&*()-+\\:;'\\''`<>?/,.{}[]\"'");
		}
	}

	@Test
	void testDependenciesWithRanges() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("classpath_log_grab_with_ranges.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");
		Project prj = pb.build(arg);

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).contains("reload4j-1.2.18.5.jar");
	}

	@Test
	void testURLPrepareDoesNotExist() throws IOException {

		String url = examplesTestFolder.resolve("classpath_example.java.dontexist").toFile().toURI().toString();

		assertThrows(ResourceNotFoundException.class, () -> Project.builder().build(url));
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

		Source src = new JavaSource("", null);
		Project prj = Project.builder().build(src);
		prj.setMainClass("wonkabear");

		JarBuildStep.createJar(prj, dir, out);

		try (JarFile jf = new JarFile(out.toFile())) {

			org.assertj.core.api.Assertions.assertThat(Collections.list(jf.entries())).hasSize(6);

			org.assertj.core.api.Assertions.assertThat(jf.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS)).isEqualTo("wonkabear");

			assert (Files.exists(out));
		}

	}

	@Test
	void testGenArgs() {

		Map<String, String> properties = new HashMap<>();

		org.assertj.core.api.Assertions.assertThat(JshCmdGenerator.generateArgs(Collections.emptyList(), properties)).isEqualTo("String[] args = {  }");

		org.assertj.core.api.Assertions.assertThat(JshCmdGenerator.generateArgs(Collections.singletonList("one"), properties)).isEqualTo("String[] args = { \"one\" }");

		org.assertj.core.api.Assertions.assertThat(JshCmdGenerator.generateArgs(Arrays.asList("one", "two"), properties)).isEqualTo("String[] args = { \"one\", \"two\" }");

		org.assertj.core.api.Assertions.assertThat(JshCmdGenerator.generateArgs(Arrays.asList("one", "two", "three \"quotes\""), properties)).isEqualTo("String[] args = { \"one\", \"two\", \"three \\\"quotes\\\"\" }");

		properties.put("value", "this value");
		org.assertj.core.api.Assertions.assertThat(JshCmdGenerator.generateArgs(Collections.emptyList(), properties)).isEqualTo("String[] args = {  }\nSystem.setProperty(\"value\",\"this value\");");

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

		Project prj = Project.builder().build(f);
		BuildContext ctx = BuildContext.forProject(prj);

		Project.codeBuilder(ctx).build();

		org.assertj.core.api.Assertions.assertThat(prj.getMainClass()).isEqualTo("aclass");

		try (FileSystem fileSystem = FileSystems.newFileSystem(ctx.getJarFile(), (ClassLoader) null)) {
			Path fileToExtract = fileSystem.getPath("META-INF/maven/dev/jbang/tests/pom.xml");

			ByteArrayOutputStream s = new ByteArrayOutputStream();

			Files.copy(fileToExtract, s);

			String xml = s.toString("UTF-8");

			org.assertj.core.api.Assertions.assertThat(xml).doesNotContain("NOT");

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

		Project prj = Project.builder().build(f);
		prj.codeBuilder().build();

		org.assertj.core.api.Assertions.assertThat(prj.getMainClass()).isEqualTo("dualclass");

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

		String arg = f.getAbsolutePath();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", arg);

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(arg);
		prj.codeBuilder().build();

		org.assertj.core.api.Assertions.assertThat(prj.getMainClass()).isEqualTo("dualclass");

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

		Path x = Util.downloadFile(u, dir);
		assertEquals(x.getFileName().toString(), "checklabeler.java");
	}

	@Test
	void testFetchFromGistWithFilename(@TempDir Path dir) throws IOException {

		String u = Util.swizzleURL(
				"https://gist.github.com/tivrfoa/503c88fb5123b1000c37a3f2832d4773#file-file2-java");

		Path x = Util.downloadFile(u, dir);
		assertEquals(x.getFileName().toString(), "file2.java");
	}

	@Test
	void testFetchFromGistWithDashInFilename(@TempDir Path dir) throws IOException {

		String u = Util.swizzleURL(
				"https://gist.github.com/tivrfoa/503c88fb5123b1000c37a3f2832d4773#file-dash-test-java");

		Path x = Util.downloadFile(u, dir);
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

		Path x = Util.downloadFile(u, dir);
		assertEquals(x.getFileName().toString(), "hello.jsh");
	}

	@Test
	void testFetchjshFromGistWithFilename(@TempDir Path dir) throws IOException {

		String u = Util.swizzleURL(
				"https://gist.github.com/tivrfoa/503c88fb5123b1000c37a3f2832d4773#file-file3-jsh");

		Path x = Util.downloadFile(u, dir);
		assertEquals(x.getFileName().toString(), "file3.jsh");
	}

	@Test
	void testFetchjshFromGistWithDashInFilename(@TempDir Path dir) throws IOException {

		String u = Util.swizzleURL(
				"https://gist.github.com/tivrfoa/503c88fb5123b1000c37a3f2832d4773#file-java-shell-script-jsh");

		Path x = Util.downloadFile(u, dir);
		assertEquals(x.getFileName().toString(), "java-shell-script.jsh");
	}

	@Test
	void testNotThere() throws IOException {

	}

	@Test
	void testFetchFromRedirected(@TempDir Path dir) throws IOException {
		String url = "https://git.io/JLyV8";
		Path x = Util.swizzleContent(url, Util.downloadFile(url, dir));
		assertEquals(x.getFileName().toString(), "helloworld.java");

		String s = Util.readString(x);

		org.assertj.core.api.Assertions.assertThat(s).as("should be redirect thus no html tag").doesNotContain("html>");

	}

	@Test
	void testFetchFromGistWithoutUsername(@TempDir Path dir) throws IOException {

		String u = Util.swizzleURL("https://gist.github.com/590b8a0e824faeb3ee7ddfad741ce842");

		Path x = Util.downloadFile(u, dir);
		assertEquals("checklabeler.java", x.getFileName().toString());
	}

	@Test
	@Disabled("twitter stopped supporting non-javascript get")
	void testFetchFromTwitter(@TempDir Path dir) throws IOException {

		verifyHello("https://twitter.com/maxandersen/status/1266329490927616001", dir);
	}

	@Test
	@Disabled("Service doesn't seem very stable")
	void testFetchFromBluesky(@TempDir Path dir) throws IOException {

		verifyHello("https://bsky.app/profile/maxandersen.xam.dk/post/3lb2vbnfpns24", dir, false);
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
		verifyHello(url, dir, true);
	}

	private void verifyHello(String url, Path dir, boolean expectFile) throws IOException {
		String u = Util.swizzleURL(url);
		Path x = Util.swizzleContent(u, Util.downloadFile(u, dir));
		if (expectFile) {
			assertEquals("hello.java", x.getFileName().toString());
		}
		String java = Util.readString(x);
		org.assertj.core.api.Assertions.assertThat(java).contains("//DEPS");
		org.assertj.core.api.Assertions.assertThat(java).doesNotContain("&gt;");
		org.assertj.core.api.Assertions.assertThat(java).contains("\n");
	}

	@Test
	@Disabled("twitter stopped supporting non-javascript get")
	void testTwitterjsh(@TempDir Path dir) throws IOException {
		String u = Util.swizzleURL("https://twitter.com/maxandersen/status/1266904846239752192");
		Path x = Util.swizzleContent(u, Util.downloadFile(u, dir));
		assertEquals("1266904846239752192.jsh", x.getFileName().toString());
		String java = Util.readString(x);
		org.assertj.core.api.Assertions.assertThat(java).startsWith("//DEPS");
		org.assertj.core.api.Assertions.assertThat(java).doesNotContain("&gt;");
		org.assertj.core.api.Assertions.assertThat(java).contains("\n");
		org.assertj.core.api.Assertions.assertThat(java).contains("/exit");

	}

	@Test
	void testSwizzle(@TempDir Path dir) throws IOException {

		org.assertj.core.api.Assertions.assertThat(Util.swizzleURL("https://github.com/jbangdev/jbang/blob/HEAD/examples/helloworld.java")).isEqualTo("https://raw.githubusercontent.com/jbangdev/jbang/HEAD/examples/helloworld.java");

		org.assertj.core.api.Assertions.assertThat(Util.swizzleURL("https://gitlab.com/jbangdev/jbang-gitlab/-/blob/HEAD/helloworld.java")).isEqualTo("https://gitlab.com/jbangdev/jbang-gitlab/-/raw/HEAD/helloworld.java");

		org.assertj.core.api.Assertions.assertThat(Util.swizzleURL("https://bitbucket.org/Shoeboom/test/src/HEAD/helloworld.java")).isEqualTo("https://bitbucket.org/Shoeboom/test/raw/HEAD/helloworld.java");

	}

	@Test
	void testCDSNotPresent() {
		String arg = examplesTestFolder.resolve("helloworld.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assert (run.runMixin.cds == null);
	}

	@Test
	void testCDSPresentOnCli() throws IOException {
		String arg = examplesTestFolder.resolve("helloworld.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--cds", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assert (run.runMixin.cds != null);
		assert (run.runMixin.cds);
	}

	@Test
	void testCDSGeneratesJSAFile() throws Exception {
		String arg = examplesTestFolder.resolve("echo.java").toAbsolutePath().toString();

		Project prj = Project.builder().mainClass("echo").build(Paths.get(arg));
		BuildContext ctx = BuildContext.forProject(prj);
		Path jsa = ctx.getJsaFile();

		if (JavaUtil.getCurrentMajorJavaVersion() >= 13) {
			String commandLine = CmdGenerator.builder(ctx).classDataSharing(true).build().generate();
			org.assertj.core.api.Assertions.assertThat(commandLine).contains("-XX:ArchiveClassesAtExit=");
			assertThat(jsa.toFile(), not(FileMatchers.anExistingFile()));

			String out = Util.runCommand(commandLine.split(" "));
			org.assertj.core.api.Assertions.assertThat(out).isNotNull();

			commandLine = CmdGenerator.builder(ctx).classDataSharing(true).build().generate();
			org.assertj.core.api.Assertions.assertThat(commandLine).contains("-XX:SharedArchiveFile=");
			assertThat(jsa.toFile(), FileMatchers.anExistingFile());
		} else {
			CaptureResult<String> cap = captureOutput(
					() -> CmdGenerator.builder(ctx).classDataSharing(true).build().generate());
			org.assertj.core.api.Assertions.assertThat(cap.err).contains("ClassDataSharing can only be used on Java versions 13 and later");
		}
	}

	@Test
	void testCDSPresentInSource(@TempDir Path output) throws Exception {
		String source = "//CDS\nclass cds { }";
		Path p = output.resolve("cds.java");
		writeString(p, source);

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", p.toString());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");
		Project code = pb.build(p);

		if (JavaUtil.getCurrentMajorJavaVersion() >= 13) {
			String commandLine = run.updateGeneratorForRun(CmdGenerator.builder(code)).build().generate();
			org.assertj.core.api.Assertions.assertThat(commandLine).contains("-XX:ArchiveClassesAtExit=");
		} else {
			CaptureResult<String> cap = captureOutput(
					() -> run.updateGeneratorForRun(CmdGenerator.builder(code)).build().generate());
			org.assertj.core.api.Assertions.assertThat(cap.err).contains("ClassDataSharing can only be used on Java versions 13 and later");
		}
	}

	@Test
	void testNoCDSPresent() {
		String arg = examplesTestFolder.resolve("helloworld.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--no-cds", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		assert (run.runMixin.cds != null);
		assert (!run.runMixin.cds);
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
		Path p = output.resolve("Agent.java");
		writeString(p, agent);

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("build", p.toFile().getAbsolutePath());
		Build build = (Build) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = build.createProjectBuilderForBuild();
		Project prj = pb.build(p.toFile().getAbsolutePath());
		BuildContext ctx = BuildContext.forProject(prj);

		Project.codeBuilder(ctx).build();

		org.assertj.core.api.Assertions.assertThat(prj.getMainSource().isAgent()).isEqualTo(true);

		org.assertj.core.api.Assertions.assertThat(prj.getManifestAttributes().get(ATTR_AGENT_CLASS)).isEqualTo("Agent");
		org.assertj.core.api.Assertions.assertThat(prj.getManifestAttributes().get(ATTR_PREMAIN_CLASS)).isEqualTo("Agent");

		try (JarFile jf = new JarFile(ctx.getJarFile().toFile())) {
			Attributes attrs = jf.getManifest().getMainAttributes();
			org.assertj.core.api.Assertions.assertThat(attrs.getValue("Premain-class")).isEqualTo("Agent");
			org.assertj.core.api.Assertions.assertThat(attrs.getValue("Can-Retransform-Classes")).isEqualTo("true");
			org.assertj.core.api.Assertions.assertThat(attrs.getValue("Can-Redefine-Classes")).isEqualTo("false");
		}

	}

	@Test
	void testpreAgent(@TempDir Path output) throws IOException {
		Path p = output.resolve("Agent.java");
		writeString(p, preagent);

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("build", p.toFile().getAbsolutePath());
		Build build = (Build) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = build.createProjectBuilderForBuild();
		Project prj = pb.build(p.toFile().getAbsolutePath());
		prj.codeBuilder().build();

		org.assertj.core.api.Assertions.assertThat(prj.getMainSource().isAgent()).isEqualTo(true);

		org.assertj.core.api.Assertions.assertThat(prj.getManifestAttributes().get(ATTR_AGENT_CLASS)).isNull();
		org.assertj.core.api.Assertions.assertThat(prj.getManifestAttributes().get(ATTR_PREMAIN_CLASS)).isEqualTo("Agent");

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

		Path agentFile = output.toPath().resolve("agent.java");
		Util.writeString(agentFile, base.replace("dualclass", "agent"));

		Path mainFile = output.toPath().resolve("main.java");
		Util.writeString(mainFile, base.replace("dualclass", "main"));

		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run",
					"--javaagent=" + agentFile.toAbsolutePath() + "=optionA",
					"--javaagent=org.jboss.byteman:byteman:4.0.13",
					mainFile.toAbsolutePath().toString());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		org.assertj.core.api.Assertions.assertThat(run.runMixin.javaAgentSlots.containsKey(agentFile.toAbsolutePath().toString())).isEqualTo(true);
		org.assertj.core.api.Assertions.assertThat(run.runMixin.javaAgentSlots.get(agentFile.toAbsolutePath().toString())).isEqualTo("optionA");

		ProjectBuilder pb = run.createProjectBuilderForRun().mainClass("fakemain");
		Project prj = pb.build(mainFile);
		Project ass1 = pb.build(agentFile);
		BuildContext ctx = BuildContext.forProject(prj);

		org.assertj.core.api.Assertions.assertThat(ass1.getMainSource().isAgent()).isEqualTo(true);

		run.buildAgents(ctx);

		CmdGeneratorBuilder bgen = CmdGenerator.builder(prj);
		String result = run.updateGeneratorForRun(bgen).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).contains("-javaagent");
		org.assertj.core.api.Assertions.assertThat(result).contains("=optionA");
		org.assertj.core.api.Assertions.assertThat(result).contains("byteman");
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain("null");
	}

	@Test
	void testJavaAgentParsing() {
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--javaagent=xyz.jar", "wonka.java");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		org.assertj.core.api.Assertions.assertThat(run.runMixin.javaAgentSlots).containsKey("xyz.jar");
	}

	@Test
	void testJavaAgentViaGAV() {
		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run",
					"--javaagent=org.jboss.byteman:byteman:4.0.13", "wonka.java");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		org.assertj.core.api.Assertions.assertThat(run.runMixin.javaAgentSlots).containsKey("org.jboss.byteman:byteman:4.0.13");
	}

	@Test
	void testAssertions() throws IOException {
		File f = examplesTestFolder.resolve("resource.java").toFile();

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--ea", f.getAbsolutePath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");

		Project prj = pb.build(f.getAbsolutePath());

		String line = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(line).contains("-ea");
	}

	@Test
	void testSystemAssertions() throws IOException {
		File f = examplesTestFolder.resolve("resource.java").toFile();

		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--enablesystemassertions", "--main",
					"fakemain",
					f.getAbsolutePath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(f.getAbsolutePath());

		String line = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(line).contains("-esa");
	}

	@Test
	void testEnablePreviewInSource(@TempDir Path output) throws Exception {
		String source = "//PREVIEW\nclass cds { }";
		Path p = output.resolve("cds.java");
		writeString(p, source);

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", p.toString());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");
		Project code = pb.build(p);

		String commandLine = run.updateGeneratorForRun(CmdGenerator.builder(code)).build().generate();
		org.assertj.core.api.Assertions.assertThat(commandLine).contains("--enable-preview");
	}

	@Test
	void testEnablePreview() throws IOException {
		File f = examplesTestFolder.resolve("resource.java").toFile();

		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--enable-preview", "--main",
					"fakemain",
					f.getAbsolutePath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(f.getAbsolutePath());

		String line = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(line).contains("--enable-preview");
	}

	@Test
	void testEnablePreviewJsh() throws IOException {
		File f = examplesTestFolder.resolve("resource.java").toFile();

		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--enable-preview", "-i", "--main",
					"fakemain",
					f.getAbsolutePath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(f.getAbsolutePath());

		BuildContext ctx = BuildContext.forProject(prj, null);
		CmdGeneratorBuilder genb = Project.codeBuilder(ctx).build();

		String cmdline = run.updateGeneratorForRun(genb).build().generate();

		org.assertj.core.api.Assertions.assertThat(cmdline).contains("--enable-preview");
		org.assertj.core.api.Assertions.assertThat(cmdline).contains("-C--enable-preview");
		org.assertj.core.api.Assertions.assertThat(cmdline).contains("-J--enable-preview");
	}

	@Test
	void testEnablePreviewJava() throws IOException {
		File f = examplesTestFolder.resolve("resource.java").toFile();

		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--enable-preview", "--main",
					"fakemain",
					f.getAbsolutePath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(f.getAbsolutePath());

		BuildContext ctx = BuildContext.forProject(prj, null);
		CmdGeneratorBuilder genb = Project.codeBuilder(ctx).build();

		String cmdline = run.updateGeneratorForRun(genb).build().generate();

		org.assertj.core.api.Assertions.assertThat(cmdline).contains("--enable-preview");
		org.assertj.core.api.Assertions.assertThat(cmdline).doesNotContain("-C--enable-preview");
		org.assertj.core.api.Assertions.assertThat(cmdline).doesNotContain("-J--enable-preview");
	}

	@Test
	void testFilePresentB() throws IOException {
		File f = examplesTestFolder.resolve("resource.java").toFile();

		Project prj = Project.builder().build(f.getAbsolutePath());
		BuildContext ctx = BuildContext.forProject(prj);

		Project.codeBuilder(ctx).build();

		org.assertj.core.api.Assertions.assertThat(prj.getMainClass()).isEqualTo("resource");

		try (FileSystem fileSystem = FileSystems.newFileSystem(ctx.getJarFile(), (ClassLoader) null)) {

			Arrays.asList("resource.properties", "renamed.properties", "META-INF/application.properties")
				.forEach(path -> {
					try {
						Path fileToExtract = fileSystem.getPath(path);

						ByteArrayOutputStream s = new ByteArrayOutputStream();
						Files.copy(fileToExtract, s);
						String xml = s.toString("UTF-8");
						org.assertj.core.api.Assertions.assertThat(xml).doesNotContain("message=hello");
					} catch (Exception e) {
						fail(e);
					}
				});

		}

	}

	@Test
	void testFileNotPresent() throws IOException {
		File f = examplesTestFolder.resolve("brokenresource.java").toFile();

		ProjectBuilder pb = Project.builder();

		ExitException root = assertThrows(ExitException.class, () -> pb.build(f.getAbsolutePath()));
		org.assertj.core.api.Assertions.assertThat(root.toString()).contains("'resourcethatdoesnotexist.properties");
		org.assertj.core.api.Assertions.assertThat(root.toString()).contains("brokenresource.java");

	}

	@Test
	void testMultiSources() throws IOException {
		Cache.clearCache(Cache.CacheClass.jars);
		File f = examplesTestFolder.resolve("one.java").toFile();

		Project prj = Project.builder().build(f.getAbsolutePath());
		BuildContext ctx = BuildContext.forProject(prj);

		Project.codeBuilder(ctx).build();

		org.assertj.core.api.Assertions.assertThat(prj.getMainClass()).isEqualTo("one");

		try (FileSystem fileSystem = FileSystems.newFileSystem(ctx.getJarFile(), (ClassLoader) null)) {
			Arrays.asList("one.class", "Two.class", "gh_release_stats.class", "fetchlatestgraalvm.class")
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

		org.assertj.core.api.Assertions.assertThat(ctx.resolveClassPath().getArtifacts()).as("duplication of deps fixed").hasSize(7);
	}

	@Test
	void testMultiResources() throws IOException {
		Cache.clearCache(Cache.CacheClass.jars);
		File f = examplesTestFolder.resolve("resources.java").toFile();

		Project prj = Project.builder().build(f.getAbsolutePath());
		BuildContext ctx = BuildContext.forProject(prj);

		Project.codeBuilder(ctx).build();

		org.assertj.core.api.Assertions.assertThat(prj.getMainClass()).isEqualTo("resources");

		try (FileSystem fileSystem = FileSystems.newFileSystem(ctx.getJarFile(), (ClassLoader) null)) {
			Arrays.asList("resources.class", "resource.properties", "test.properties")
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

		Project prj = Project.builder().build(f.getAbsolutePath());
		BuildContext ctx = BuildContext.forProject(prj);

		Project.codeBuilder(ctx).build();

		org.assertj.core.api.Assertions.assertThat(prj.getMainClass()).isEqualTo("resourcesmnt");

		try (FileSystem fileSystem = FileSystems.newFileSystem(ctx.getJarFile(), (ClassLoader) null)) {
			Arrays.asList("resourcesmnt.class", "somedir/resource.properties", "somedir/test.properties")
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
		Project.builder().build(url).codeBuilder().build();
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
		Path p = output.resolve("script");
		writeString(p, script);

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("build", p.toFile().getAbsolutePath());
		Build build = (Build) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = build.createProjectBuilderForBuild();
		Project prj = pb.build(p.toFile().getAbsolutePath());
		prj.codeBuilder().build();

		org.assertj.core.api.Assertions.assertThat(prj.getMainClass()).isEqualTo("Two");

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
		Path p = output.resolve("script");
		writeString(p, ambigiousScript);

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("build", p.toFile().getAbsolutePath());
		Build build = (Build) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = build.createProjectBuilderForBuild();
		Project prj = pb.build(p.toFile().getAbsolutePath());
		prj.codeBuilder().build();

		org.assertj.core.api.Assertions.assertThat(prj.getMainClass()).isEqualTo("One");

	}

	@Test
	void testMultiSourcesNonPublicMakeNonAmbigious(@TempDir Path output) throws IOException {
		Path p = output.resolve("script");
		writeString(p, ambigiousScript);

		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("build", "-m", "Three",
					p.toFile().getAbsolutePath());
		Build build = (Build) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = build.createProjectBuilderForBuild();
		Project prj = pb.build(p.toFile().getAbsolutePath());
		prj.codeBuilder().build();

		org.assertj.core.api.Assertions.assertThat(prj.getMainClass()).isEqualTo("Three");

	}

	@Test
	void testAdditionalSources() throws IOException {
		String mainFile = examplesTestFolder.resolve("foo.java").toString();
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("build", "-s", incFile, mainFile);
		Build build = (Build) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = build.createProjectBuilderForBuild();
		Project prj = pb.build(mainFile);
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						org.assertj.core.api.Assertions.assertThat(optionList).contains(mainFile);
						org.assertj.core.api.Assertions.assertThat(optionList).contains(incFile);
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

		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("build", "--files", resFile.toString(), mainFile.toString());
		Build build = (Build) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = build.createProjectBuilderForBuild();
		Project prj = pb.build(mainFile.toString());
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(ctx) {
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
				return new JarBuildStep(ctx) {
					@Override
					public Project build() {
						Project project = ctx.getProject();
						org.assertj.core.api.Assertions.assertThat(project.getMainSourceSet().getResources().size()).isEqualTo(1);
						List<String> ps = project.getMainSourceSet()
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
		Project prj = Project.builder().build(url);
		BuildContext ctx = BuildContext.forProject(prj);

		Project.codeBuilder(ctx).build();

		try (FileSystem fileSystem = FileSystems.newFileSystem(ctx.getJarFile(), (ClassLoader) null)) {
			Arrays.asList("one.class", "index.html")
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
		Project.builder().build(url).codeBuilder().build();
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

		Project.builder().build(dir.toPath().toString()).codeBuilder().build();
	}

	@Test
	void testNoDefaultApp(@TempDir File dir) throws IOException {

		ExitException e = assertThrows(ExitException.class,
				() -> Project.builder().build(dir.toPath().toString()));

		org.assertj.core.api.Assertions.assertThat(e.getMessage()).contains("is a directory and no default application");

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
		Project.builder().build(url).codeBuilder().build();
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
		assertThrows(ResourceNotFoundException.class,
				() -> Project.builder().build("http://localhost:" + wms.port() + "/sub/one/"));

	}

	@Test
	void testJFRPresent() throws IOException {
		String arg = new File(examplesTestFolder.toFile(), "helloworld.java").getAbsolutePath();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--jfr", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");

		Project prj = pb.build(arg);
		String line = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(line).contains("helloworld.jfr");
		// testing it does not go to cache by accident
		org.assertj.core.api.Assertions.assertThat(line.split("helloworld.jfr")[0]).doesNotContain(Settings.getCacheDir().toString());

	}

	@Test
	void testJVMOpts() throws IOException {
		String arg = new File(examplesTestFolder.toFile(), "helloworld.java").getAbsolutePath();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--runtime-option=--show-version", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");

		Project prj = pb.build(arg);
		String line = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(line).contains(" --show-version ");
	}

	@Test
	void testJavaFXViaFileDeps() throws IOException {
		Path fileref = examplesTestFolder.resolve("SankeyPlotTestWithDeps.java").toAbsolutePath();

		// todo fix so --deps can use system properties
		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run",
					fileref.toString());

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");

		Project prj = pb.build(fileref.toString());
		String line = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(line).contains("org/openjfx".replace("/", File.separator));
	}

	@Test
	void testJavaFXViaCommandLineDeps() throws IOException {
		Path fileref = examplesTestFolder.resolve("SankeyPlotTest.java").toAbsolutePath();

		// todo fix so --deps can use system properties
		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run",
					"--deps", "org.openjfx:javafx-graphics:17:mac",
					"--deps", "org.openjfx:javafx-controls:17:mac",
					"--deps", "eu.hansolo.fx:charts:RELEASE",
					fileref.toString());

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");

		Project prj = pb.build(fileref.toString());
		String line = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(line).contains("org/openjfx".replace("/", File.separator));
	}

	@Test
	void testJavaFXViaCommandLineDepsUsingCommas() throws IOException {
		Path fileref = examplesTestFolder.resolve("SankeyPlotTest.java").toAbsolutePath();

		// todo fix so --deps can use system properties
		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run",
					"--deps",
					"org.openjfx:javafx-graphics:17:mac,org.openjfx:javafx-controls:17:mac,eu.hansolo.fx:charts:RELEASE",
					fileref.toString());

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");

		Project prj = pb.build(fileref.toString());
		String line = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(line).contains("org/openjfx".replace("/", File.separator));
	}

	@Test
	void testJavaFXMagicPropertyViaCommandline() throws IOException {
		Path fileref = examplesTestFolder.resolve("SankeyPlotTest.java").toAbsolutePath();

		// todo fix so --deps can use system properties
		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run",
					"--java", "11",
					"--deps", "org.openjfx:javafx-graphics:17:${os.detected.jfxname}",
					"--deps", "org.openjfx:javafx-controls:17:${os.detected.jfxname}",
					"--deps", "eu.hansolo.fx:charts:RELEASE",
					fileref.toString());

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		pb.mainClass("fakemain");

		Project prj = pb.build(fileref.toString());
		String line = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(line).contains(" --module-path ");

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

		String arg = f.getAbsolutePath();
		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--repos", "https://dummyrepo", "--deps",
					"dummygroup:dummyart:0.1", arg);

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(arg);

		try {
			prj.codeBuilder().build();
			fail("Should have thrown exception");
		} catch (ExitException ex) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			org.assertj.core.api.Assertions.assertThat(sw.toString()).contains("Could not transfer artifact dummygroup:dummyart:pom:0.1 from/to https://dummyrepo");
		}
	}

	@Test
	void testGAVCliReposAndDepsSingleRepo(@TempDir File output) throws IOException {
		String jar = "info.picocli:picocli-codegen:4.6.3";

		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--repos", "https://dummyrepo", "--deps",
					"dummygroup:dummyart:0.1", jar);

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();

		try {
			pb.build(jar);
			fail("Should have thrown exception");
		} catch (ExitException ex) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			org.assertj.core.api.Assertions.assertThat(sw.toString()).contains("Could not transfer artifact info.picocli:picocli-codegen:pom:4.6.3 from/to https://dummyrepo");
		}
	}

	@Test
	void testGAVCliReposAndDepsTwoRepos(@TempDir File output) throws IOException {
		String jar = "info.picocli:picocli-codegen:4.6.3";

		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--repos", "mavencentral", "--repos",
					"https://dummyrepo", "--deps",
					"dummygroup:dummyart:0.1", jar);

		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();

		try {
			pb.build(jar);
			fail("Should have thrown exception");
		} catch (ExitException ex) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			org.assertj.core.api.Assertions.assertThat(sw.toString()).contains("Could not transfer artifact dummygroup:dummyart:pom:0.1 from/to https://dummyrepo");
		}
	}

	@Test
	void testMissingSource() throws IOException {
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "-s", "missing.jsh", "-i");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		try {
			Project prj = pb.build(LiteralScriptResourceResolver.stringToResourceRef(null, "", null));
			fail("Should have thrown exception");
		} catch (ExitException ex) {
			StringWriter sw = new StringWriter();
			ex.printStackTrace(new PrintWriter(sw));
			org.assertj.core.api.Assertions.assertThat(sw.toString()).contains("Script or alias could not be found or read: 'missing.jsh'");
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

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(p.toString());

		try {
			run.updateGeneratorForRun(code.codeBuilder().build()).build().generate();
			fail("Should have thrown exception");
		} catch (ExitException e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			org.assertj.core.api.Assertions.assertThat(sw.toString()).contains("in acme");
			org.assertj.core.api.Assertions.assertThat(sw.toString()).doesNotContain("in mavencentral=");
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

		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--fresh", "--repos", "central", f.getPath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(f.getPath());
		String result = run.updateGeneratorForRun(code.codeBuilder().build()).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).contains("log4j-1.2.17.jar");
	}

	@Test
	void testForceJavaVersion() throws IOException {
		int v = defaultJdkManager().getJdk(null).majorVersion();
		String arg = examplesTestFolder.resolve("java4321.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--java", "" + v, arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(arg);
		org.assertj.core.api.Assertions.assertThat(code.getJavaVersion()).isEqualTo("" + v);
	}

	@Test
	void testBuildJbangProject() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.resolve("build.jbang").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("--preview", "run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(arg);
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) throws IOException {
						// Make sure the file "build.jbang" isn't passed to the compiler
						org.assertj.core.api.Assertions.assertThat(optionList.stream().filter(o -> o.contains("build.jbang")).count()).isEqualTo(1L);
						super.runCompiler(optionList);
					}
				};
			}
		}.setFresh(true).build();

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*java(.exe)? .*$");
		org.assertj.core.api.Assertions.assertThat(result).endsWith("quote_notags");
		org.assertj.core.api.Assertions.assertThat(result).contains("classpath");
		org.assertj.core.api.Assertions.assertThat(result).matches(".*build\\.jbang\\.[a-z0-9]+.build.jbang.jar.*");
		org.assertj.core.api.Assertions.assertThat(result).contains("picocli-4.6.3.jar");
		org.assertj.core.api.Assertions.assertThat(result).contains("-Dfoo=bar");
		org.assertj.core.api.Assertions.assertThat(result).contains(CommandBuffer.escapeShellArgument("-Dbar=aap noot mies", Util.getShell()));
		// Make sure the opts only appear once
		org.assertj.core.api.Assertions.assertThat(result.replaceFirst(Pattern.quote("-Dfoo=bar"), "")).doesNotContain("-Dfoo=bar");
		org.assertj.core.api.Assertions.assertThat(result.replaceFirst(Pattern.quote("-Dbar=aap noot mies"), "")).doesNotContain("-Dbar=aap noot mies");
		// Make sure the opts only appear unquoted
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain(CommandBuffer.escapeShellArgument("-Dfoo=bar -Dbar=aap noot mies", Util.getShell()));
		// assertThat(result, containsString("--source 11"));
	}

	@Test
	void testBuildJbangProjectFolder() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String arg = examplesTestFolder.toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("--preview", "run", arg);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build(arg);
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) throws IOException {
						// Make sure the file "build.jbang" isn't passed to the compiler
						org.assertj.core.api.Assertions.assertThat(optionList.stream().filter(o -> o.contains("build.jbang")).count()).isEqualTo(1L);
						super.runCompiler(optionList);
					}
				};
			}
		}.setFresh(true).build();

		String result = run.updateGeneratorForRun(CmdGenerator.builder(prj)).build().generate();

		org.assertj.core.api.Assertions.assertThat(result).matches("^.*java(.exe)? .*$");
		org.assertj.core.api.Assertions.assertThat(result).endsWith("quote_notags");
		org.assertj.core.api.Assertions.assertThat(result).contains("classpath");
		org.assertj.core.api.Assertions.assertThat(result).matches(".*build\\.jbang\\.[a-z0-9]+.build.jbang.jar.*");
		org.assertj.core.api.Assertions.assertThat(result).contains("picocli-4.6.3.jar");
		org.assertj.core.api.Assertions.assertThat(result).contains("-Dfoo=bar");
		org.assertj.core.api.Assertions.assertThat(result).contains(CommandBuffer.escapeShellArgument("-Dbar=aap noot mies", Util.getShell()));
		// Make sure the opts only appear once
		org.assertj.core.api.Assertions.assertThat(result.replaceFirst(Pattern.quote("-Dfoo=bar"), "")).doesNotContain("-Dfoo=bar");
		org.assertj.core.api.Assertions.assertThat(result.replaceFirst(Pattern.quote("-Dbar=aap noot mies"), "")).doesNotContain("-Dbar=aap noot mies");
		// Make sure the opts only appear unquoted
		org.assertj.core.api.Assertions.assertThat(result).doesNotContain(CommandBuffer.escapeShellArgument("-Dfoo=bar -Dbar=aap noot mies", Util.getShell()));
		// assertThat(result, containsString("--source 11"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRemoteFileArgSimple() throws Exception {

		wms.stubFor(WireMock.get(urlEqualTo("/readme.md"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withBodyFile("readme.md")
				.withBody(
						Util.readString(
								examplesTestFolder.resolve("readme.md")))));

		wms.start();
		String script = examplesTestFolder.resolve("helloworld.java").toString();
		String arg = "http://localhost:" + wms.port() + "/readme.md";
		CaptureResult<Integer> result = checkedRun(null, "run", "--verbose", script, "%" + arg);
		org.assertj.core.api.Assertions.assertThat(result.err).contains("Requesting HTTP GET " + arg);
		Path file = Util.downloadAndCacheFile(arg);
		org.assertj.core.api.Assertions.assertThat(result.err).contains(file.toString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRemoteFileArgBraced() throws Exception {

		wms.stubFor(WireMock.get(urlEqualTo("/readme.md"))
			.willReturn(aResponse()
				.withHeader("Content-Type", "text/plain")
				.withBodyFile("readme.md")
				.withBody(
						Util.readString(
								examplesTestFolder.resolve("readme.md")))));

		wms.start();
		String script = examplesTestFolder.resolve("helloworld.java").toString();
		String arg = "http://localhost:" + wms.port() + "/readme.md";
		CaptureResult<Integer> result = checkedRun(null, "run", "--verbose", script, "%{" + arg + "}");
		org.assertj.core.api.Assertions.assertThat(result.err).contains("Requesting HTTP GET " + arg);
		Path file = Util.downloadAndCacheFile(arg);
		org.assertj.core.api.Assertions.assertThat(result.err).contains(file.toString());
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRemoteFileArgComplex() throws Exception {

		wms.stubFor(
				WireMock.get(urlEqualTo("/readme1.md"))
					.willReturn(aResponse()
						.withHeader("Content-Type", "text/plain")
						.withBodyFile("readme1.md")
						.withBody(
								Util.readString(
										examplesTestFolder.resolve("readme.md")))));
		wms.stubFor(
				WireMock.get(urlEqualTo("/readme2.md"))
					.willReturn(aResponse()
						.withHeader("Content-Type", "text/plain")
						.withBodyFile("readme2.md")
						.withBody(
								Util.readString(
										examplesTestFolder.resolve("readme.md")))));

		wms.start();
		String script = examplesTestFolder.resolve("helloworld.java").toString();
		String arg1 = "http://localhost:" + wms.port() + "/readme1.md";
		String arg2 = "http://localhost:" + wms.port() + "/readme2.md";
		CaptureResult<Integer> result = checkedRun(null, "run", "--verbose", script,
				"foo%{" + arg1 + "}bar%{" + arg2 + "}baz");
		org.assertj.core.api.Assertions.assertThat(result.err).contains("Requesting HTTP GET " + arg1);
		org.assertj.core.api.Assertions.assertThat(result.err).contains("Requesting HTTP GET " + arg2);
		Path file1 = Util.downloadAndCacheFile(arg1);
		Path file2 = Util.downloadAndCacheFile(arg2);
		org.assertj.core.api.Assertions.assertThat(result.err).contains("foo" + file1 + "bar" + file2 + "baz");
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRemoteFileJavaagentComplex() throws Exception {

		wms.stubFor(
				WireMock.get(urlEqualTo("/readme.md"))
					.willReturn(aResponse()
						.withHeader("Content-Type", "text/plain")
						.withBodyFile("readme.md")
						.withBody(
								Util.readString(
										examplesTestFolder.resolve("readme.md")))));

		wms.start();
		String script = examplesTestFolder.resolve("helloworld.java").toString();
		String agent = examplesTestFolder.resolve("JULAgent.java").toString();
		String arg = "http://localhost:" + wms.port() + "/readme.md";
		CaptureResult<Integer> result = checkedRun(null, "run", "--verbose",
				"--javaagent=" + agent + "=test:%{" + arg + "}",
				script);
		org.assertj.core.api.Assertions.assertThat(result.err).contains("Requesting HTTP GET " + arg);
		Path file = Util.downloadAndCacheFile(arg);
		Project aprj = Project.builder().build(agent);
		BuildContext actx = BuildContext.forProject(aprj);
		Path jar = actx.getJarFile();
		org.assertj.core.api.Assertions.assertThat(result.err).contains("-javaagent:" + jar + "=test:" + file);
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRemoteFileArgSimpleEscaped() throws Exception {
		String script = examplesTestFolder.resolve("helloworld.java").toString();
		String arg = "http://localhost:1234/readme.md";
		CaptureResult<Integer> result = checkedRun(null, "run", "--verbose", script, "%%" + arg);
		org.assertj.core.api.Assertions.assertThat(result.err).doesNotContain("Requesting HTTP GET " + arg);
		org.assertj.core.api.Assertions.assertThat(result.err).contains("%" + arg);
		org.assertj.core.api.Assertions.assertThat(result.err).doesNotContain("%%" + arg);
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRemoteFileArgComplexEscaped() throws Exception {
		if (Util.isWindows()) {
			environmentVariables.set(Util.JBANG_RUNTIME_SHELL, "powershell");
		}
		String script = examplesTestFolder.resolve("helloworld.java").toString();
		String arg = "http://localhost:1234/readme.md";
		CaptureResult<Integer> result = checkedRun(null, "run", "--verbose", script, "foo%%{" + arg + "}bar");
		org.assertj.core.api.Assertions.assertThat(result.err).doesNotContain("Requesting HTTP GET " + arg);
		org.assertj.core.api.Assertions.assertThat(result.err).contains("foo%{" + arg + "}bar");
		org.assertj.core.api.Assertions.assertThat(result.err).doesNotContain("foo%%{" + arg + "}bar");
	}

	@Test
	@SuppressWarnings("unchecked")
	void testDepsSubstituteArg() throws Exception {
		if (Util.isWindows()) {
			environmentVariables.set(Util.JBANG_RUNTIME_SHELL, "powershell");
		}
		String script = examplesTestFolder.resolve("helloworld.java").toString();
		CaptureResult<Integer> result = checkedRun(null, "run", "--verbose", script,
				"%{deps:info.picocli:picocli:4.6.3,log4j:log4j:1.2.17}");
		org.assertj.core.api.Assertions.assertThat(result.err).contains("Resolving dependencies...");
		org.assertj.core.api.Assertions.assertThat(result.err).contains("info/picocli/picocli/4.6.3/picocli-4.6.3.jar".replace("/", File.separator));
		org.assertj.core.api.Assertions.assertThat(result.err).contains("log4j/log4j/1.2.17/log4j-1.2.17.jar".replace("/", File.separator));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testUnknownSubstituteArg() throws Exception {
		if (Util.isWindows()) {
			environmentVariables.set(Util.JBANG_RUNTIME_SHELL, "powershell");
		}
		String script = examplesTestFolder.resolve("helloworld.java").toString();
		ExitException e = Assertions.assertThrows(ExitException.class,
				() -> checkedRun(null, "run", "--verbose", script, "%{foo:bar}"));
	}

	@Test
	void testHelloWorldGAVWithModulesButNoManifest() throws IOException {
		environmentVariables.clear("JAVA_HOME");
		String jar = "org.graalvm.python:python-launcher:23.1.0";

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(jar);

		// assertThat(code.getResourceRef().getFile().toString(),
		// matchesPattern(".*jbang_tests_maven.*codegen-4.6.3.jar"));

		ExitException e = Assertions.assertThrows(ExitException.class,
				() -> run.updateGeneratorForRun(CmdGenerator.builder(code)).build().generate());

		org.assertj.core.api.Assertions.assertThat(e.getMessage()).startsWith("no main class");
	}

	@Test
	void testAliasArguments() throws IOException {
		File f = examplesTestFolder.resolve("echo.java").toFile();
		List<String> args = Arrays.asList("foo", "bar");
		Alias alias = new Alias(f.toString(), null, args, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
		CatalogUtil.addNearestAlias("echo", alias);

		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "echo", "baz");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build("echo");

		BuildContext ctx = BuildContext.forProject(prj, null);
		CmdGeneratorBuilder genb = Project.codeBuilder(ctx).build();

		String cmdline = run.updateGeneratorForRun(genb).build().generate();

		org.assertj.core.api.Assertions.assertThat(cmdline).endsWith("echo foo bar baz");
	}

	@Test
	void testCatalogAliasArguments() throws IOException {
		File f = examplesTestFolder.resolve("jbang-catalog.json").toFile();
		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("run", "--catalog", f.getAbsolutePath(), "echo", "baz");
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project prj = pb.build("echo");

		BuildContext ctx = BuildContext.forProject(prj, null);
		CmdGeneratorBuilder genb = Project.codeBuilder(ctx).build();

		String cmdline = run.updateGeneratorForRun(genb).build().generate();

		org.assertj.core.api.Assertions.assertThat(cmdline).endsWith("echo baz");
	}

	@Test
	void testNativeOptsVerbose() {
		String arg = examplesTestFolder.resolve("helloworld.java").toAbsolutePath().toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("build", "-n", "-N=--verbose", arg);
	}

	@Test
	void testReadingAddExports() throws IOException {
		String jar = "com.google.googlejavaformat:google-java-format:1.25.2";

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(jar);

		BuildContext ctx = BuildContext.forProject(code);

		String cmd = CmdGenerator.builder(ctx).build().generate();

		org.assertj.core.api.Assertions.assertThat(cmd).contains("--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED");
	}

	@Test
	@Disabled("java 8 is not installing reliably on github action")
	void testReadingNoAddExportsOnJava8() throws IOException {
		String jar = "com.google.googlejavaformat:google-java-format:1.25.2";

		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("run", "--java=8", jar);
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilderForRun();
		Project code = pb.build(jar);

		BuildContext ctx = BuildContext.forProject(code);

		String cmd = CmdGenerator.builder(ctx).build().generate();

		org.assertj.core.api.Assertions.assertThat(cmd).doesNotContain("--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED");
	}

	@Test
	void testStdinHonoursForcedSourceType() throws Exception {
		// Capture original System.in to restore it later
		InputStream originalIn = System.in;
		try {
			// Feed some trivial Java code via stdin
			System.setIn(new ByteArrayInputStream("class Test { public static void main(String...a) { } }".getBytes()));

			CaptureResult<Integer> res = checkedRun(null,
					"run", "--source-type=java", "--enable-preview", "--verbose", "-");

			// expect the JShell pipeline NOT to be used
			org.assertj.core.api.Assertions.assertThat(res.err).doesNotContain(".jsh");
		} finally {
			// Always restore original System.in
			System.setIn(originalIn);
		}
	}

	@Test
	void testForceJavaSourceTypeOnJshFile() throws Exception {
		// create a .jsh file with unique name
		Path p = cwdDir.resolve("sourceTypeTest.jsh");
		try {
			writeString(p, "class Test { public static void main(String...a) { } }");

			CaptureResult<Integer> res = checkedRun(null,
					"run", "--source-type=java", "--verbose", p.toString());

			// current (broken) behaviour emits "test.jsh.jar".
			// Once fixed, that message must disappear and the run should succeed.
			org.assertj.core.api.Assertions.assertThat(res.err).doesNotContain("sourceTypeTest.jsh.jar");
			org.assertj.core.api.Assertions.assertThat(res.result).isEqualTo(EXIT_EXECUTE);
		} finally {
			// Clean up the test file
			if (Files.exists(p)) {
				Files.delete(p);
			}
		}
	}
}
