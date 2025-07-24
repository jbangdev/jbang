package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
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
		org.assertj.core.api.Assertions.assertThat(info.originalResource).isEqualTo(src);
		org.assertj.core.api.Assertions.assertThat(info.applicationJar)
			.satisfies(
				arg -> org.assertj.core.api.Assertions.assertThat(arg).contains("quote.java."),
				arg -> org.assertj.core.api.Assertions.assertThat(arg).endsWith(".jar")
			);
		org.assertj.core.api.Assertions.assertThat(info.backingResource).isEqualTo(src);
		org.assertj.core.api.Assertions.assertThat(info.javaVersion).isNull();
		org.assertj.core.api.Assertions.assertThat(info.mainClass).isNull();
		org.assertj.core.api.Assertions.assertThat(info.resolvedDependencies)
			.satisfies(
				arg -> assertThat(arg, hasSize(equalTo(1))),
				arg -> assertThat(arg, everyItem(containsString("picocli")))
			);
		org.assertj.core.api.Assertions.assertThat(info.description).isEqualTo("For testing purposes");
		org.assertj.core.api.Assertions.assertThat(info.gav).isEqualTo("dev.jbang.itests:quote");
	}

	@Test
	void testInfoToolsBuilt() {
		String src = examplesTestFolder.resolve("quote.java").toString();
		JBang.getCommandLine().execute("build", src);
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "tools", src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo(false);
		org.assertj.core.api.Assertions.assertThat(info.originalResource).isEqualTo(src);
		org.assertj.core.api.Assertions.assertThat(info.applicationJar)
			.satisfies(
				arg -> org.assertj.core.api.Assertions.assertThat(arg).contains("quote.java."),
				arg -> org.assertj.core.api.Assertions.assertThat(arg).endsWith(".jar")
			);
		org.assertj.core.api.Assertions.assertThat(info.mainClass).isEqualTo("quote");
		org.assertj.core.api.Assertions.assertThat(info.resolvedDependencies)
			.satisfies(
				arg -> assertThat(arg, hasSize(equalTo(1))),
				arg -> assertThat(arg, everyItem(containsString("picocli")))
			);
	}

	@Test
	void testInfoToolsWithDeps() {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine()
			.parseArgs("info", "tools",
					"--deps", "info.picocli:picocli:4.6.3,commons-io:commons-io:2.8.0",
					"--deps", "org.apache.commons:commons-lang3:3.12.0",
					src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo(false);
		org.assertj.core.api.Assertions.assertThat(info.originalResource).isEqualTo(src);
		org.assertj.core.api.Assertions.assertThat(info.applicationJar)
			.satisfies(
				arg -> org.assertj.core.api.Assertions.assertThat(arg).contains("helloworld.java."),
				arg -> org.assertj.core.api.Assertions.assertThat(arg).endsWith(".jar")
			);
		org.assertj.core.api.Assertions.assertThat(info.backingResource).isEqualTo(src);
		org.assertj.core.api.Assertions.assertThat(info.javaVersion).isNull();
		org.assertj.core.api.Assertions.assertThat(info.mainClass).isNull();
		org.assertj.core.api.Assertions.assertThat(info.resolvedDependencies)
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
		org.assertj.core.api.Assertions.assertThat(info.originalResource).isEqualTo(src);
		org.assertj.core.api.Assertions.assertThat(info.applicationJar)
			.satisfies(
				arg -> org.assertj.core.api.Assertions.assertThat(arg).contains("helloworld.java."),
				arg -> org.assertj.core.api.Assertions.assertThat(arg).endsWith(".jar")
			);
		org.assertj.core.api.Assertions.assertThat(info.backingResource).isEqualTo(src);
		org.assertj.core.api.Assertions.assertThat(info.javaVersion).isNull();
		org.assertj.core.api.Assertions.assertThat(info.mainClass).isNull();
		org.assertj.core.api.Assertions.assertThat(info.resolvedDependencies)
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
		org.assertj.core.api.Assertions.assertThat(info.originalResource).isEqualTo(src);
		org.assertj.core.api.Assertions.assertThat(info.applicationJar)
			.satisfies(
				arg -> org.assertj.core.api.Assertions.assertThat(arg).contains("sources.java."),
				arg -> org.assertj.core.api.Assertions.assertThat(arg).endsWith(".jar")
			);
		org.assertj.core.api.Assertions.assertThat(info.backingResource).isEqualTo(src);
		org.assertj.core.api.Assertions.assertThat(info.javaVersion).isNull();
		org.assertj.core.api.Assertions.assertThat(info.mainClass).isNull();
		org.assertj.core.api.Assertions.assertThat(info.resolvedDependencies)
			.satisfies(
				arg -> assertThat(arg, hasSize(equalTo(1))),
				arg -> assertThat(arg, everyItem(containsString("picocli")))
			);
		assertThat(info.sources, hasSize(equalTo(2)));
		org.assertj.core.api.Assertions.assertThat(info.sources.get(0).originalResource).isEqualTo(src);
		org.assertj.core.api.Assertions.assertThat(info.sources.get(0).backingResource).isEqualTo(src);
		org.assertj.core.api.Assertions.assertThat(info.sources.get(1).originalResource).isEqualTo(quote);
		org.assertj.core.api.Assertions.assertThat(info.sources.get(1).backingResource).isEqualTo(quote);
	}

	@Test
	void testInfoJShell() {
		String src = examplesTestFolder.resolve("basic.jsh").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "tools", src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo(false);
		org.assertj.core.api.Assertions.assertThat(info.originalResource).isEqualTo(src);
		org.assertj.core.api.Assertions.assertThat(info.applicationJar).isEqualTo(null);
		org.assertj.core.api.Assertions.assertThat(info.backingResource).isEqualTo(src);
		org.assertj.core.api.Assertions.assertThat(info.javaVersion).isNull();
		org.assertj.core.api.Assertions.assertThat(info.mainClass).isNull();
		org.assertj.core.api.Assertions.assertThat(info.resolvedDependencies)
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
		org.assertj.core.api.Assertions.assertThat(info.originalResource).isEqualTo(jar);
		org.assertj.core.api.Assertions.assertThat(info.applicationJar).isEqualTo(jar);
		org.assertj.core.api.Assertions.assertThat(info.backingResource).isEqualTo(jar);
		org.assertj.core.api.Assertions.assertThat(info.javaVersion).isNotNull();
		org.assertj.core.api.Assertions.assertThat(info.mainClass).isEqualTo("helloworld");
		org.assertj.core.api.Assertions.assertThat(info.resolvedDependencies).isEmpty();
	}

	@Test
	void testInfoStarSources() {
		String src = examplesTestFolder.resolve("sources/ying.java").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "tools", src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo(false);
		org.assertj.core.api.Assertions.assertThat(info.originalResource).isEqualTo(src);
		// assertThat(info.applicationJar, equalTo(src));
		org.assertj.core.api.Assertions.assertThat(info.backingResource).isEqualTo(src);
		// assertThat(info.javaVersion, not(nullValue()));
		// assertThat(info.mainClass, equalTo("helloworld"));
		org.assertj.core.api.Assertions.assertThat(info.resolvedDependencies).isEmpty();
	}

	@Test
	void testInfoDocsFile() {
		String src = examplesTestFolder.resolve("docstest1.java").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "docs", src);
		Docs docs = (Docs) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ProjectFile pf = docs.getInfo(false).docs.get("main").get(0);
		org.assertj.core.api.Assertions.assertThat(pf.originalResource).endsWith(File.separator + "itests" + File.separator + "readme.md");
	}

	@Test
	void testInfoDocsUrl() {
		String src = examplesTestFolder.resolve("docstest2.java").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "docs", src);
		Docs docs = (Docs) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ProjectFile pf = docs.getInfo(false).docs.get("main").get(0);
		org.assertj.core.api.Assertions.assertThat(pf.originalResource).isEqualTo("https://www.jbang.dev/documentation/guide/latest/faq.html");
	}

	@Test
	void givenScriptWithoutDocsDirectiveWhenInfoDocsCommandIsInvokedThenReturnEmptyResult() {
		String src = examplesTestFolder.resolve("docstest_nodocs.java").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "docs", src);
		Docs docs = (Docs) pr.subcommand().subcommand().commandSpec().userObject();
		org.assertj.core.api.Assertions.assertThat(docs.getInfo(false).docs.isEmpty()).isEqualTo(true);
	}

	@Test
	void testInfoToolsWithDocs() throws IOException {
		String src = examplesTestFolder.resolve("docsexample.java").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "tools", src);
		Tools docs = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		docs.call();
	}
}
