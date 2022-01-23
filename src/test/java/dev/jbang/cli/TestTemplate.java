package dev.jbang.cli;

import static dev.jbang.util.TestUtil.clearSettingsCaches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Settings;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.Template;
import dev.jbang.catalog.TemplateProperty;
import dev.jbang.util.Util;

import picocli.CommandLine;

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
	void init() throws IOException {
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), templates.getBytes());
		Util.setCwd(Files.createDirectory(cwdDir.resolve("test")));
	}

	@Test
	void testReadFromFile() throws IOException {
		clearSettingsCaches();
		Template one = Template.get("one");
		assertThat(one, notNullValue());
		Template two = Template.get("two");
		assertThat(two, notNullValue());
		Template threeWithProperties = Template.get("three-with-properties");
		assertThat(threeWithProperties, notNullValue());
	}

	@Test
	void testAddWithDefaultCatalogFile() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("template", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Template name = Template.get("name");
		assertThat(name.fileRefs, aMapWithSize(1));
		assertThat(name.fileRefs.keySet(), hasItems("{basename}.java"));
		assertThat(name.fileRefs.values(), hasItems("test.java"));
	}

	@Test
	void testAddWithDescriptionInArgs() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("template", "add", "-f", cwd.toString(), "--name=name",
				"-d", "Description of the template", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Template name = Template.get("name");
		assertThat(name.description, is("Description of the template"));
	}

	@Test
	void testAddWithHiddenJBangCatalog() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		Path hiddenJBangPath = Paths.get(cwd.toString(), Settings.JBANG_DOT_DIR);
		Files.createDirectory(hiddenJBangPath);
		Files.createFile(Paths.get(cwd.toString(), Settings.JBANG_DOT_DIR, Catalog.JBANG_CATALOG_JSON));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("template", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		Catalog catalog = Catalog.get(hiddenJBangPath);
		Template name = catalog.templates.get("name");
		assertThat(name.fileRefs, aMapWithSize(1));
		assertThat(name.fileRefs.keySet(), hasItems("{basename}.java"));
		assertThat(name.fileRefs.values(), hasItems(Paths.get("../test.java").toString()));
	}

	@Test
	void testAddPreservesExistingCatalog() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("template", "add", "-f", cwd.toString(), "--name=name",
				testFile.toString());
		Template one = Template.get("one");
		Template name = Template.get("name");
		assertThat(one.fileRefs, aMapWithSize(3));
		assertThat(one.fileRefs.keySet(), hasItems("{basename}.java", "{basename}Test.java", "file2.java"));
		assertThat(one.fileRefs.values(), hasItems("file1_1.java", "file1_1_test.java", "file1_2.java"));
		assertThat(name.fileRefs, aMapWithSize(1));
		assertThat(name.fileRefs.keySet(), hasItems("{basename}.java"));
		assertThat(name.fileRefs.values(), hasItems("test.java"));
	}

	@Test
	void testGetTemplateNone() throws IOException {
		Template template = Template.get("dummy-template!");
		assertThat(template, nullValue());
	}

	@Test
	void testGetTemplateOne() throws IOException {
		Template template = Template.get("one");
		assertThat(template, notNullValue());
		assertThat(template.description, is("Template 1"));
		assertThat(template.fileRefs, aMapWithSize(3));
		assertThat(template.fileRefs.keySet(), hasItems("{basename}.java", "{basename}Test.java", "file2.java"));
		assertThat(template.fileRefs.values(), hasItems("file1_1.java", "file1_1_test.java", "file1_2.java"));
	}

	@Test
	void testGetTemplateTwo() throws IOException {
		Template template = Template.get("two");
		assertThat(template, notNullValue());
		assertThat(template.description, is("Template 2"));
		assertThat(template.fileRefs, aMapWithSize(2));
		assertThat(template.fileRefs.keySet(), hasItems("src/{filename}", "src/file2.java"));
		assertThat(template.fileRefs.values(), hasItems("tpl2/file2_1.java", "tpl2/file2_2.java"));
	}

	@Test
	void testGetTemplateThreeWithProperties() throws IOException {
		Template template = Template.get("three-with-properties");
		assertThat(template, notNullValue());
		assertThat(template.description, is("Template 3"));
		assertThat(template.fileRefs, aMapWithSize(2));
		assertThat(template.fileRefs.keySet(), hasItems("src/{filename}", "src/file2.java"));
		assertThat(template.fileRefs.values(), hasItems("tpl2/file2_1.java", "tpl2/file2_2.java"));
		assertThat(template.properties.entrySet(), hasItems(
				new AbstractMap.SimpleEntry<>("test-key", new TemplateProperty(null, null)),
				new AbstractMap.SimpleEntry<>("test-key-with-description",
						new TemplateProperty("This is a test description", null)),
				new AbstractMap.SimpleEntry<>("test-key-with-description-and-default-value",
						new TemplateProperty("This is a test description with default value", "2.11")),
				new AbstractMap.SimpleEntry<>("test-key-with-default-value", new TemplateProperty(null, "3.12"))));
	}

	@Test
	void testAddFailAbsolute() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = Files.createFile(cwd.resolve("file1.java"));
		int result = JBang	.getCommandLine()
							.execute("template", "add", "-f", cwd.toString(), "--name=name",
									"/test=" + testFile.toString());
		assertThat(result, is(2));
	}

	@Test
	void testAddFailParent() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = Files.createFile(cwd.resolve("file1.java"));
		int result = JBang	.getCommandLine()
							.execute("template", "add", "-f", cwd.toString(), "--name=name",
									"test/../..=" + testFile.toString());
		assertThat(result, is(2));
	}

	@Test
	void testAddFailNoTargetPattern() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = Files.createFile(cwd.resolve("file1.java"));
		int result = JBang	.getCommandLine()
							.execute("template", "add", "-f", cwd.toString(), "--name=name",
									"test=" + testFile.toString());
		assertThat(result, is(2));
	}

	@Test
	void testAddWithSingleProperty() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("template", "add", "-f", cwd.toString(), "--name=template-with-single-property",
				"-d", "Description of the template", "-P", "new-test-key", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Template name = Template.get("template-with-single-property");
		assertThat(name.properties.entrySet(), hasItems(
				new AbstractMap.SimpleEntry<>("new-test-key", new TemplateProperty(null, null))));
	}

	@Test
	void testAddWithSingleComplexProperty() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("template", "add", "-f", cwd.toString(),
				"--name=template-with-single-complex-property",
				"-d", "Description of the template", "-P",
				"new-test-key:This is a description for the property key:3.14", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Template name = Template.get("template-with-single-complex-property");
		assertThat(name.properties.entrySet(), hasItems(
				new AbstractMap.SimpleEntry<>("new-test-key",
						new TemplateProperty("This is a description for the property key", "3.14"))));
	}

	@Test
	void testAddWithMultipleComplexProperties() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		JBang jbang = new JBang();
		new CommandLine(jbang).execute("template", "add", "-f", cwd.toString(),
				"--name=template-with-complex-properties",
				"-d", "Description of the template", "-P",
				"new-test-key:This is a description for the property key:3.14", "--property",
				"second-test-key:This is another description for the second property key:Non-Blocker",
				testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Template name = Template.get("template-with-complex-properties");
		assertThat(name.properties.entrySet(), hasItems(
				new AbstractMap.SimpleEntry<>("new-test-key",
						new TemplateProperty("This is a description for the property key", "3.14")),
				new AbstractMap.SimpleEntry<>("second-test-key", new TemplateProperty(
						"This is another description for the second property key", "Non-Blocker"))));
	}
}
