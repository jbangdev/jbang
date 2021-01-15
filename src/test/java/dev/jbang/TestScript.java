package dev.jbang;

import static dev.jbang.ExtendedScript.prepareScript;
import static dev.jbang.Util.writeString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestScript extends BaseTest {

	String example = "//#!/usr/bin/env jbang\n" + "\n"
			+ "//DEPS com.offbytwo:docopt:0.6.0.20150202,log4j:log4j:${log4j.version:1.2.14}\n" + "\n"
			+ "import org.docopt.Docopt;\n"
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

	String exampleURLInsourceMain = "///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n"
			+ "\n"
			+ "//JAVA 15\n"
			+ "\n"
			+ "//SOURCES Hi.java\n"
			+ "//SOURCES https://gist.github.com/tivrfoa/bb5deb269de39eb8fca9636dd3c9f123#file-gsonhelper-java\n"
			+ "//SOURCES pkg1/Bye.java\n"
			+ "\n"
			+ "import pkg1.Bye;\n"
			+ "\n"
			+ "public class Main {\n"
			+ "	\n"
			+ "	private static final String JSON = \"\"\"\n"
			+ "	{\n"
			+ "	  \"title\": \"Free Music Archive - Albums\",\n"
			+ "	  \"message\": \"\",\n"
			+ "	  \"errors\": [],\n"
			+ "	  \"total\": \"11259\",\n"
			+ "	  \"total_pages\": 2252,\n"
			+ "	  \"page\": 1,\n"
			+ "	  \"limit\": \"5\",\n"
			+ "	  \"dataset\": [\n"
			+ "		{\n"
			+ "		  \"album_id\": \"7596\",\n"
			+ "		  \"album_title\": \"Album 1\",\n"
			+ "		  \"album_images\": [\n"
			+ "			{\n"
			+ "			  \"image_id\": \"1\",\n"
			+ "			  \"user_id\": null\n"
			+ "			}\n"
			+ "		  ]\n"
			+ "		}\n"
			+ "	  ]\n"
			+ "	}\n"
			+ "	\"\"\";\n"
			+ "\n"
			+ "    public static void main(String... args) {\n"
			+ "    	System.out.println(\"Testing //SOURCES url, where url \" +\n"
			+ "				\"also contains //SOURCES and //DEPS\");\n"
			+ "\n"
			+ "		Hi.say();\n"
			+ "		\n"
			+ "		Albums albums = GsonHelper.getAlbums(JSON);\n"
			+ "		System.out.println(albums.title);\n"
			+ "		System.out.println(albums.dataset.get(0).album_title);\n"
			+ "		System.out.println(albums.dataset.get(0).album_images);\n"
			+ "\n"
			+ "		Bye.say();\n"
			+ "    }\n"
			+ "}\n";

	String exampleURLInsourceHi = "//SOURCES pkg1/Hello.java\n"
			+ "\n"
			+ "import pkg1.Hello;\n"
			+ "\n"
			+ "public class Hi {\n"
			+ "    \n"
			+ "    public static void say() {\n"
			+ "		System.out.println(\"Hi!!!\");\n"
			+ "		Hello.say();\n"
			+ "    }\n"
			+ "}\n";

	String exampleURLInsourceHello = "package pkg1;\n"
			+ "\n"
			+ "public class Hello {\n"
			+ "    \n"
			+ "    public static void say() {\n"
			+ "		System.out.println(\"Hello!!!\");\n"
			+ "    }\n"
			+ "}\n";

	String exampleURLInsourceBye = "package pkg1;\n"
			+ "\n"
			+ "public class Bye {\n"
			+ "    \n"
			+ "    public static void say() {\n"
			+ "		System.out.println(\"Bye!!!\");\n"
			+ "    }\n"
			+ "}";

	String exampleCommandsWithComments = "//DEPS info.picocli:picocli:4.5.0 // <.>\n" +
			"//JAVA 14+ // <.>\n" +
			"//JAVAC_OPTIONS commons-codec:commons-codec:1.15 // <.>\n" +
			"public class test {" +
			"}";

	@Test
	void testCommentsDoesNotGetPickedUp() {
		Script script = new Script(exampleCommandsWithComments);

		assertEquals(script.javaVersion(), "14+");

		List<String> deps = script.collectAllDependencies(System.getProperties());

		assertThat(deps, containsInAnyOrder("info.picocli:picocli:4.5.0"));
	}

	@Test
	void testFindDependencies() {
		Script script = new Script(example);

		List<String> dependencies = script.collectAllDependencies(System.getProperties());
		assertEquals(2, dependencies.size());

		assertTrue(dependencies.contains("com.offbytwo:docopt:0.6.0.20150202"));
		assertTrue(dependencies.contains("log4j:log4j:1.2.14"));

	}

	@Test
	void testFindDependenciesWithProperty() {

		Map<String, String> p = new HashMap<>();
		p.put("log4j.version", "1.2.9");

		ExtendedScript script = new ExtendedScript(example, (List<String>) null, (Map<String, String>) p);

		List<String> dependencies = script.collectAllDependencies();
		assertEquals(2, dependencies.size());

		assertTrue(dependencies.contains("com.offbytwo:docopt:0.6.0.20150202"));
		assertTrue(dependencies.contains("log4j:log4j:1.2.9"));

	}

	@Test
	void testFindDependenciesWithURLInsource() throws IOException {
		Path mainPath = createTmpFileWithContent("", "Main.java", exampleURLInsourceMain);
		createTmpFileWithContent("", "Hi.java", exampleURLInsourceHi);
		createTmpFileWithContent("pkg1", "Hello.java", exampleURLInsourceHello);
		createTmpFileWithContent("pkg1", "Bye.java", exampleURLInsourceBye);
		String scriptURL = mainPath.toString();
		Script script = prepareScript(scriptURL);

		List<Script> resolvesourceRecursively = script.collectAllSources();
		assertTrue(resolvesourceRecursively.size() == 7);
	}

	public static Path createTmpFileWithContent(String strPath, String fileName, String content) throws IOException {
		return createTmpFileWithContent(null, strPath, fileName, content);
	}

	public static Path createTmpFileWithContent(Path parent, String strPath, String fileName, String content)
			throws IOException {
		Path path = createTmpFile(parent, strPath, fileName);
		writeContentToFile(path, content);
		return path;
	}

	public static Path createTmpFile(String strPath, String fileName) throws IOException {
		return createTmpFile(null, strPath, fileName);
	}

	public static Path createTmpFile(Path parent, String strPath, String fileName) throws IOException {
		Path dir = null;
		if (parent == null) {
			String defaultBaseDir = System.getProperty("java.io.tmpdir");
			dir = Paths.get(defaultBaseDir + File.separator + strPath);
		} else {
			dir = parent.resolve(strPath);
		}
		if (!Files.exists(dir))
			dir = Files.createDirectory(dir);
		return dir.resolve(fileName);
	}

	public static void writeContentToFile(Path path, String content) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(path)) {
			writer.write(content);
		}
	}

	@Test
	public void testSourceInGistURL(@TempDir Path temp) throws IOException {
		String url = "https://gist.github.com/tivrfoa/8e6ea001f168fd4ef14763ceca3e5ab6#file-one-java";

		File tempFile = temp.resolve("temptrust.json").toFile();

		try {
			Settings.getTrustedSources().add(url, tempFile);

			Script script = prepareScript(url);
			assertEquals(2, script.collectAllSources().size());
			boolean foundtwo = false;
			boolean foundt3 = false;
			for (Script source : script.collectAllSources()) {
				if (source.getScriptResource().getFile().getName().equals("two.java"))
					foundtwo = true;
				if (source.getScriptResource().getFile().getName().equals("t3.java"))
					foundt3 = true;
			}
			assertTrue(foundtwo && foundt3);
		} finally {
			Settings.getTrustedSources().remove(Collections.singletonList(url), tempFile);
		}
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

		assertEquals(s.collectAllCompileOptions(), Arrays.asList("--enable-preview", "--verbose"));

		assertEquals(s.collectAllRuntimeOptions(), Arrays.asList("--enable-preview", "-Dvalue='this is space'"));

	}

	@Test
	void testNonJavaExtension(@TempDir Path output) throws IOException {
		Path p = output.resolve("kube-example");
		writeString(p, example);

		prepareScript(p.toAbsolutePath().toString());

	}

}
