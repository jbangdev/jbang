package dk.xam.jbang;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class TestScript {

	String example = "//#!/usr/bin/env jbang\n" + "\n"
			+ "//DEPS com.offbytwo:docopt:0.6.0.20150202,log4j:log4j:1.2.14\n" + "\n" + "import org.docopt.Docopt;\n"
			+ "import java.io.File;\n" + "import java.util.*;\n" + "import static java.lang.System.*;\n" + "\n"
			+ "class classpath_example {\n" + "\n"
			+ "\tString usage = \"jbang  - Enhanced scripting support for Java on *nix-based systems.\\n\" + \"\\n\" + \"Usage:\\n\"\n"
			+ "\t\t\t+ \"    jbang ( -t | --text ) <version>\\n\"\n"
			+ "\t\t\t+ \"    jbang [ --interactive | --idea | --package ] [--] ( - | <file or URL> ) [<args>]...\\n\"\n"
			+ "\t\t\t+ \"    jbang (-h | --help)\\n\" + \"\\n\" + \"Options:\\n\"\n"
			+ "\t\t\t+ \"    -t, --text         Enable stdin support API for more streamlined text processing  [default: latest]\\n\"\n"
			+ "\t\t\t+ \"    --package          Package script and dependencies into self-dependent binary\\n\"\n"
			+ "\t\t\t+ \"    --idea             boostrap IDEA from a kscript\\n\"\n"
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
	void testExtractDependencies() {

		Script s = new Script(example);

		var deps = s.extractDependencies("//DEPS blah, blue").collect(Collectors.toList());

		assertTrue(deps.contains("blah"));

		assertTrue(deps.contains("blue"));

	}
}
