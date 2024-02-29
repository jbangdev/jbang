package dev.jbang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.ImplicitCatalogRef;

/**
 * TODO: use mock to handle https lookups instead of jbang.dev
 */
public class TestImplicitAlias extends BaseTest {

	@Test
	public void testGitImplicitCatalog() {
		assertThat(ImplicitCatalogRef.getImplicitCatalogUrl("jbangdev").get(),
				equalTo("https://github.com/jbangdev/jbang-catalog/blob/HEAD/jbang-catalog.json"));
		assertThat(ImplicitCatalogRef.getImplicitCatalogUrl("jbangdev/jbang-examples").get(),
				equalTo("https://github.com/jbangdev/jbang-examples/blob/HEAD/jbang-catalog.json"));
	}

	@ParameterizedTest
	@ValueSource(strings = { /* not sure this should be allowed "", */ "/", "/sqlline", "jbanghub/sqlline" })
	public void testGitImplicitCatalogHub(String catalog) {
		String cref = ImplicitCatalogRef.getImplicitCatalogUrl(catalog).get();
		assertThat(cref,
				startsWith("https://github.com/jbanghub/"));

		Catalog c = Catalog.getByName(catalog);
		assertThat(c.catalogRef.getOriginalResource(),
				startsWith("https://github.com/jbanghub/"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "sqlline@", "sqlline@/", "sqlline@/sqlline", "sqlline@jbanghub/sqlline" })
	public void testImplicitHub(String alias) {
		Alias a = Alias.get(alias);
		assertThat(a.scriptRef, containsString("sqlline:sqlline"));
		assertThat(a.catalog.baseRef, containsString("jbanghub"));
	}

	@Test
	public void testImplictURLAlias() {

		Alias url = Alias.get("tree@xam.dk");
		assertThat(url.scriptRef, equalTo("tree/main.java"));

	}

	@Test
	public void testImplictExplicitURLAlias() {

		Alias url = Alias.get("tree@https://xam.dk");
		assertThat(url.scriptRef, equalTo("tree/main.java"));

	}

	// @Test needs fixing to not generate absolute paths but instead relative paths.
	public void testFileURLAlias() throws Exception {

		assertThat(jbangTempDir.resolve("inner").toFile().mkdirs(), Matchers.is(true));

		Files.copy(examplesTestFolder.resolve("helloworld.java"), jbangTempDir.resolve("inner/helloworld.java"));
		String src = jbangTempDir.resolve("inner/helloworld.java").toString();
		Path path = jbangTempDir.resolve("jbang-catalog.json");

		checkedRun(null, "alias", "add", "-f", path.toString(), "--name=apptest", src);

		String url = "apptest@" + path.toUri();
		assertThat(url, Matchers.stringContainsInOrder("file://"));

		Alias alias = Alias.get(url);

		assertThat(alias.scriptRef, equalTo("helloworld.java"));

	}
}
