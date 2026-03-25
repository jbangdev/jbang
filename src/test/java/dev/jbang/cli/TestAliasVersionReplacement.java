package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.catalog.Alias;
import dev.jbang.catalog.AliasRef;
import dev.jbang.catalog.AliasVersionPinner;
import dev.jbang.catalog.Catalog;
import dev.jbang.util.PropertiesValueResolver;

public class TestAliasVersionReplacement extends BaseTest {

	private static String resolveWithVersion(Alias alias, AliasRef ref) {
		String raw = alias.scriptRef;
		if (ref.requestedVersion != null) {
			raw = AliasVersionPinner.applyVersion(raw, ref.requestedVersion);
		} else {
			raw = PropertiesValueResolver.replaceProperties(raw);
		}
		return alias.resolve(raw);
	}

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
		AliasRef ref = AliasRef.parse("gav-with-property:2.0.0");
		Alias alias = Alias.get(ref.alias);

		assertThat(alias, notNullValue());

		String resolved = resolveWithVersion(alias, ref);
		assertThat(resolved, equalTo("com.example:artifact:2.0.0"));
	}

	@Test
	void testPropertyReplacementInUrl() throws IOException {
		AliasRef ref = AliasRef.parse("url-with-property:v1.5.0");
		Alias alias = Alias.get(ref.alias);

		assertThat(alias, notNullValue());

		String resolved = resolveWithVersion(alias, ref);
		assertThat(resolved, equalTo("https://github.com/user/repo/blob/v1.5.0/script.java"));
	}

	@Test
	void testPropertyDefaultUsedWhenNoVersionProvided() throws IOException {
		AliasRef ref = AliasRef.parse("gav-with-property");
		Alias alias = Alias.get(ref.alias);

		assertThat(alias, notNullValue());

		String resolved = resolveWithVersion(alias, ref);
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

		AliasRef ref = AliasRef.parse("tool:2.0.0");
		Alias alias = Alias.get(ref.alias);
		String resolved = resolveWithVersion(alias, ref);

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

		AliasRef ref = AliasRef.parse("tool:2.0.0");
		Alias alias = Alias.get(ref.alias);
		String resolved = resolveWithVersion(alias, ref);

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

		AliasRef ref = AliasRef.parse("tool:2.0.0");
		Alias alias = Alias.get(ref.alias);
		String resolved = resolveWithVersion(alias, ref);

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

		AliasRef ref = AliasRef.parse("tool:2.0.0");
		Alias alias = Alias.get(ref.alias);
		String resolved = resolveWithVersion(alias, ref);

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

		AliasRef ref = AliasRef.parse("tool:v1.0.0");
		Alias alias = Alias.get(ref.alias);
		String resolved = resolveWithVersion(alias, ref);

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

		AliasRef ref = AliasRef.parse("tool:1.0.0");
		Alias alias = Alias.get(ref.alias);
		String resolved = resolveWithVersion(alias, ref);

		assertThat(resolved, equalTo("https://raw.githubusercontent.com/org/repo/1.0.0/script.java"));
	}

	@Test
	void testGitHubReleaseDownloadVersionReplacement() throws IOException {
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"https://github.com/jbangdev/jbang/releases/download/v0.137.0/checksums_sha256.txt\"\n"
				+
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		AliasRef ref = AliasRef.parse("tool:v0.138.0");
		Alias alias = Alias.get(ref.alias);
		String resolved = resolveWithVersion(alias, ref);

		assertThat(resolved,
				equalTo("https://github.com/jbangdev/jbang/releases/download/v0.138.0/checksums_sha256.txt"));
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

		AliasRef ref = AliasRef.parse("tool:v1.0");
		Alias alias = Alias.get(ref.alias);
		String resolved = resolveWithVersion(alias, ref);

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

		AliasRef ref = AliasRef.parse("tool:1.0.0");
		Alias alias = Alias.get(ref.alias);
		String resolved = resolveWithVersion(alias, ref);

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

		AliasRef ref = AliasRef.parse("tool:1.0");
		Alias alias = Alias.get(ref.alias);
		String resolved = resolveWithVersion(alias, ref);

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

		AliasRef ref = AliasRef.parse("tool:1.0");
		Alias alias = Alias.get(ref.alias);
		String resolved = resolveWithVersion(alias, ref);

		assertThat(resolved, equalTo("https://bitbucket.org/org/repo/raw/1.0/script.java"));
	}

	@Test
	void testDirectCatalogRefWithVersion() throws IOException {
		AliasRef ref = AliasRef.parse("hello:v1.0@jbangdev/jbang-catalog/main");
		assertThat(ref.requestedVersion, equalTo("v1.0"));
		assertThat(ref.alias, equalTo("hello@jbangdev/jbang-catalog/main"));
	}

	@Test
	void testPropertyReplacementSkipsAutomaticReplacement() throws IOException {
		// Property replacement should take precedence over automatic GAV replacement
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"com.example:tool-${jbang.app.version:1.0}:2.0\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		AliasRef ref = AliasRef.parse("tool:3.0");
		Alias alias = Alias.get(ref.alias);
		String resolved = resolveWithVersion(alias, ref);

		// Should use property replacement only, not replace the :2.0 GAV version
		assertThat(resolved, equalTo("com.example:tool-3.0:2.0"));
	}

	@Test
	void testBackwardCompatibilityNoVersion() throws IOException {
		// Aliases without version syntax should work exactly as before
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"com.example:artifact:1.0.0\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Alias alias = Alias.get("tool");
		String resolved = alias.resolve();

		assertThat(resolved, equalTo("com.example:artifact:1.0.0"));
	}

	@Test
	void testVersionWithSpecialCharacters() throws IOException {
		// Version strings can contain various characters
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"com.example:artifact:1.0.0\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		AliasRef ref = AliasRef.parse("tool:1.0.0-alpha+build.123");
		Alias alias = Alias.get(ref.alias);
		String resolved = resolveWithVersion(alias, ref);

		assertThat(ref.requestedVersion, equalTo("1.0.0-alpha+build.123"));
		assertThat(resolved, equalTo("com.example:artifact:1.0.0-alpha+build.123"));
	}

	@Test
	void testNoReplacementForPlainUrls() throws IOException {
		// URLs that don't match git patterns should throw error for least surprise
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"tool\": {\n" +
				"      \"script-ref\": \"https://example.com/script.java\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		AliasRef ref = AliasRef.parse("tool:1.0.0");
		Alias alias = Alias.get(ref.alias);
		assertThat(alias, notNullValue());

		ExitException ex = assertThrows(ExitException.class,
				() -> resolveWithVersion(alias, ref));
		assertThat(ex.getMessage(), containsString("Cannot apply version '1.0.0'"));
		assertThat(ex.getMessage(), containsString("No recognizable version pattern found"));
	}

	@Test
	void testAliasChainWithVersionErrors() throws IOException {
		// Test that version CANNOT be applied to alias chains
		// Version should only apply to the direct target, not through references
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"base\": {\n" +
				"      \"script-ref\": \"com.example:artifact:1.0.0\"\n" +
				"    },\n" +
				"    \"wrapper\": {\n" +
				"      \"script-ref\": \"base\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Catalog cat = Catalog.get(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON));
		AliasRef ref = AliasRef.parse("wrapper:2.0.0");
		ExitException ex = assertThrows(ExitException.class, () -> Alias.get(cat, ref.alias, ref.requestedVersion));
		assertThat(ex.getMessage(), containsString("Cannot apply version '2.0.0' to alias reference 'base'"));
		assertThat(ex.getMessage(), containsString("Use the target alias directly: base:2.0.0"));
	}

	@Test
	@Disabled("version pinning currently not supported inside catalogs")
	void testAliasChainWithVersionRef() throws IOException {
		// Test that version CANNOT be applied to alias chains
		// Version should only apply to the direct target, not through references
		String catalog = "{\n" +
				"  \"aliases\": {\n" +
				"    \"base\": {\n" +
				"      \"script-ref\": \"com.example:artifact:1.0.0\"\n" +
				"    },\n" +
				"    \"wrapper\": {\n" +
				"      \"script-ref\": \"base:2.0.0\"\n" +
				"    }\n" +
				"  }\n" +
				"}";
		Files.write(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON), catalog.getBytes());

		Catalog cat = Catalog.get(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON));
		AliasRef ref = AliasRef.parse("wrapper");
		Alias alias = Alias.get(cat, ref.alias, ref.requestedVersion);

		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("com.example:artifact:1.0.0"));

		cat = Catalog.get(jbangTempDir.resolve(Catalog.JBANG_CATALOG_JSON));
		ref = AliasRef.parse("wrapper:3.0.0");
		alias = Alias.get(cat, ref.alias, ref.requestedVersion);

		assertThat(alias, notNullValue());
		assertThat(alias.scriptRef, equalTo("com.example:artifact:3.0.0"));

	}

}
