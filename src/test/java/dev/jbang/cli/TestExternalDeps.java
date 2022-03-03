package dev.jbang.cli;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.source.Code;
import dev.jbang.source.DefaultCmdGenerator;
import dev.jbang.source.RunContext;
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
											.parseArgs("run", "--deps", "info.picocli:picocli:4.6.1",
													f.getPath());
		Run run = (Run) pr.subcommand().commandSpec().userObject();

		RunContext ctx = run.getRunContext();
		Code code = ctx.forResource(f.getPath());
		code = run.prepareArtifacts(code, ctx);

		String result = new DefaultCmdGenerator().generate(code, ctx);

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

		RunContext ctx = run.getRunContext();
		Code code = ctx.forResource(f.getPath());
		code = run.prepareArtifacts(code, ctx);

		String result = new DefaultCmdGenerator().generate(code, ctx);

		assertThat(result, containsString("com.github.jbangdev.jbang"));

	}

}