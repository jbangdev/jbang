package dev.jbang.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

class TestSourcesRecursivelyMultipleFiles extends BaseTest {

	String classA = "//SOURCES person/B.java\n"
			+ "\n"
			+ "import person.B;\n"
			+ "\n"
			+ "public class A {\n"
			+ "    \n"
			+ "    public static void main(String args[]) {\n"
			+ "        new B();\n"
			+ "		   new HiJBang();\n"
			+ "    }\n"
			+ "}\n";

	String classB = "package person;\n"
			+ "\n"
			+ "//SOURCES model/C.java\n"
			+ "\n"
			+ "import person.model.C;\n"
			+ "\n"
			+ "public class B {\n"
			+ "    \n"
			+ "	public B() {\n"
			+ "		System.out.println(\"B constructor\");\n"
			+ "		new C();\n"
			+ "    }\n"
			+ "}\n";

	String classC = "package person.model;\n"
			+ "\n"
			+ "public class C {\n"
			+ "    \n"
			+ "	public C() {\n"
			+ "		System.out.println(\"C constructor\");\n"
			+ "        }\n"
			+ "}\n";

	String classHiJBang = "//SOURCES inner/HelloInner.java\n"
			+ "\n"
			+ "public class HiJBang {\n"
			+ "    \n"
			+ "	public HiJBang() {\n"
			+ "		System.out.println(\"HiJBang constructor.\");\n"
			+ "		new HelloInner();\n"
			+ "	}\n"
			+ "}\n";

	String classHelloInner = "public class HelloInner {\n"
			+ "    \n"
			+ "	public HelloInner() {\n"
			+ "		System.out.println(\"HelloInner constructor.\");\n"
			+ "	}\n"
			+ "}\n";

	@Test
	void testFindSourcesInMultipleFilesRecursively() throws IOException {
		Path HiJBangPath = TestScript.createTmpFileWithContent("", "HiJBang.java", classHiJBang);
		Path mainPath = TestScript.createTmpFile("somefolder", "A.java");
		// Add absolute path in //SOURCES
		final String mainClass = "//SOURCES " + HiJBangPath.toString() + "\n" + classA;
		TestScript.writeContentToFile(mainPath, mainClass);
		Path BPath = TestScript.createTmpFileWithContent(mainPath.getParent(), "person", "B.java", classB);
		TestScript.createTmpFileWithContent(BPath.getParent(), "model", "C.java", classC);
		TestScript.createTmpFileWithContent(HiJBangPath.getParent(), "inner", "HelloInner.java",
				classHelloInner);
		String scriptURL = mainPath.toString();
		ResourceRef resourceRef = ResourceRef.forNamedFile(scriptURL, mainPath.toFile());
		ScriptSource script = ScriptSource.prepareScript(resourceRef, null);
		RunContext ctx = new RunContext();
		List<ScriptSource> sources = ctx.getAllSources(script);
		assertEquals(sources.size(), 4);
		TreeSet<String> fileNames = new TreeSet<>();
		for (ScriptSource source : sources) {
			fileNames.add(source.getResourceRef().getFile().getName());
		}
		assertEquals(fileNames.pollFirst(), "B.java");
		assertEquals(fileNames.pollFirst(), "C.java");
		assertEquals(fileNames.pollFirst(), "HelloInner.java");
		assertEquals(fileNames.pollFirst(), "HiJBang.java");
	}

}
