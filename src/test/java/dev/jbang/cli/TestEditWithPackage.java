package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.io.FileMatchers.aReadableFile;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.DecoratedSource;
import dev.jbang.Source;
import dev.jbang.TestScript;

public class TestEditWithPackage extends BaseTest {

	final String classA = "//SOURCES person/B.java\n"
			+ "\n"
			+ "import person.B;\n"
			+ "\n"
			+ "public class A {\n"
			+ "    \n"
			+ "    public static void main(String args[]) {\n"
			+ "        new B();\n"
			+ "    }\n"
			+ "}\n";

	final String classB = "package           person          ;\n"
			+ "\n"
			+ "//SOURCES model/C.java\n"
			+ "\n"
			+ "import person.model.C;\n"
			+ "\n"
			+ "public class B {\n"
			+ "    \n"
			+ "	public B() {\n"
			+ "        new C();\n"
			+ "    }\n"
			+ "}\n";

	final String classC = "            package person.model;\n"
			+ "\n"
			+ "public class C {\n"
			+ "	public C() {}\n"
			+ "}\n";

	@Test
	void testEditPackage() throws IOException {
		Path mainPath = TestScript.createTmpFileWithContent("", "A.java", classA);
		Path BPath = TestScript.createTmpFileWithContent(mainPath.getParent(), "person", "B.java", classB);
		Path CPath = TestScript.createTmpFileWithContent(BPath.getParent(), "model", "C.java", classC);
		assertTrue(mainPath.toFile().exists());
		DecoratedSource xrunit = Source.forResource(mainPath.toString());
		File project = new Edit().createProjectForEdit(xrunit, false);
		assertTrue(new File(project, "src/A.java").exists());
		assertTrue(new File(project, "src/person/B.java").exists());
		assertTrue(new File(project, "src/person/model/C.java").exists());

		File javaC = new File(project, "src/person/model/C.java");

		// first check for symlink. in some cases on windows (non admin privileg)
		// symlink cannot be created, as fallback a hardlink will be created.
		assertTrue(Files.isSymbolicLink(javaC.toPath()) || javaC.exists());
		assertTrue(Files.isSameFile(javaC.toPath(), CPath));

		Arrays	.asList("A.java", "person/B.java", "person/model/C.java")
				.forEach(f -> {
					File java = new File(project, "src/" + f);

					assertThat(f + " not found", java, aReadableFile());
				});
	}

}
