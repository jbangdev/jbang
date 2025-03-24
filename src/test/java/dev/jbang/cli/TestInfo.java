package dev.jbang.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.nio.file.Paths;
import java.util.Collection;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

import picocli.CommandLine;

public class TestInfo extends BaseTest {

	@Test
	void testInfoToolsSimple() {
		String src = examplesTestFolder.resolve("quote.java").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "tools", src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo(false);
		assertThat(info.originalResource).isEqualTo(src);
		assertThat(info.applicationJar)
				.satisfies(
						arg -> assertThat(arg).contains("quote.java."),
						arg -> assertThat(arg).endsWith(".jar")
				);
		assertThat(info.backingResource).isEqualTo(src);
		assertThat(info.javaVersion).isNull();
		assertThat(info.mainClass).isNull();
		assertThat(info.resolvedDependencies)
				.satisfies(
						arg -> assertThat(arg, hasSize(equalTo(1))),
						arg -> assertThat(arg, everyItem(containsString("picocli")))
				);
		assertThat(info.description).isEqualTo("For testing purposes");
		assertThat(info.gav).isEqualTo("dev.jbang.itests:quote");
	}

	@Test
	void testInfoToolsBuilt() {
		String src = examplesTestFolder.resolve("quote.java").toString();
		JBang.getCommandLine().execute("build", src);
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "tools", src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo(false);
		assertThat(info.originalResource).isEqualTo(src);
		assertThat(info.applicationJar)
				.satisfies(
						arg -> assertThat(arg).contains("quote.java."),
						arg -> assertThat(arg).endsWith(".jar")
				);
		assertThat(info.mainClass).isEqualTo("quote");
		assertThat(info.resolvedDependencies)
				.satisfies(
						arg -> assertThat(arg, hasSize(equalTo(1))),
						arg -> assertThat(arg, everyItem(containsString("picocli")))
				);
	}

	@Test
	void testInfoToolsWithDeps() {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		CommandLine.ParseResult pr = JBang	.getCommandLine()
											.parseArgs("info", "tools",
													"--deps", "info.picocli:picocli:4.6.3,commons-io:commons-io:2.8.0",
													"--deps", "org.apache.commons:commons-lang3:3.12.0",
													src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo(false);
		assertThat(info.originalResource).isEqualTo(src);
		assertThat(info.applicationJar)
				.satisfies(
						arg -> assertThat(arg).contains("helloworld.java."),
						arg -> assertThat(arg).endsWith(".jar")
				);
		assertThat(info.backingResource).isEqualTo(src);
		assertThat(info.javaVersion).isNull();
		assertThat(info.mainClass).isNull();
		assertThat(info.resolvedDependencies)
				.satisfies(
						arg -> assertThat(arg, hasSize(equalTo(3))),
						arg -> assertThat(arg, hasItem(containsString("picocli"))),
						arg -> assertThat(arg, hasItem(containsString("commons-io"))),
						arg -> assertThat(arg, hasItem(containsString("commons-lang3")))
				);
	}

	@Test
	void testInfoToolsWithClasspath() {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		String jar = examplesTestFolder.resolve("hellojar.jar").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "tools", "--cp", jar, src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo(false);
		assertThat(info.originalResource).isEqualTo(src);
		assertThat(info.applicationJar)
				.satisfies(
						arg -> assertThat(arg).contains("helloworld.java."),
						arg -> assertThat(arg).endsWith(".jar")
				);
		assertThat(info.backingResource).isEqualTo(src);
		assertThat(info.javaVersion).isNull();
		assertThat(info.mainClass).isNull();
		assertThat(info.resolvedDependencies)
				.satisfies(
						arg -> assertThat(arg, hasSize(equalTo(1))),
						arg -> assertThat(arg, everyItem(containsString(Paths.get("itests/hellojar.jar").toString())))
				);
	}

	@Test
	void testInfoClasspathNested() {
		String src = examplesTestFolder.resolve("sources.java").toString();
		String quote = examplesTestFolder.resolve("quote.java").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "tools", src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo(false);
		assertThat(info.originalResource).isEqualTo(src);
		assertThat(info.applicationJar)
				.satisfies(
						arg -> assertThat(arg).contains("sources.java."),
						arg -> assertThat(arg).endsWith(".jar")
				);
		assertThat(info.backingResource).isEqualTo(src);
		assertThat(info.javaVersion).isNull();
		assertThat(info.mainClass).isNull();
		assertThat(info.resolvedDependencies)
				.satisfies(
						arg -> assertThat(arg, hasSize(equalTo(1))),
						arg -> assertThat(arg, everyItem(containsString("picocli")))
				);
		assertThat(info.sources, hasSize(equalTo(2)));
		assertThat(info.sources.get(0).originalResource).isEqualTo(src);
		assertThat(info.sources.get(0).backingResource).isEqualTo(src);
		assertThat(info.sources.get(1).originalResource).isEqualTo(quote);
		assertThat(info.sources.get(1).backingResource).isEqualTo(quote);
	}

	@Test
	void testInfoJShell() {
		String src = examplesTestFolder.resolve("basic.jsh").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "tools", src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo(false);
		assertThat(info.originalResource).isEqualTo(src);
		assertThat(info.applicationJar).isEqualTo(null);
		assertThat(info.backingResource).isEqualTo(src);
		assertThat(info.javaVersion).isNull();
		assertThat(info.mainClass).isNull();
		assertThat(info.resolvedDependencies)
				.satisfies(
						arg -> assertThat(arg, hasSize(equalTo(1))),
						arg -> assertThat(arg, everyItem(containsString("commons-lang3")))
				);
	}

	@Test
	void testInfoHelloJar() {
		String jar = examplesTestFolder.resolve("hellojar.jar").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "tools", jar);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo(false);
		assertThat(info.originalResource).isEqualTo(jar);
		assertThat(info.applicationJar).isEqualTo(jar);
		assertThat(info.backingResource).isEqualTo(jar);
		assertThat(info.javaVersion).isNotNull();
		assertThat(info.mainClass).isEqualTo("helloworld");
		assertThat(info.resolvedDependencies).isEmpty();
	}

	@Test
	void testInfoStarSources() {
		String src = examplesTestFolder.resolve("sources/ying.java").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "tools", src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo(false);
		assertThat(info.originalResource).isEqualTo(src);
		// assertThat(info.applicationJar, equalTo(src));
		assertThat(info.backingResource).isEqualTo(src);
		// assertThat(info.javaVersion, not(nullValue()));
		// assertThat(info.mainClass, equalTo("helloworld"));
		assertThat(info.resolvedDependencies).isEmpty();
	}
}
