package dev.jbang.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.util.Util;

public class TestDeps extends BaseTest {

	@Test
	void testDepsAddToJavaFile(@TempDir Path outputDir) throws IOException {
		// Create a test Java file
		Path testFile = outputDir.resolve("test.java");
		String content = "///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n\nclass Test {\n    public static void main(String[] args) {\n        System.out.println(\"Hello!\");\n    }\n}";
		Files.write(testFile, content.getBytes());

		// Add a dependency
		int result = JBang.getCommandLine().execute("deps", "add", "info.picocli:picocli:4.6.3", testFile.toString());
		assertThat(result).isEqualTo(0);

		// Verify the dependency was added
		String fileContent = Util.readString(testFile);
		assertThat(fileContent).contains("//DEPS info.picocli:picocli:4.6.3");
		assertThat(fileContent).contains("class Test");

		assertThat(fileContent.indexOf("//DEPS info.picocli:picocli:4.6.3"))
			.withFailMessage("Dependency should be added below the //DEPS line and not 'stuck' to class")
			.isGreaterThan(fileContent.indexOf("///"));

		// Verify it is added below the first line and not "stuck" to class
		assertThat(fileContent).doesNotContain("4.6.3\nclass Test {");
	}

	@Test
	void testDepsAddMultipleToJavaFile(@TempDir Path outputDir) throws IOException {
		// Create a test Java file
		Path testFile = outputDir.resolve("test.java");
		String content = "///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n\nclass Test {\n    public static void main(String[] args) {\n        System.out.println(\"Hello!\");\n    }\n}";
		Files.write(testFile, content.getBytes());

		// Add multiple dependencies
		int result = JBang.getCommandLine()
			.execute("deps", "add",
					"info.picocli:picocli:4.6.3",
					"com.fasterxml.jackson.core:jackson-core:2.15.2",
					testFile.toString());
		assertThat(result).isEqualTo(0);

		// Verify the dependencies were added
		String fileContent = Util.readString(testFile);
		assertThat(fileContent).contains("//DEPS info.picocli:picocli:4.6.3");
		assertThat(fileContent).contains("//DEPS com.fasterxml.jackson.core:jackson-core:2.15.2");
	}

	@Test
	void testDepsAddToJbangFile(@TempDir Path outputDir) throws IOException {
		// Create a test .jbang file
		Path testFile = outputDir.resolve("test.jbang");
		String content = "SOURCES test.java\n";
		Files.write(testFile, content.getBytes());

		// Add a dependency
		int result = JBang.getCommandLine().execute("deps", "add", "org.slf4j:slf4j-api:1.7.36", testFile.toString());
		assertThat(result).isEqualTo(0);

		// Verify the dependency was added
		String fileContent = Util.readString(testFile);
		assertThat(fileContent).contains("DEPS org.slf4j:slf4j-api:1.7.36");
		assertThat(fileContent).contains("SOURCES test.java");
	}

	@Test
	void testDepsAddMultipleToJbangFile(@TempDir Path outputDir) throws IOException {
		// Create a test .jbang file
		Path testFile = outputDir.resolve("test.jbang");
		String content = "SOURCES test.java\n";
		Files.write(testFile, content.getBytes());

		// Add multiple dependencies
		int result = JBang.getCommandLine()
			.execute("deps", "add",
					"org.slf4j:slf4j-api:1.7.36",
					"org.slf4j:slf4j-simple:1.7.36",
					testFile.toString());
		assertThat(result).isEqualTo(0);

		// Verify the dependencies were added
		String fileContent = Util.readString(testFile);
		assertThat(fileContent).contains("DEPS org.slf4j:slf4j-api:1.7.36");
		assertThat(fileContent).contains("DEPS org.slf4j:slf4j-simple:1.7.36");
	}

