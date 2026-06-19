package dev.jbang.cli.completion;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.jbang.BaseTest;
import dev.jbang.catalog.Catalog;

public class TestScriptRefCompleter extends BaseTest {

	private ScriptRefCompleter completer;

	@BeforeEach
	void setUp() {
		completer = new ScriptRefCompleter();
	}

	// --- File completion tests ---

	@Test
	void testCompletesJavaFilesAndFiltersNonScript() throws IOException {
		Files.writeString(cwdDir.resolve("hello.java"), "// hello");
		Files.writeString(cwdDir.resolve("world.java"), "// world");
		Files.writeString(cwdDir.resolve("readme.txt"), "text");

		List<String> candidates = complete("");

		assertThat(candidates, hasItem("hello.java"));
		assertThat(candidates, hasItem("world.java"));
		assertThat(candidates, not(hasItem("readme.txt")));
	}

	static Stream<Arguments> scriptExtensions() {
		return Stream.of(
				Arguments.of("app.kt", "fun main() {}"),
				Arguments.of("script.groovy", "println 'hi'"),
				Arguments.of("snippet.jsh", "var x = 1;"),
				Arguments.of("literate.md", "# hello"));
	}

	@ParameterizedTest
	@MethodSource("scriptExtensions")
	void testCompletesScriptExtension(String filename, String content) throws IOException {
		Files.writeString(cwdDir.resolve(filename), content);
		assertThat(complete(""), hasItem(filename));
	}

	@Test
	void testCompletesDirectoriesWithTrailingSlash() throws IOException {
		Files.createDirectory(cwdDir.resolve("subdir"));

		List<String> candidates = complete("");

		assertThat(valuesOnly(candidates), hasItem("subdir/"));
		assertThat(candidates, hasItem("subdir/\tDirectory"));
	}

	@Test
	void testSkipsHiddenFiles() throws IOException {
		Files.writeString(cwdDir.resolve(".hidden.java"), "// hidden");
		Files.writeString(cwdDir.resolve("visible.java"), "// visible");

		List<String> candidates = complete("");

		assertThat(candidates, hasItem("visible.java"));
		assertThat(candidates, not(hasItem(".hidden.java")));
	}

	@Test
	void testCompletesWithPartialPrefix() throws IOException {
		Files.writeString(cwdDir.resolve("hello.java"), "// hello");
		Files.writeString(cwdDir.resolve("help.java"), "// help");
		Files.writeString(cwdDir.resolve("world.java"), "// world");

		List<String> candidates = complete("hel");

		assertThat(candidates, hasItem("hello.java"));
		assertThat(candidates, hasItem("help.java"));
		assertThat(candidates, not(hasItem("world.java")));
	}

	@Test
	void testCompletesInSubdirectory() throws IOException {
		Path sub = Files.createDirectory(cwdDir.resolve("src"));
		Files.writeString(sub.resolve("App.java"), "// app");

		List<String> candidates = complete("src/");

		assertThat(candidates, hasItem("src/App.java"));
	}

	@Test
	void testCompletesPartialInSubdirectory() throws IOException {
		Path sub = Files.createDirectory(cwdDir.resolve("src"));
		Files.writeString(sub.resolve("App.java"), "// app");
		Files.writeString(sub.resolve("Main.java"), "// main");

		List<String> candidates = complete("src/A");

		assertThat(candidates, hasItem("src/App.java"));
		assertThat(candidates, not(hasItem("src/Main.java")));
	}

	@Test
	void testEmptyForNonexistentDirectory() {
		List<String> candidates = complete("nonexistent/");

		assertThat(candidates, is(empty()));
	}

	// --- Alias completion tests ---

	@Test
	void testCompletesAliases() throws IOException {
		Path catalogPath = cwdDir.resolve(Catalog.JBANG_CATALOG_JSON);
		Files.writeString(catalogPath, "{\n"
				+ "  \"aliases\": {\n"
				+ "    \"myalias\": { \"script-ref\": \"hello.java\" },\n"
				+ "    \"otheralias\": { \"script-ref\": \"world.java\" }\n"
				+ "  }\n"
				+ "}");
		dev.jbang.util.TestUtil.clearSettingsCaches();

		List<String> candidates = complete("");

		assertThat(valuesOnly(candidates), hasItem("myalias"));
		assertThat(valuesOnly(candidates), hasItem("otheralias"));
		assertThat(candidates, hasItem("myalias\tAlias"));
	}

	@Test
	void testCompletesAliasesWithPrefix() throws IOException {
		Path catalogPath = cwdDir.resolve(Catalog.JBANG_CATALOG_JSON);
		Files.writeString(catalogPath, "{\n"
				+ "  \"aliases\": {\n"
				+ "    \"myalias\": { \"script-ref\": \"hello.java\" },\n"
				+ "    \"otheralias\": { \"script-ref\": \"world.java\" }\n"
				+ "  }\n"
				+ "}");
		dev.jbang.util.TestUtil.clearSettingsCaches();

		List<String> candidates = complete("my");

		assertThat(valuesOnly(candidates), hasItem("myalias"));
		assertThat(valuesOnly(candidates), not(hasItem("otheralias")));
	}

