package dev.jbang;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class TestSourcesMutualDependency extends BaseTest {

	String classMain = "//SOURCES A.java\n"
			+ "\n"
			+ "public class Main {    \n"
			+ "    public static void main(String args[]) {\n"
			+ "        new A();\n"
			+ "    }\n"
			+ "}\n";

	String classA = "//SOURCES B.java\n"
			+ "\n"
			+ "public class A {\n"
			+ "    public A() { new B(); }\n"
			+ "}\n";

	String classB = "//SOURCES A.java\n"
			+ "\n"
			+ "public class B {\n"
			+ "	public B() {\n"
			+ "		System.out.println(\"B constructor.\");\n"
			+ "	}\n"
			+ "}\n";

	@Test
	void testFindSourcesInMultipleFilesRecursively() throws IOException {
		Path mainPath = TestScript.createTmpFileWithContent("", "Main.java", classMain);
		TestScript.createTmpFileWithContent("", "A.java", classA);
		TestScript.createTmpFileWithContent("", "B.java", classB);
		String scriptURL = mainPath.toString();
		Script script = Script.prepareScript(scriptURL);
		assertTrue(script.collectAllSources().size() == 2);
	}

}
