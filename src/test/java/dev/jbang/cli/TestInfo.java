package dev.jbang.cli;

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
		JBang jbang = new JBang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("info", "tools", src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo();
		assertThat(info.originalResource, equalTo(src));
		assertThat(info.applicationJar, allOf(
				containsString("quote.java."),
				endsWith(".jar")));
		assertThat(info.backingResource, equalTo(src));
		assertThat(info.javaVersion, is(nullValue()));
		assertThat(info.mainClass, is(nullValue()));
		assertThat(info.resolvedDependencies, Matchers.<Collection<String>>allOf(
				hasSize(equalTo(1)),
				everyItem(containsString("picocli"))));
		assertThat(info.description, equalTo("For testing purposes"));
		assertThat(info.gav, equalTo("dev.jbang.itests:quote"));
	}

	@Test
	void testInfoToolsBuilt() {
		String src = examplesTestFolder.resolve("quote.java").toString();
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("build", src);
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("info", "tools", src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo();
		assertThat(info.originalResource, equalTo(src));
		assertThat(info.applicationJar, allOf(
				containsString("quote.java."),
				endsWith(".jar")));
		assertThat(info.mainClass, equalTo("quote"));
		assertThat(info.resolvedDependencies, Matchers.<Collection<String>>allOf(
				hasSize(equalTo(1)),
				everyItem(containsString("picocli"))));
	}

	@Test
	void testInfoToolsWithDeps() {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		JBang jbang = new JBang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("info", "tools",
				"--deps", "info.picocli:picocli:4.6.3,commons-io:commons-io:2.8.0",
				"--deps", "org.apache.commons:commons-lang3:3.12.0",
				src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo();
		assertThat(info.originalResource, equalTo(src));
		assertThat(info.applicationJar, allOf(
				containsString("helloworld.java."),
				endsWith(".jar")));
		assertThat(info.backingResource, equalTo(src));
		assertThat(info.javaVersion, is(nullValue()));
		assertThat(info.mainClass, is(nullValue()));
		assertThat(info.resolvedDependencies, Matchers.<Collection<String>>allOf(
				hasSize(equalTo(3)),
				hasItem(containsString("picocli")),
				hasItem(containsString("commons-io")),
				hasItem(containsString("commons-lang3"))));
	}

	@Test
	void testInfoToolsWithClasspath() {
		String src = examplesTestFolder.resolve("helloworld.java").toString();
		String jar = examplesTestFolder.resolve("hellojar.jar").toString();
		JBang jbang = new JBang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("info", "tools", "--cp", jar, src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo();
		assertThat(info.originalResource, equalTo(src));
		assertThat(info.applicationJar, allOf(
				containsString("helloworld.java."),
				endsWith(".jar")));
		assertThat(info.backingResource, equalTo(src));
		assertThat(info.javaVersion, is(nullValue()));
		assertThat(info.mainClass, is(nullValue()));
		assertThat(info.resolvedDependencies, Matchers.<Collection<String>>allOf(
				hasSize(equalTo(1)),
				everyItem(containsString(Paths.get("itests/hellojar.jar").toString()))));
	}

	@Test
	void testInfoClasspathNested() {
		String src = examplesTestFolder.resolve("sources.java").toString();
		String quote = examplesTestFolder.resolve("quote.java").toString();
		JBang jbang = new JBang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("info", "tools", src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo();
		assertThat(info.originalResource, equalTo(src));
		assertThat(info.applicationJar, allOf(
				containsString("sources.java."),
				endsWith(".jar")));
		assertThat(info.backingResource, equalTo(src));
		assertThat(info.javaVersion, is(nullValue()));
		assertThat(info.mainClass, is(nullValue()));
		assertThat(info.resolvedDependencies, Matchers.<Collection<String>>allOf(
				hasSize(equalTo(1)),
				everyItem(containsString("picocli"))));
		assertThat(info.sources, hasSize(equalTo(2)));
		assertThat(info.sources.get(0).originalResource, equalTo(src));
		assertThat(info.sources.get(0).backingResource, equalTo(src));
		assertThat(info.sources.get(1).originalResource, equalTo(quote));
		assertThat(info.sources.get(1).backingResource, equalTo(quote));
	}

	@Test
	void testInfoJShell() {
		String src = examplesTestFolder.resolve("basic.jsh").toString();
		JBang jbang = new JBang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("info", "tools", src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo();
		assertThat(info.originalResource, equalTo(src));
		assertThat(info.applicationJar, equalTo(null));
		assertThat(info.backingResource, equalTo(src));
		assertThat(info.javaVersion, is(nullValue()));
		assertThat(info.mainClass, is(nullValue()));
		assertThat(info.resolvedDependencies, Matchers.<Collection<String>>allOf(
				hasSize(equalTo(1)),
				everyItem(containsString("commons-lang3"))));
	}

	@Test
	void testInfoHelloJar() {
		String jar = examplesTestFolder.resolve("hellojar.jar").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "tools", jar);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo();
		assertThat(info.originalResource, equalTo(jar));
		assertThat(info.applicationJar, equalTo(jar));
		assertThat(info.backingResource, equalTo(jar));
		assertThat(info.javaVersion, not(nullValue()));
		assertThat(info.mainClass, equalTo("helloworld"));
		assertThat(info.resolvedDependencies, empty());
	}

	@Test
	void testInfoStarSources() {
		String src = examplesTestFolder.resolve("sources/ying.java").toString();
		CommandLine.ParseResult pr = JBang.getCommandLine().parseArgs("info", "tools", src);
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo();
		assertThat(info.originalResource, equalTo(src));
		// assertThat(info.applicationJar, equalTo(src));
		assertThat(info.backingResource, equalTo(src));
		// assertThat(info.javaVersion, not(nullValue()));
		// assertThat(info.mainClass, equalTo("helloworld"));
		assertThat(info.resolvedDependencies, empty());
	}
}