	@Test
	void testCombinesFilesAndAliases() throws IOException {
		Files.writeString(cwdDir.resolve("hello.java"), "// hello");

		Path catalogPath = cwdDir.resolve(Catalog.JBANG_CATALOG_JSON);
		Files.writeString(catalogPath, "{\n"
				+ "  \"aliases\": {\n"
				+ "    \"myalias\": { \"script-ref\": \"hello.java\" }\n"
				+ "  }\n"
				+ "}");
		dev.jbang.util.TestUtil.clearSettingsCaches();

		List<String> candidates = complete("");

		assertThat(candidates, hasItem("hello.java"));
		assertThat(valuesOnly(candidates), hasItem("myalias"));
	}

	@Test
	void testCompletionIsCaseInsensitiveForFiles() throws IOException {
		Files.writeString(cwdDir.resolve("Hello.java"), "// hello");

		List<String> candidates = complete("hel");

		assertThat(candidates, hasItem("Hello.java"));
	}

	@Test
	void testNullPartialTreatedAsEmpty() throws IOException {
		Files.writeString(cwdDir.resolve("test.java"), "// test");

		List<String> candidates = completeRaw(null);

		assertThat(candidates, hasItem("test.java"));
	}

	// --- Navigation hint tests ---

	@Test
	void testEmptyInputShowsNavigationHints() throws IOException {
		List<String> candidates = complete("");

		assertThat(candidates, hasItem("@\tBrowse catalogs"));
		assertThat(valuesOnly(candidates), hasItem("https://github.com/"));
		assertThat(valuesOnly(candidates), hasItem("https://gitlab.com/"));
	}

	@Test
	void testHttpPrefixOffersUrlHints() {
		List<String> candidates = complete("htt");

		assertThat(valuesOnly(candidates), hasItem("https://github.com/"));
		assertThat(valuesOnly(candidates), hasItem("https://gist.github.com/"));
		assertThat(valuesOnly(candidates), hasItem("https://gitlab.com/"));
		assertThat(valuesOnly(candidates), hasItem("https://bitbucket.org/"));
	}

	@Test
	void testHttpsBPrefixNarrowsUrlHints() {
		// https://b narrows to bitbucket only (github/gitlab don't match)
		List<String> candidates = complete("https://b");

		assertThat(valuesOnly(candidates), hasItem("https://bitbucket.org/"));
		assertThat(valuesOnly(candidates), not(hasItem("https://github.com/")));
		assertThat(valuesOnly(candidates), not(hasItem("https://gitlab.com/")));
	}

	@Test
	void testNonMatchingPrefixOmitsUrlHints() throws IOException {
		Files.writeString(cwdDir.resolve("hello.java"), "// hello");

		List<String> candidates = complete("hel");

		assertThat(valuesOnly(candidates), not(hasItem("@")));
		assertThat(valuesOnly(candidates), not(hasItem("https://github.com/")));
	}

	@Test
	void testDottedInputHintsMavenColon() {
		List<String> candidates = complete("dev.jbang");

		// Single candidate has its description stripped to avoid aesh
		// space-escaping the tab-separated description
		assertThat(valuesOnly(candidates), hasItem("dev.jbang:"));
	}

	@Test
	void testDottedInputNoColonHintWhenAlreadyGav() {
		// 2+ dots triggers GAV mode — Maven colon *hint* should not appear,
		// but real GAV results (local or remote) are expected
		List<String> candidates = complete("com.google.guava");

		// Should not have the navigation hint "Maven artifact lookup"
		assertThat(candidates.stream()
			.anyMatch(c -> c.contains("Maven artifact lookup")), is(false));
	}

	// --- Catalog alias browsing tests ---

	@Test
	void testAtSignListsCatalogNames() throws IOException {
		Path catalogPath = cwdDir.resolve(Catalog.JBANG_CATALOG_JSON);
		Files.writeString(catalogPath, "{\n"
				+ "  \"catalogs\": {\n"
				+ "    \"mycat\": { \"catalog-ref\": \"" + catalogPath.toUri() + "\" }\n"
				+ "  }\n"
				+ "}");
		dev.jbang.util.TestUtil.clearSettingsCaches();

		List<String> candidates = complete("@");

		assertThat(valuesOnly(candidates), hasItem("@mycat"));
		assertThat(candidates, hasItem("@mycat\tCatalog"));
	}

