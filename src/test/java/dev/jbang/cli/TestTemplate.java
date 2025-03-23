package dev.jbang.cli;

import static dev.jbang.util.TestUtil.clearSettingsCaches;
import static dev.jbang.util.Util.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Settings;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.Template;
import dev.jbang.catalog.TemplateProperty;
import dev.jbang.util.Util;

public class TestTemplate extends BaseTest {

	static final String templates = "{\n" +
			"  \"templates\": {\n" +
			"    \"one\": {\n" +
			"      \"description\": \"Template 1\",\n" +
			"      \"file-refs\": {\n" +
			"        \"{basename}.java\": \"file1_1.java\",\n" +
			"        \"{basename}Test.java\": \"file1_1_test.java\",\n" +
			"        \"file2.java\": \"file1_2.java\"\n" +
			"      }\n" +
			"    },\n" +
			"    \"two\": {\n" +
			"      \"description\": \"Template 2\",\n" +
			"      \"file-refs\": {\n" +
			"        \"src/{filename}\": \"tpl2/file2_1.java\",\n" +
			"        \"src/file2.java\": \"tpl2/file2_2.java\"\n" +
			"      }\n" +
			"    },\n" +
			"    \"three-with-properties\": {\n" +
			"      \"description\": \"Template 3\",\n" +
			"      \"file-refs\": {\n" +
			"        \"src/{filename}\": \"tpl2/file2_1.java\",\n" +
			"        \"src/file2.java\": \"tpl2/file2_2.java\"\n" +
			"      },\n" +
			"     \"properties\": {\n" +
			"        \"test-key\": {\n" +
			"        },\n" +
			"        \"test-key-with-description\": {\n" +
			"          \"description\": \"This is a test description\"\n" +
			"        },\n" +
			"        \"test-key-with-description-and-default-value\": {\n" +
			"          \"description\": \"This is a test description with default value\",\n" +
			"          \"default\": \"2.11\"\n" +
			"        },\n" +
			"        \"test-key-with-default-value\": {\n" +
			"          \"default\": \"3.12\"\n" +
			"        }\n" +
			"      }\n" +
			"    }\n" +
			"  }\n" +
			"}";

