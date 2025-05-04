package dev.jbang;

import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import dev.jbang.catalog.Alias;
import dev.jbang.catalog.ImplicitCatalogRef;

/**
 * TODO: use mock to handle https lookups instead of jbang.dev
 */
public class TestImplicitAlias extends BaseTest {

	@Test
	public void testGitImplicitCatalog() {
		assertThat(ImplicitCatalogRef.resolveImplicitCatalogUrl("jbangdev").get(),
				Matchers.equalTo("https://github.com/jbangdev/jbang-catalog/blob/HEAD/jbang-catalog.json"));
		assertThat(ImplicitCatalogRef.resolveImplicitCatalogUrl("jbangdev/jbang-examples").get(),
				Matchers.equalTo("https://github.com/jbangdev/jbang-examples/blob/HEAD/jbang-catalog.json"));
	}

	@Test
	public void testImplictURLAlias() {

		Alias url = Alias.get("tree@xam.dk");
		assertThat(url.scriptRef, Matchers.equalTo("tree/main.java"));

	}

	@Test
	public void testImplictExplicitURLAlias() {

		Alias url = Alias.get("tree@https://xam.dk");
		assertThat(url.scriptRef, Matchers.equalTo("tree/main.java"));

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

		assertThat(alias.scriptRef, Matchers.equalTo("helloworld.java"));

	}
}
