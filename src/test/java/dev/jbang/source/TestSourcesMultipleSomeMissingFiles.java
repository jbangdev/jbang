package dev.jbang.source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.Assume;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.resources.ResourceRef;

class TestSourcesMultipleSomeMissingFiles extends BaseTest {

	String classA = "//SOURCES **/*.java\n"
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

	/**
	 * Tests if a //SOURCES <pattern with *> matches a symbolic link file which
	 * targets does not exist the file is just ignored
	 * 
	 * @throws IOException
	 */
	@Test
	void testFindSourcesInMultipleFilesSymbolicLinkMissing() throws IOException {
		Path HiJBangPath = TestSource.createTmpFileWithContent("", "HiJBang.java", classHiJBang);
		Path mainPath = TestSource.createTmpFile("somefolder", "A.java");
		// Add absolute path in //SOURCES
		final String mainClass = "//SOURCES " + HiJBangPath.toString() + "\n" + classA;
		TestSource.writeContentToFile(mainPath, mainClass);
		Path BPath = TestSource.createTmpFileWithContent(mainPath.getParent(), "person", "B.java", classB);
		Path target = TestSource.createTmpFileWithContent(BPath.getParent(), "model", "C.java", classC);
		Path t = target.getParent().resolve("C2.java");
		t.toFile().delete();

		target = Files.copy(target, target.getParent().resolve("C3.java"), StandardCopyOption.REPLACE_EXISTING);
		try {
			Files.createSymbolicLink(t, target);
		} catch (FileSystemException ex) {
			Assume.assumeThat("Cannot check symbolic link. Permissions should be enabled first. " +
					"See https://github.com/jbangdev/jbang/blob/main/CONTRIBUTING.adoc#building-on-windows-specifics",
					ex.getReason(), not(containsString("A required privilege is not held by the client")));
		}
		Files.delete(target);

		TestSource.createTmpFileWithContent(HiJBangPath.getParent(), "inner", "HelloInner.java",
				classHelloInner);
		String scriptURL = mainPath.toString();
		ResourceRef resourceRef = ResourceRef.forResolvedResource(scriptURL, mainPath);
		Source script = Source.forResourceRef(resourceRef, null);
		Project prj = Project.builder().build(script);
		List<ResourceRef> sources = prj.getMainSourceSet().getSources();
		assertEquals(5, sources.size());
		TreeSet<String> fileNames = new TreeSet<>();
		for (ResourceRef source : sources) {
			fileNames.add(source.getFile().getFileName().toString());
		}
		assertEquals(fileNames.pollFirst(), "A.java");
		assertEquals(fileNames.pollFirst(), "B.java");
		assertEquals(fileNames.pollFirst(), "C.java");
		assertEquals(fileNames.pollFirst(), "HelloInner.java");
		assertEquals(fileNames.pollFirst(), "HiJBang.java");
	}

	/**
	 * tests if //SOURCE <specific path> points to file that does not exist then we
	 * fail.
	 * 
	 * @throws IOException
	 */
	@Test
	void testFindSourcesInMultipleFilesSimpleFileMissing() throws IOException {
		Path HiJBangPath = TestSource.createTmpFileWithContent("", "HiJBang.java", classHiJBang);
		Path mainPath = TestSource.createTmpFile("somefolder", "A.java");
		// Add absolute path in //SOURCES
		final String mainClass = "//SOURCES " + HiJBangPath.toString() + "\n" + classA;
		TestSource.writeContentToFile(mainPath, mainClass);
		Path BPath = TestSource.createTmpFileWithContent(mainPath.getParent(), "person", "B.java", classB);
		Path target = TestSource.createTmpFileWithContent(BPath.getParent(), "model", "C.java", classC);
		Files.delete(target);

		TestSource.createTmpFileWithContent(HiJBangPath.getParent(), "inner", "HelloInner.java",
				classHelloInner);
		String scriptURL = mainPath.toString();
		ResourceRef resourceRef = ResourceRef.forResolvedResource(scriptURL, mainPath);
		Source source = Source.forResourceRef(resourceRef, null);
		Project prj = Project.builder().build(source);
		assertThat(prj.getMainSourceSet().getSources(), iterableWithSize(5));
		assertThat(prj.getMainSourceSet()
			.getSources()
			.stream()
			.filter(ref -> ref instanceof ResourceRef.UnresolvableResourceRef)
			.collect(Collectors.toList()), iterableWithSize(1));
	}

}