	@Test
	void testDepsAddDuplicateToJavaFile(@TempDir Path outputDir) throws IOException {
		// Create a test Java file with existing dependency
		Path testFile = outputDir.resolve("test.java");
		String content = "///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n//DEPS info.picocli:picocli:4.6.3\n\nclass Test {\n    public static void main(String[] args) {\n        System.out.println(\"Hello!\");\n    }\n}";
		Files.write(testFile, content.getBytes());

		// Try to add the same dependency
		int result = JBang.getCommandLine().execute("deps", "add", "info.picocli:picocli:4.6.3", testFile.toString());
		assertThat(result).isEqualTo(0);

		// Verify the dependency was not duplicated
		String fileContent = Util.readString(testFile);
		long depsCount = fileContent.lines().filter(line -> line.contains("//DEPS info.picocli:picocli:4.6.3")).count();
		assertThat(depsCount).isEqualTo(1L);
	}

	@Test
	void testDepsAddDuplicateToJbangFile(@TempDir Path outputDir) throws IOException {
		// Create a test .jbang file with existing dependency
		Path testFile = outputDir.resolve("test.jbang");
		String content = "SOURCES test.java\nDEPS org.slf4j:slf4j-api:1.7.36\n";
		Files.write(testFile, content.getBytes());

		// Try to add the same dependency
		int result = JBang.getCommandLine().execute("deps", "add", "org.slf4j:slf4j-api:1.7.36", testFile.toString());
		assertThat(result).isEqualTo(0);

		String fileContent = Util.readString(testFile);
		long depsCount = fileContent.lines().filter(line -> line.contains("DEPS org.slf4j:slf4j-api:1.7.36")).count();
		assertThat(depsCount).isEqualTo(1L);
	}

	@Test
	void testDepsAddWithVersionUpdate(@TempDir Path outputDir) throws IOException {
		// Create a test Java file with existing dependency
		Path testFile = outputDir.resolve("test.java");
		String content = "///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n//DEPS info.picocli:picocli:4.6.0\n\nclass Test {\n    public static void main(String[] args) {\n        System.out.println(\"Hello!\");\n    }\n}";
		Files.write(testFile, content.getBytes());

		// Add the same dependency with different version
		int result = JBang.getCommandLine().execute("deps", "add", "info.picocli:picocli:4.6.3", testFile.toString());
		assertThat(result).isEqualTo(0);

		String fileContent = Util.readString(testFile);
		// The new version should be added and old one should be removed
		assertThat(fileContent).doesNotContain("//DEPS info.picocli:picocli:4.6.0");
		assertThat(fileContent).contains("//DEPS info.picocli:picocli:4.6.3");
	}

	@Test
	void testDepsAddToJavaFileWithExistingDeps(@TempDir Path outputDir) throws IOException {
		// Create a test Java file with existing dependencies
		Path testFile = outputDir.resolve("test.java");
		String content = "///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n//DEPS info.picocli:picocli:4.6.3\n//DEPS com.fasterxml.jackson.core:jackson-core:2.15.2\n\nclass Test {\n    public static void main(String[] args) {\n        System.out.println(\"Hello!\");\n    }\n}";
		Files.write(testFile, content.getBytes());

		// Add new dependency
		int result = JBang.getCommandLine().execute("deps", "add", "org.slf4j:slf4j-api:1.7.36", testFile.toString());
		assertThat(result).isEqualTo(0);

		// Verify the new dependency was added
		String fileContent = Util.readString(testFile);
		assertThat(fileContent).contains("//DEPS org.slf4j:slf4j-api:1.7.36");
		assertThat(fileContent).contains("//DEPS info.picocli:picocli:4.6.3");
		assertThat(fileContent).contains("//DEPS com.fasterxml.jackson.core:jackson-core:2.15.2");
	}