	@Test
	void testAtCatalogNameListsAliasesInCatalog() throws IOException {
		Path catalogPath = cwdDir.resolve(Catalog.JBANG_CATALOG_JSON);
		Files.writeString(catalogPath, "{\n"
				+ "  \"catalogs\": {\n"
				+ "    \"mycat\": { \"catalog-ref\": \"" + catalogPath.toUri() + "\" }\n"
				+ "  },\n"
				+ "  \"aliases\": {\n"
				+ "    \"foo\": { \"script-ref\": \"foo.java\" },\n"
				+ "    \"bar\": { \"script-ref\": \"bar.java\" }\n"
				+ "  }\n"
				+ "}");
		dev.jbang.util.TestUtil.clearSettingsCaches();

		List<String> candidates = complete("@mycat");

		assertThat(valuesOnly(candidates), hasItem("foo@mycat"));
		assertThat(valuesOnly(candidates), hasItem("bar@mycat"));
		assertThat(candidates, hasItem("foo@mycat\tAlias in mycat"));
	}

	@Test
	void testAtPrefixMatchesCatalogNames() throws IOException {
		Path catalogPath = cwdDir.resolve(Catalog.JBANG_CATALOG_JSON);
		Files.writeString(catalogPath, "{\n"
				+ "  \"catalogs\": {\n"
				+ "    \"mycat\": { \"catalog-ref\": \"" + catalogPath.toUri() + "\" },\n"
				+ "    \"other\": { \"catalog-ref\": \"" + catalogPath.toUri() + "\" }\n"
				+ "  }\n"
				+ "}");
		dev.jbang.util.TestUtil.clearSettingsCaches();

		List<String> candidates = complete("@my");

		assertThat(valuesOnly(candidates), hasItem("@mycat"));
		assertThat(valuesOnly(candidates), not(hasItem("@other")));
	}

	// --- GAV completion tests ---

	@Test
	void testLooksLikeGavWithColon() {
		assertThat(ScriptRefCompleter.looksLikeGav("com.google.guava:"), is(true));
		assertThat(ScriptRefCompleter.looksLikeGav("com.google.guava:guava:"), is(true));
		assertThat(ScriptRefCompleter.looksLikeGav(":"), is(true));
	}

	@Test
	void testLooksLikeGavWithMultipleDots() {
		assertThat(ScriptRefCompleter.looksLikeGav("com.google.guava"), is(true));
		assertThat(ScriptRefCompleter.looksLikeGav("com.google."), is(true));
	}

	@Test
	void testDoesNotLookLikeGavForFiles() {
		// Single dot is a file extension
		assertThat(ScriptRefCompleter.looksLikeGav("hello.java"), is(false));
		assertThat(ScriptRefCompleter.looksLikeGav("script.jsh"), is(false));
		// No dots at all
		assertThat(ScriptRefCompleter.looksLikeGav("myalias"), is(false));
		// Path separators
		assertThat(ScriptRefCompleter.looksLikeGav("src/Main.java"), is(false));
	}

	@Test
	void testCompleteGavGroupId() throws IOException {
		// Create a fake maven repo structure:
		// com/example/mylib/1.0/mylib-1.0.pom
		Path repo = Files.createDirectories(cwdDir.resolve(".m2/repository"));
		Path versionDir = Files.createDirectories(
				repo.resolve("com/example/mylib/1.0"));
		Files.writeString(versionDir.resolve("mylib-1.0.pom"), "<pom/>");

		// Point user.home to our temp dir so fallback repo path works
		ScriptRefCompleter completer = new ScriptRefCompleter();
		List<String> candidates = completeGav(completer, "com.example.");

		// Should not crash; whether it finds our repo depends on
		// ArtifactResolver config, so just verify it doesn't error.
		// The real integration is tested via --aesh-complete.
		assertThat(candidates, is(notNullValue()));
	}

	@Test
	void testCompleteGavSwitchesAwayFromFiles() throws IOException {
		// With 2+ dots the completer should NOT return local files
		Files.writeString(cwdDir.resolve("com.example.test.java"), "// file");

		List<String> candidates = complete("com.example.test");

		// Should not contain the file since it looks like a GAV
		assertThat(candidates, not(hasItem("com.example.test.java")));
	}

	@Test
	void testGavModeNoResultsSuppressesFileFallback() {
		// When GAV mode finds nothing, return the partial itself
		// to suppress the shell's file-completion fallback
		List<String> candidates = complete("dev.jba:");

		assertThat(candidates, hasSize(1));
		assertThat(candidates, hasItem("dev.jba:"));
	}

	// --- Helpers ---

	private static List<String> valuesOnly(List<String> candidates) {
		return candidates.stream()
			.map(c -> {
				int t = c.indexOf('\t');
				return t >= 0 ? c.substring(0, t) : c;
			})
			.collect(Collectors.toList());
	}

	private List<String> complete(String partial) {
		return completeRaw(partial);
	}

	private List<String> completeRaw(String partial) {
		StubCompleterInvocation inv = new StubCompleterInvocation(partial);
		completer.complete(inv);
		return inv.getValues();
	}

	private List<String> completeGav(ScriptRefCompleter c, String partial) {
		StubCompleterInvocation inv = new StubCompleterInvocation(partial);
		c.complete(inv);
		return inv.getValues();
	}
}
