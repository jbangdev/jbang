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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.catalog.Template;
import dev.jbang.util.Util;

import picocli.CommandLine;

public class TestTemplate extends BaseTest {

	static final String templates = "{\n" +
			"  \"templates\": {\n" +
			"    \"one\": {\n" +
			"      \"description\": \"Template 1\",\n" +
			"      \"file-refs\": {\n" +
			"        \"{basename}.java\": \"file1_1.java\",\n" +
			"        \"file2.java\": \"file1_2.java\"\n" +
			"      }\n" +
			"    },\n" +
			"    \"two\": {\n" +
			"      \"description\": \"Template 2\",\n" +
			"      \"file-refs\": {\n" +
			"        \"src/{filename}\": \"tpl2/file2_1.java\",\n" +
			"        \"src/file2.java\": \"tpl2/file2_2.java\"\n" +
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
	}

	@Test
	void testAddWithDefaultCatalogFile() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		Jbang jbang = new Jbang();
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
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("template", "add", "-f", cwd.toString(), "--name=name",
				"-d", "Description of the template", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)),
				is(true));
		Template name = Template.get("name");
		assertThat(name.description, is("Description of the template"));
	}

	@Test
	void testAddWithHiddenJbangCatalog() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = cwd.resolve("test.java");
		Files.write(testFile, "// Test file".getBytes());
		Path hiddenJbangPath = Paths.get(cwd.toString(), CatalogUtil.JBANG_DOT_DIR);
		Files.createDirectory(hiddenJbangPath);
		Files.createFile(Paths.get(cwd.toString(), CatalogUtil.JBANG_DOT_DIR, Catalog.JBANG_CATALOG_JSON));
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("template", "add", "-f", cwd.toString(), "--name=name", testFile.toString());
		assertThat(Files.isRegularFile(Paths.get(cwd.toString(), Catalog.JBANG_CATALOG_JSON)), is(false));
		Catalog catalog = Catalog.get(hiddenJbangPath);
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
		Jbang jbang = new Jbang();
		new CommandLine(jbang).execute("template", "add", "-f", cwd.toString(), "--name=name",
				testFile.toString());
		Template one = Template.get("one");
		Template name = Template.get("name");
		assertThat(one.fileRefs, aMapWithSize(2));
		assertThat(one.fileRefs.keySet(), hasItems("{basename}.java", "file2.java"));
		assertThat(one.fileRefs.values(), hasItems("file1_1.java", "file1_2.java"));
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
		assertThat(template.fileRefs, aMapWithSize(2));
		assertThat(template.fileRefs.keySet(), hasItems("{basename}.java", "file2.java"));
		assertThat(template.fileRefs.values(), hasItems("file1_1.java", "file1_2.java"));
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
	void testAddFailAbsolute() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = Files.createFile(cwd.resolve("file1.java"));
		int result = Jbang	.getCommandLine()
							.execute("template", "add", "-f", cwd.toString(), "--name=name",
									"/test=" + testFile.toString());
		assertThat(result, is(2));
	}

	@Test
	void testAddFailParent() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = Files.createFile(cwd.resolve("file1.java"));
		int result = Jbang	.getCommandLine()
							.execute("template", "add", "-f", cwd.toString(), "--name=name",
									"test/../..=" + testFile.toString());
		assertThat(result, is(2));
	}

	@Test
	void testAddFailNoTargetPattern() throws IOException {
		Path cwd = Util.getCwd();
		Path testFile = Files.createFile(cwd.resolve("file1.java"));
		int result = Jbang	.getCommandLine()
							.execute("template", "add", "-f", cwd.toString(), "--name=name",
									"test=" + testFile.toString());
		assertThat(result, is(2));
	}
}