	@Test
	void testAddNearExistingDeps(@TempDir Path outputDir) throws IOException {
		// Create a test Java file with existing dependencies
		Path testFile = outputDir.resolve("test.java");
		String content = "/** something else *//\n//JAVA 17+\n//DEPS info.picocli:picocli:4.6.3\n//DEPS com.fasterxml.jackson.core:jackson-core:2.15.2\n//FILES a=b\n\nclass Test {\n    public static void main(String[] args) {\n        System.out.println(\"Hello!\");\n    }\n}";
		Files.write(testFile, content.getBytes());

		// Add new dependency
		int result = JBang.getCommandLine().execute("deps", "add", "org.slf4j:slf4j-api:1.7.36", testFile.toString());
		assertThat(result).isEqualTo(0);

		// Verify the new dependency was added
		String fileContent = Util.readString(testFile);
		assertThat(fileContent).contains("//DEPS org.slf4j:slf4j-api:1.7.36");
		assertThat(fileContent).contains("//DEPS info.picocli:picocli:4.6.3");
		assertThat(fileContent).contains("//DEPS com.fasterxml.jackson.core:jackson-core:2.15.2");

		assertThat(fileContent.indexOf("//DEPS org.slf4j:slf4j-api:1.7.36"))
			.isGreaterThan(fileContent.indexOf("//DEPS info.picocli:picocli:4.6.3"))
			.isGreaterThan(fileContent.indexOf("//JAVA 17+"))
			.isLessThan(fileContent.indexOf("//FILES a=b"));
	}

	@Test
	void testDepsAddToJavaFileWithoutDirectives(@TempDir Path outputDir) throws IOException {
		// Create a test Java file without directives
		Path testFile = outputDir.resolve("test.java");
		String content = "class Test {\n    public static void main(String[] args) {\n        System.out.println(\"Hello!\");\n    }\n}";
		Files.write(testFile, content.getBytes());

		// Add a dependency
		int result = JBang.getCommandLine().execute("deps", "add", "info.picocli:picocli:4.6.3", testFile.toString());
		assertThat(result).isEqualTo(0);

		// Verify the dependency was added at the beginning
		String fileContent = Util.readString(testFile);
		assertThat(fileContent).contains("//DEPS info.picocli:picocli:4.6.3");
		assertThat(fileContent).contains("class Test");
	}

	@Test
	void testDepsAddInvalidDependency(@TempDir Path outputDir) throws IOException {
		// Create a test Java file
		Path testFile = outputDir.resolve("test.java");
		String content = "///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n\nclass Test {\n    public static void main(String[] args) {\n        System.out.println(\"Hello!\");\n    }\n}";
		Files.write(testFile, content.getBytes());

		// Try to add an invalid dependency
		int result = JBang.getCommandLine().execute("deps", "add", "invalid-dependency", testFile.toString());
		assertThat(result).isNotEqualTo(0);
	}

	@Test
	void testDepsAddToNonExistentFile(@TempDir Path outputDir) throws IOException {
		// Try to add dependency to non-existent file
		Path testFile = outputDir.resolve("nonexistent.java");
		int result = JBang.getCommandLine().execute("deps", "add", "info.picocli:picocli:4.6.3", testFile.toString());
		assertThat(result).isNotEqualTo(0);
	}

	@Test
	void testDepsAddToUnsupportedFileType(@TempDir Path outputDir) throws IOException {
		// Create a test file with unsupported extension
		Path testFile = outputDir.resolve("test.txt");
		String content = "Some content";
		Files.write(testFile, content.getBytes());

		// Try to add dependency to unsupported file
		int result = JBang.getCommandLine().execute("deps", "add", "info.picocli:picocli:4.6.3", testFile.toString());
		assertThat(result).isNotEqualTo(0);
	}

	@Test
	void testDepsAddMissingParameters() {
		// Test with missing parameters
		int result = JBang.getCommandLine().execute("deps", "add");
		assertThat(result).isNotEqualTo(0);
	}

	@Test
	void testDepsAddOnlyTargetFile() {
		// Test with only target file (no dependencies)
		int result = JBang.getCommandLine().execute("deps", "add", "test.java");
		assertThat(result).isNotEqualTo(0);
	}
}
