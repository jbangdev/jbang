package dev.jbang.cli;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.DecoratedSource;
import dev.jbang.Util;

import picocli.CommandLine;

class TestExternalDeps extends BaseTest {

	String checkdeps = "public class test {"
			+ "public static void main(String... args) {"
			+ ""
			+ " \n"
			+ "    ClassLoader classLoader = test.class.getClassLoader();\n"
			+ "\n"
			+ "    try {\n"
			+ "        Class aClass = classLoader.loadClass(\"picocli.Commandline\");\n"
			+ "        System.out.println(aClass.getName());\n"
			+ "    } catch (ClassNotFoundException e) {\n"
			+ "        System.out.println(\"ERROR\");\n"
			+ "    } "
			+ "}"
			+ ""
			+ ""
			+ "}";

	@Test
	void testDepsWork(@TempDir File dir) throws IOException {

		File f = new File(dir, "test.java");

		Util.writeString(f.toPath(), checkdeps);

		Jbang jbang = new Jbang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--deps", "info.picocli:picocli:4.6.1",
				f.getPath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		DecoratedSource xrunit = DecoratedSource.forResource(f.getPath(), run.userParams, run.properties,
				run.getDependencyContext(), run.fresh,
				run.forcejsh);

		run.prepareArtifacts(xrunit);

		String result = run.generateCommandLine(xrunit);

		assertThat(result, containsString("pico"));

	}

	@Test
	void testReposWork(@TempDir File dir) throws IOException {

		File f = new File(dir, "test.java");

		Util.writeString(f.toPath(), checkdeps);

		Jbang jbang = new Jbang();
		CommandLine.ParseResult pr = new CommandLine(jbang).parseArgs("run", "--repos", "mavencentral", "--repos",
				"https://jitpack.io",
				"--deps", "com.github.jbangdev.jbang-resolver:shrinkwrap-resolver-api:3.1.5-allowpom", f.getPath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		DecoratedSource xrunit = DecoratedSource.forResource(f.getPath(), run.userParams, run.properties,
				run.getDependencyContext(), run.fresh,
				run.forcejsh);

		run.prepareArtifacts(xrunit);

		String result = run.generateCommandLine(xrunit);

		assertThat(result, containsString("com.github.jbangdev.jbang"));

	}

}
