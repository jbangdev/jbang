package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

import java.nio.file.Paths;
import java.util.Collection;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

import picocli.CommandLine;

public class TestInfo extends BaseTest {

	@Test
	void testInfoToolsSimple() {
		Jbang jbang = new Jbang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("info", "tools", "itests/quote.java");
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo();
		assertThat(info.originalResource, equalTo("itests/quote.java"));
		assertThat(info.applicationJar, allOf(
				containsString("quote.java."),
				endsWith(".jar")));
		assertThat(info.backingResource, equalTo(Paths.get("itests/quote.java").toString()));
//		assertThat(info.javaVersion, is(nullValue()));
//		assertThat(info.mainClass, is(nullValue()));
		assertThat(info.resolvedDependencies, Matchers.<Collection<String>>allOf(
				hasSize(equalTo(1)),
				everyItem(containsString("picocli"))));
	}

	@Test
	void testInfoToolsBuilt() {
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("build", "itests/quote.java");
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("info", "tools", "itests/quote.java");
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo();
		assertThat(info.originalResource, equalTo("itests/quote.java"));
		assertThat(info.applicationJar, allOf(
				containsString("quote.java."),
				endsWith(".jar")));
		assertThat(info.backingResource, equalTo(Paths.get("itests/quote.java").toString()));
		assertThat(Integer.parseInt(info.javaVersion), greaterThan(0));
		assertThat(info.mainClass, equalTo("quote"));
		assertThat(info.resolvedDependencies, Matchers.<Collection<String>>allOf(
				hasSize(equalTo(1)),
				everyItem(containsString("picocli"))));
	}

	@Test
	void testInfoToolsWithDeps() {
		Jbang jbang = new Jbang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("info", "tools", "--deps",
				"info.picocli:picocli:4.5.0", "itests/helloworld.java");
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo();
		assertThat(info.originalResource, equalTo("itests/helloworld.java"));
		assertThat(info.applicationJar, allOf(
				containsString("helloworld.java."),
				endsWith(".jar")));
		assertThat(info.backingResource, equalTo(Paths.get("itests/helloworld.java").toString()));
//		assertThat(info.javaVersion, is(nullValue()));
//		assertThat(info.mainClass, is(nullValue()));
		assertThat(info.resolvedDependencies, Matchers.<Collection<String>>allOf(
				hasSize(equalTo(1)),
				everyItem(containsString("picocli"))));
	}

	@Test
	void testInfoToolsWithClasspath() {
		Jbang jbang = new Jbang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("info", "tools", "--cp", "itests/hellojar.jar",
				"itests/helloworld.java");
		Tools tools = (Tools) pr.subcommand().subcommand().commandSpec().userObject();
		BaseInfoCommand.ScriptInfo info = tools.getInfo();
		assertThat(info.originalResource, equalTo("itests/helloworld.java"));
		assertThat(info.applicationJar, allOf(
				containsString("helloworld.java."),
				endsWith(".jar")));
		assertThat(info.backingResource, equalTo(Paths.get("itests/helloworld.java").toString()));
//		assertThat(info.javaVersion, is(nullValue()));
//		assertThat(info.mainClass, is(nullValue()));
		assertThat(info.resolvedDependencies, Matchers.<Collection<String>>allOf(
				hasSize(equalTo(1)),
				everyItem(containsString("itests/hellojar.jar"))));
	}
}
