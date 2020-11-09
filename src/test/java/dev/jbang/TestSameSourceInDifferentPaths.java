package dev.jbang;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.jbang.cli.BaseScriptCommand;

/**
 * Both class A and person.B have //SOURCES model/C.java
 */
public class TestSameSourceInDifferentPaths {

	String classA = "//SOURCES person/B.java\n"
			+ "//SOURCES model/C.java\n"
			+ "\n"
			+ "import person.B;\n"
			+ "import model.C;\n"
			+ "\n"
			+ "public class A {\n"
			+ "    \n"
			+ "    public static void main(String args[]) {\n"
			+ "        new B();\n"
			+ "        new C();\n"
			+ "		   new HiJbang();\n"
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

	String classModelC = "package model;\n"
			+ "\n"
			+ "public class C {\n"
			+ "    \n"
			+ "	public C() {\n"
			+ "		System.out.println(\"C in model\");\n"
			+ "        }\n"
			+ "}\n";

	String classPersonModelC = "package person.model;\n"
			+ "\n"
			+ "public class C {\n"
			+ "    \n"
			+ "	public C() {\n"
			+ "		System.out.println(\"C in person.model\");\n"
			+ "        }\n"
			+ "}\n";

	@Test
	void testFindSourcesInMultipleFilesRecursively() throws IOException {
		Path mainPath = TestScript.createTmpFileWithContent("", "Main.java", classA);
		Path BPath = TestScript.createTmpFileWithContent(mainPath.getParent(), "person", "B.java", classB);
		TestScript.createTmpFileWithContent(mainPath.getParent(), "model", "C.java", classModelC);
		TestScript.createTmpFileWithContent(BPath.getParent(), "model", "C.java", classPersonModelC);
		Script script = BaseScriptCommand.prepareScript(mainPath.toString());
		assertTrue(script.collectSourcesRecursively().size() == 3);
	}

}
