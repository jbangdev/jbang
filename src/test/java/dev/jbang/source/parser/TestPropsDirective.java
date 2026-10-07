package dev.jbang.source.parser;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.Properties;

import org.junit.jupiter.api.Test;

public class TestPropsDirective {

	private static Properties props(String... kvs) {
		Properties p = new Properties();
		for (int i = 0; i < kvs.length; i += 2) {
			p.setProperty(kvs[i], kvs[i + 1]);
		}
		return p;
	}

	@Test
	void testPropsReplacedInDirectives() {
		Directives d = new Directives.Extended(
				"//PROPS base=../lib/src\n"
						+ "//PROPS quarkus.version=3.25\n"
						+ "//SOURCES ${base}/A.java\n"
						+ "//SOURCES ${base}/util/B.java\n"
						+ "//DEPS io.quarkus:quarkus-arc:${quarkus.version}\n",
				new Properties());
		assertThat(d.sources(), contains("../lib/src/A.java", "../lib/src/util/B.java"));
		assertThat(d.binaryDependencies(), contains("io.quarkus:quarkus-arc:3.25"));
	}

	@Test
	void testPropsPositionDoesNotMatter() {
		Directives d = new Directives.Extended(
				"//DEPS io.quarkus:quarkus-arc:${quarkus.version}\n"
						+ "//PROPS quarkus.version=3.25\n",
				new Properties());
		assertThat(d.binaryDependencies(), contains("io.quarkus:quarkus-arc:3.25"));
	}

	@Test
	void testPropsEvaluatedInOrder() {
		Directives d = new Directives.Extended(
				"//PROPS root=../lib\n"
						+ "//PROPS base=${root}/src\n"
						+ "//SOURCES ${base}/A.java\n",
				new Properties());
		assertThat(d.sources(), contains("../lib/src/A.java"));
	}

	@Test
	void testMultiplePropsPerLineAndQuoting() {
		Directives d = new Directives.Extended(
				"//PROPS a=1 b=\"two words\" c='3' empty=\n",
				new Properties());
		Properties p = d.properties();
		assertThat(p.getProperty("a"), equalTo("1"));
		assertThat(p.getProperty("b"), equalTo("two words"));
		assertThat(p.getProperty("c"), equalTo("3"));
		assertThat(p.getProperty("empty"), equalTo(""));
	}

	@Test
	void testLastDuplicateWins() {
		Directives d = new Directives.Extended(
				"//PROPS v=1\n"
						+ "//DEPS g:a:${v}\n"
						+ "//PROPS v=2\n",
				new Properties());
		assertThat(d.binaryDependencies(), contains("g:a:2"));
	}

	@Test
	void testInheritedPropertiesWin() {
		Directives d = new Directives.Extended(
				"//PROPS v=1 w=1\n"
						+ "//DEPS g:a:${v}, g:b:${w}\n",
				props("v", "42"));
		assertThat(d.binaryDependencies(), containsInAnyOrder("g:a:42", "g:b:1"));
	}

	@Test
	void testInheritedPropertiesNotModified() {
		Properties inherited = props("v", "42");
		Directives d = new Directives.Extended("//PROPS w=1\n", inherited);
		assertThat(d.properties().getProperty("w"), equalTo("1"));
		assertThat(inherited.getProperty("w"), nullValue());
	}

	@Test
	void testDefaultValueStillWorks() {
		Directives d = new Directives.Extended(
				"//DEPS g:a:${v:1.0}, g:b:${w:1.0}\n"
						+ "//PROPS w=2.0\n",
				new Properties());
		assertThat(d.binaryDependencies(), containsInAnyOrder("g:a:1.0", "g:b:2.0"));
	}

	@Test
	void testInvalidEntriesIgnored() {
		Directives d = new Directives.Extended(
				"//PROPS novalue a=1\n",
				new Properties());
		Properties p = d.properties();
		assertThat(p.getProperty("novalue"), nullValue());
		assertThat(p.getProperty("a"), equalTo("1"));
	}

	@Test
	void testNoPropertiesMeansNoReplacement() {
		Directives d = new Directives.Extended(
				"//PROPS v=1\n"
						+ "//DEPS g:a:${v}\n",
				null);
		assertThat(d.properties(), nullValue());
		assertThat(d.binaryDependencies(), contains("g:a:${v}"));
	}

	@Test
	void testPropsInJbangProject() {
		Directives d = new Directives.JbangProject(
				"PROPS v=1.2.3\n"
						+ "DEPS g:a:${v}\n",
				new Properties());
		assertThat(d.binaryDependencies(), contains("g:a:1.2.3"));
	}
}