	@BeforeEach
	void initEach() throws IOException {
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), templates.getBytes());
		Util.setCwd(Files.createDirectory(cwdDir.resolve("test")));
	}

	@Test
	void testReadFromFile() throws IOException {
		clearSettingsCaches();
		Template one = Template.get("one");
		assertThat(one).isNotNull();
		Template two = Template.get("two");
		assertThat(two).isNotNull();
		Template threeWithProperties = Template.get("three-with-properties");
		assertThat(threeWithProperties).isNotNull();
	}

	@Test
	void testAddWithDefaultCatalogFile() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		JBang.getCommandLine().execute("template", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(true);
		Template name = Template.get("name");
		assertThat(name.fileRefs).hasSize(1);
		assertThat(name.fileRefs.keySet()).contains("{basename}.java");
		assertThat(name.fileRefs.values()).contains("test.java");
	}

	@Test
	void testAddWithDescriptionInArgs() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		JBang	.getCommandLine()
				.execute("template", "add", "-f", cwd.toString(), "--name=name",
						"--description", "Description of the template", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(true);
		Template name = Template.get("name");
		assertThat(name.description).isEqualTo("Description of the template");
	}

	@Test
	void testAddWithHiddenJBangCatalog() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		Path hiddenJBangPath = Paths.get(cwd.toString(), Settings.JBANG_DOT_DIR);
		Files.createDirectory(hiddenJBangPath);
		Files.createFile(Paths.get(cwd.toString(), Settings.JBANG_DOT_DIR, Catalog.JBANG_CATALOG_JSON));
		JBang.getCommandLine().execute("template", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		Catalog catalog = Catalog.get(hiddenJBangPath);
		Template name = catalog.templates.get("name");
		assertThat(name.fileRefs).hasSize(1);
		assertThat(name.fileRefs.keySet()).contains("{basename}.java");
		assertThat(name.fileRefs.values()).contains(Paths.get("../test.java").toString());
	}

	@Test
	void testAddPreservesExistingCatalog() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		JBang	.getCommandLine()
				.execute("template", "add", "-f", cwd.toString(), "--name=name",
						testFile.toString());
		Template one = Template.get("one");
		Template name = Template.get("name");
		assertThat(one.fileRefs).hasSize(3);
		assertThat(one.fileRefs.keySet()).contains("{basename}.java", "{basename}Test.java", "file2.java");
		assertThat(one.fileRefs.values()).contains("file1_1.java", "file1_1_test.java", "file1_2.java");
		assertThat(name.fileRefs).hasSize(1);
		assertThat(name.fileRefs.keySet()).contains("{basename}.java");
		assertThat(name.fileRefs.values()).contains("test.java");
	}

	@Test
	void testAddExisting() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		Path testFile2 = cwd.resolve("test2.java");
		Files.write(testFile2, "// Test file 2".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		int exitCode = JBang.getCommandLine()
							.execute("template", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(exitCode).isEqualTo(BaseCommand.EXIT_OK);
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(true);
		Template name = Template.get("name");
		assertThat(name.fileRefs).hasSize(1);
		assertThat(name.fileRefs.keySet()).contains("{basename}.java");
		assertThat(name.fileRefs.values()).contains("test.java");
		exitCode = JBang.getCommandLine()
						.execute("template", "add", "-f", cwd.toString(), "--name=name", testFile2.toString());
		assertThat(exitCode).isEqualTo(BaseCommand.EXIT_INVALID_INPUT);
		exitCode = JBang.getCommandLine()
						.execute("template", "add", "-f", cwd.toString(), "--name=name", "--force",
								testFile2.toString());
		assertThat(exitCode).isEqualTo(BaseCommand.EXIT_OK);
		name = Template.get("name");
		assertThat(name.fileRefs).hasSize(1);
		assertThat(name.fileRefs.keySet()).contains("{basename}.java");
		assertThat(name.fileRefs.values()).contains("test2.java");
	}

	@Test
	void testGetTemplateNone() throws IOException {
		Template template = Template.get("dummy-template!");
		assertThat(template).isNull();
	}

	@Test
	void testGetTemplateOne() throws IOException {
		Template template = Template.get("one");
		assertThat(template).isNotNull();
		assertThat(template.description).isEqualTo("Template 1");
		assertThat(template.fileRefs).hasSize(3);
		assertThat(template.fileRefs.keySet()).contains("{basename}.java", "{basename}Test.java", "file2.java");
		assertThat(template.fileRefs.values()).contains("file1_1.java", "file1_1_test.java", "file1_2.java");
	}

	@Test
	void testGetTemplateTwo() throws IOException {
		Template template = Template.get("two");
		assertThat(template).isNotNull();
		assertThat(template.description).isEqualTo("Template 2");
		assertThat(template.fileRefs).hasSize(2);
		assertThat(template.fileRefs.keySet()).contains("src/{filename}", "src/file2.java");
		assertThat(template.fileRefs.values()).contains("tpl2/file2_1.java", "tpl2/file2_2.java");
	}

	@Test
	void testGetTemplateThreeWithProperties() throws IOException {
		Template template = Template.get("three-with-properties");
		assertThat(template).isNotNull();
		assertThat(template.description).isEqualTo("Template 3");
		assertThat(template.fileRefs).hasSize(2);
		assertThat(template.fileRefs.keySet()).contains("src/{filename}", "src/file2.java");
		assertThat(template.fileRefs.values()).contains("tpl2/file2_1.java", "tpl2/file2_2.java");
		assertThat(template.properties.entrySet()).contains(entry("test-key", new TemplateProperty(null, null)), entry("test-key-with-description",
				new TemplateProperty("This is a test description", null)), entry("test-key-with-description-and-default-value",
				new TemplateProperty("This is a test description with default value", "2.11")), entry("test-key-with-default-value", new TemplateProperty(null, "3.12")));
	}

	@Test
	void testAddFailAbsolute() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = Files.createFile(cwd.resolve("file1.java"));
		int result = JBang	.getCommandLine()
							.execute("template", "add", "-f", cwd.toString(), "--name=name",
									"/test=" + testFile.toString());
		assertThat(result).isEqualTo(2);
	}

	@Test
	void testAddFailParent() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = Files.createFile(cwd.resolve("file1.java"));
		int result = JBang	.getCommandLine()
							.execute("template", "add", "-f", cwd.toString(), "--name=name",
									"test/../..=" + testFile.toString());
		assertThat(result).isEqualTo(2);
	}

	@Test
	void testAddFailNoTargetPattern() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = Files.createFile(cwd.resolve("file1.java"));
		int result = JBang	.getCommandLine()
							.execute("template", "add", "-f", cwd.toString(), "--name=name",
									"test=" + testFile.toString());
		assertThat(result).isEqualTo(2);
	}

	@Test
	void testAddWithSingleProperty() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		JBang	.getCommandLine()
				.execute("template", "add", "-f", cwd.toString(), "--name=template-with-single-property",
						"--description", "Description of the template", "-P", "new-test-key", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(true);
		Template name = Template.get("template-with-single-property");
		assertThat(name.properties.entrySet()).contains(entry("new-test-key", new TemplateProperty(null, null)));
	}

	@Test
	void testAddWithSingleComplexProperty() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		JBang	.getCommandLine()
				.execute("template", "add", "-f", cwd.toString(),
						"--name=template-with-single-complex-property",
						"--description", "Description of the template", "-P",
						"new-test-key:This is a description for the property key:3.14", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(true);
		Template name = Template.get("template-with-single-complex-property");
		assertThat(name.properties.entrySet()).contains(entry("new-test-key",
				new TemplateProperty("This is a description for the property key", "3.14")));
	}

	@Test
	void testAddWithMultipleComplexProperties() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(false);
		JBang	.getCommandLine()
				.execute("template", "add", "-f", cwd.toString(),
						"--name=template-with-complex-properties",
						"--description", "Description of the template", "-P",
						"new-test-key:This is a description for the property key:3.14", "--property",
						"second-test-key:This is another description for the second property key:Non-Blocker",
						testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON))).isEqualTo(true);
		Template name = Template.get("template-with-complex-properties");
		assertThat(name.properties.entrySet()).contains(entry("new-test-key",
				new TemplateProperty("This is a description for the property key", "3.14")), entry("second-test-key", new TemplateProperty(
				"This is another description for the second property key", "Non-Blocker")));
	}
}
