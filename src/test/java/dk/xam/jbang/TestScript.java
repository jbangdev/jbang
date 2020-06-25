package dk.xam.jbang;

import static dk.xam.jbang.Util.writeString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.xam.jbang.cli.JbangBaseScriptCommand;

class TestScript {

	String example = "//#!/usr/bin/env jbang\n" + "\n"
			+ "//DEPS com.offbytwo:docopt:0.6.0.20150202,log4j:log4j:1.2.14\n" + "\n" + "import org.docopt.Docopt;\n"
			+ "import java.io.File;\n" + "import java.util.*;\n" + "import static java.lang.System.*;\n" + "\n"
			+ "//JAVA_OPTIONS --enable-preview \"-Dvalue='this is space'\"\n"
			+ "//JAVAC_OPTIONS --enable-preview\n"
			+ "//JAVAC_OPTIONS --verbose \n"
			+ "class classpath_example {\n" + "\n"
			+ "\tString usage = \"jbang  - Enhanced scripting support for Java on *nix-based systems.\\n\" + \"\\n\" + \"Usage:\\n\"\n"
			+ "\t\t\t+ \"    jbang ( -t | --text ) <version>\\n\"\n"
			+ "\t\t\t+ \"    jbang [ --interactive | --idea | --package ] [--] ( - | <file or URL> ) [<args>]...\\n\"\n"
			+ "\t\t\t+ \"    jbang (-h | --help)\\n\" + \"\\n\" + \"Options:\\n\"\n"
			+ "\t\t\t+ \"    -t, --text         Enable stdin support API for more streamlined text processing  [default: latest]\\n\"\n"
			+ "\t\t\t+ \"    --package          Package script and dependencies into self-dependent binary\\n\"\n"
			+ "\t\t\t+ \"    --idea             boostrap IDEA from a jbang\\n\"\n"
			+ "\t\t\t+ \"    -i, --interactive  Create interactive shell with dependencies as declared in script\\n\"\n"
			+ "\t\t\t+ \"    -                  Read script from the STDIN\\n\" + \"    -h, --help         Print this text\\n\"\n"
			+ "\t\t\t+ \"    --clear-cache      Wipe cached script jars and urls\\n\" + \"\";\n" + "\n"
			+ "\tpublic static void main(String[] args) {\n"
			+ "\t\tString doArgs = new Docopt(usage).parse(args.toList());\n" + "\n"
			+ "\t\tout.println(\"parsed args are: \\n$doArgs (${doArgs.javaClass.simpleName})\\n\");\n" + "\n"
			+ "\t\t/*doArgs.forEach { (key: Any, value: Any) ->\n"
			+ "\t\t\t\t    println(\"$key:\\t$value\\t(${value?.javaClass?.canonicalName})\")\n" + "\t\t};*/\n" + "\n"
			+ "\t\tout.println(\"\\nHello from Java!\");\n" + "\t\tfor (String arg : args) {\n"
			+ "\t\t\tout.println(\"arg: $arg\");\n" + "\t\t}\n" + "\t\n" + "\t}\n" + "}";

	@Test
	void testFindDependencies() {
		Script script = new Script(example);

		List<String> dependencies = script.collectDependencies();
		assertEquals(2, dependencies.size());

		assertTrue(dependencies.contains("com.offbytwo:docopt:0.6.0.20150202"));

	}

	@Test
	void testCDS() {
		Script script = new Script("//CDS\nclass m { }");
		Script script2 = new Script("class m { }");

		assertTrue(script.enableCDS());
		assertFalse(script2.enableCDS());

	}

	@Test
	void testExtractDependencies() {
		List<String> deps = Script.extractDependencies("//DEPS blah, blue").collect(Collectors.toList());

		assertTrue(deps.contains("blah"));

		assertTrue(deps.contains("blue"));

	}

	@Test
	void textExtractRepositories() {
		List<String> repos = Script.extractRepositories("//REPOS jcenter=https://xyz.org").collect(Collectors.toList());

		assertThat(repos, hasItem("jcenter=https://xyz.org"));

		repos = Script	.extractRepositories("//REPOS jcenter=https://xyz.org localMaven xyz=file://~test")
						.collect(Collectors.toList());

		assertThat(repos, hasItem("jcenter=https://xyz.org"));
		assertThat(repos, hasItem("localMaven"));
		assertThat(repos, hasItem("xyz=file://~test"));
	}

	@Test
	void textExtractRepositoriesGrape() {
		List<String> deps = Script.extractRepositories(
				"@GrabResolver(name=\"restlet.org\", root=\"http://maven.restlet.org\")").collect(Collectors.toList());

		assertThat(deps, hasItem("restlet.org=http://maven.restlet.org"));

		deps = Script.extractRepositories("@GrabResolver(\"http://maven.restlet.org\")").collect(Collectors.toList());

		assertThat(deps, hasItem("http://maven.restlet.org"));

	}

	@Test
	void testExtractOptions() {
		Script s = new Script(example);

		assertEquals(s.collectCompileOptions(), Arrays.asList("--enable-preview", "--verbose"));

		assertEquals(s.collectRuntimeOptions(), Arrays.asList("--enable-preview", "-Dvalue='this is space'"));

	}

	@Test
	void testNonJavaExtension(@TempDir Path output) throws IOException {
		Path p = output.resolve("kube-example");
		writeString(p, example);

		Script s = JbangBaseScriptCommand.prepareScript(p.toAbsolutePath().toString());

	}

}
