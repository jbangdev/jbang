package dev.jbang.cli;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.source.BuildContext;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.util.Util;

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
	void testDepsWork() throws IOException {
		File f = Util.getCwd().resolve("test.java").toFile();

		Util.writeString(f.toPath(), checkdeps);

		CommandLine.ParseResult pr = JBang	.getCommandLine()
											.parseArgs("run", "--deps", "info.picocli:picocli:4.6.3",
													f.getPath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(f.getPath());
		BuildContext ctx = BuildContext.forProject(prj);
		prj.builder(ctx).build();

		String result = prj.cmdGenerator(ctx).generate();

		assertThat(result, containsString("pico"));

	}

	@Test
	void testReposWork() throws IOException {
		File f = Util.getCwd().resolve("test.java").toFile();

		Util.writeString(f.toPath(), checkdeps);

		CommandLine.ParseResult pr = JBang	.getCommandLine()
											.parseArgs("run", "--repos", "mavencentral", "--repos",
													"https://jitpack.io",
													"--deps",
													"com.github.jbangdev.jbang-resolver:shrinkwrap-resolver-api:3.1.5-allowpom",
													f.getPath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		ProjectBuilder pb = run.createProjectBuilder();
		Project prj = pb.build(f.getPath());
		BuildContext ctx = BuildContext.forProject(prj);
		prj.builder(ctx).build();

		String result = prj.cmdGenerator(ctx).generate();

		assertThat(result, matchesPattern(".*com[/\\\\]github[/\\\\]jbangdev[/\\\\]jbang.*"));

	}

}