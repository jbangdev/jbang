package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;

public class TestAliasVersionReplacement extends BaseTest {

	@BeforeEach
	void initCatalog() throws IOException {
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"gav-with-property\": {\n" +
				"      \"script-ref\": \"com.example:artifact:${jbang.app.version:1.0.0}\"\n" +
				"    },\n" +
				"    \"url-with-property\": {\n" +
				"      \"script-ref\": \"https://github.com/user/repo/blob/${jbang.app.version:main}/script.java\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());
	}

	@Test
	void testPropertyReplacementInGav() throws IOException {
		Alias alias = Alias.get("gav-with-property:2.0.0");

		assertThat(alias, notNullValue());
		assertThat(alias.requestedVersion, equalTo("2.0.0"));

		String resolved = alias.resolve();
		assertThat(resolved, equalTo("com.example:artifact:2.0.0"));
	}

	@Test
	void testPropertyReplacementInUrl() throws IOException {
		Alias alias = Alias.get("url-with-property:v1.5.0");

		assertThat(alias, notNullValue());
		assertThat(alias.requestedVersion, equalTo("v1.5.0"));

		String resolved = alias.resolve();
		assertThat(resolved, equalTo("https://github.com/user/repo/blob/v1.5.0/script.java"));
	}

	@Test
	void testPropertyDefaultUsedWhenNoVersionProvided() throws IOException {
		Alias alias = Alias.get("gav-with-property");

		assertThat(alias, notNullValue());
		assertThat(alias.requestedVersion, nullValue());

		String resolved = alias.resolve();
		assertThat(resolved, equalTo("com.example:artifact:1.0.0"));
	}

	@Test
	void testGavVersionReplacement() throws IOException {
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"com.example:artifact:1.0.0\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Alias alias = Alias.get("tool:2.0.0");
		String resolved = alias.resolve();

		assertThat(resolved, equalTo("com.example:artifact:2.0.0"));
	}

	@Test
	void testGavWithClassifierVersionReplacement() throws IOException {
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"com.example:artifact:1.0.0:classifier\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Alias alias = Alias.get("tool:2.0.0");
		String resolved = alias.resolve();

		assertThat(resolved, equalTo("com.example:artifact:2.0.0:classifier"));
	}

	@Test
	void testGavWithClassifierAndTypeVersionReplacement() throws IOException {
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"com.example:artifact:1.0.0:classifier@jar\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Alias alias = Alias.get("tool:2.0.0");
		String resolved = alias.resolve();

		assertThat(resolved, equalTo("com.example:artifact:2.0.0:classifier@jar"));
	}

	@Test
	void testGavWithoutVersionAppends() throws IOException {
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"com.example:artifact\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Alias alias = Alias.get("tool:2.0.0");
		String resolved = alias.resolve();

		assertThat(resolved, equalTo("com.example:artifact:2.0.0"));
	}

	@Test
	void testGitHubBlobVersionReplacement() throws IOException {
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"https://github.com/org/repo/blob/main/script.java\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Alias alias = Alias.get("tool:v1.0.0");
		String resolved = alias.resolve();

		assertThat(resolved, equalTo("https://github.com/org/repo/blob/v1.0.0/script.java"));
	}

	@Test
	void testGitHubRawVersionReplacement() throws IOException {
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"https://raw.githubusercontent.com/org/repo/main/script.java\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Alias alias = Alias.get("tool:1.0.0");
		String resolved = alias.resolve();

		assertThat(resolved, equalTo("https://raw.githubusercontent.com/org/repo/1.0.0/script.java"));
	}

	@Test
	void testGitLabBlobVersionReplacement() throws IOException {
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"https://gitlab.com/org/repo/-/blob/develop/script.java\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Alias alias = Alias.get("tool:v1.0");
		String resolved = alias.resolve();

		assertThat(resolved, equalTo("https://gitlab.com/org/repo/-/blob/v1.0/script.java"));
	}

	@Test
	void testGitLabRawVersionReplacement() throws IOException {
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"https://gitlab.com/org/repo/-/raw/main/script.java\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Alias alias = Alias.get("tool:1.0.0");
		String resolved = alias.resolve();

		assertThat(resolved, equalTo("https://gitlab.com/org/repo/-/raw/1.0.0/script.java"));
	}

	@Test
	void testBitbucketSrcVersionReplacement() throws IOException {
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"https://bitbucket.org/org/repo/src/master/script.java\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Alias alias = Alias.get("tool:1.0");
		String resolved = alias.resolve();

		assertThat(resolved, equalTo("https://bitbucket.org/org/repo/src/1.0/script.java"));
	}

	@Test
	void testBitbucketRawVersionReplacement() throws IOException {
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"https://bitbucket.org/org/repo/raw/master/script.java\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Alias alias = Alias.get("tool:1.0");
		String resolved = alias.resolve();

		assertThat(resolved, equalTo("https://bitbucket.org/org/repo/raw/1.0/script.java"));
	}

	@Test
	void testCatalogRefVersionReplacement() throws IOException {
		// Test that catalog references with path segments get version replaced
		// Format: alias@org/repo/ref → alias@org/repo/version
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"local-tool\": {\n" +
				"      \"script-ref\": \"com.example:artifact:1.0.0\"\n" +
				"    },\n" +
				"    \"wrapper\": {\n" +
				"      \"script-ref\": \"local-tool\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		// Test the wrapper alias with version
		Alias alias = Alias.get("wrapper:2.0.0");

		assertThat(alias, notNullValue());
		assertThat(alias.requestedVersion, equalTo("2.0.0"));

		// The scriptRef should resolve through to the GAV with version replaced
		String resolved = alias.resolve();
		assertThat(resolved, equalTo("com.example:artifact:2.0.0"));
	}
}
