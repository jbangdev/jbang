package dev.jbang.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

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
		Path mainPath = TestSource.createTmpFileWithContent("", "Main.java", classMain);
		TestSource.createTmpFileWithContent("", "A.java", classA);
		TestSource.createTmpFileWithContent("", "B.java", classB);
		String scriptURL = mainPath.toString();
		Source source = Source.forResource(scriptURL, null);
		Project prj = Project.builder().build(source);
		assertEquals(3, prj.getMainSourceSet().getSources().size());
	}

}
