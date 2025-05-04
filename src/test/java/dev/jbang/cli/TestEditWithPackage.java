package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.io.FileMatchers.aReadableFile;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.source.TestSource;

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
		Path mainPath = TestSource.createTmpFileWithContent("", "A.java", classA);
		Path BPath = TestSource.createTmpFileWithContent(mainPath.getParent(), "person", "B.java", classB);
		Path CPath = TestSource.createTmpFileWithContent(BPath.getParent(), "model", "C.java", classC);
		assertTrue(mainPath.toFile().exists());
		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(mainPath.toString());
		Path project = new Edit().createProjectForLinkedEdit(prj, Collections.emptyList(), false);
		assertTrue(Files.exists(project.resolve("src/A.java")));
		assertTrue(Files.exists(project.resolve("src/person/B.java")));
		assertTrue(Files.exists(project.resolve("src/person/model/C.java")));

		Path javaC = project.resolve("src/person/model/C.java");

		// first check for symlink. in some cases on windows (non admin privileg)
		// symlink cannot be created, as fallback a hardlink will be created.
		assertTrue(Files.isSymbolicLink(javaC) || Files.exists(javaC));
		assertTrue(Files.isSameFile(javaC, CPath));

		Arrays.asList("A.java", "person/B.java", "person/model/C.java")
			.forEach(f -> {
				Path java = project.resolve("src/" + f);

				assertThat(f + " not found", java.toFile(), aReadableFile());
			});
	}

}
