package dev.jbang.cli.completion;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.aesh.command.Command;
import org.aesh.command.completer.CompleterInvocation;
import org.aesh.console.AeshContext;
import org.aesh.terminal.formatting.TerminalString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
	void testCompletesJavaFiles() throws IOException {
		Files.writeString(cwdDir.resolve("hello.java"), "// hello");
		Files.writeString(cwdDir.resolve("world.java"), "// world");
		Files.writeString(cwdDir.resolve("readme.txt"), "text");

		List<String> candidates = complete("");

		assertThat(candidates, hasItem("hello.java"));
		assertThat(candidates, hasItem("world.java"));
		assertThat(candidates, not(hasItem("readme.txt")));
	}

	@Test
	void testCompletesKotlinFiles() throws IOException {
		Files.writeString(cwdDir.resolve("app.kt"), "fun main() {}");

		List<String> candidates = complete("");

		assertThat(candidates, hasItem("app.kt"));
	}

	@Test
	void testCompletesGroovyFiles() throws IOException {
		Files.writeString(cwdDir.resolve("script.groovy"), "println 'hi'");

		List<String> candidates = complete("");

		assertThat(candidates, hasItem("script.groovy"));
	}

	@Test
	void testCompletesJShellFiles() throws IOException {
		Files.writeString(cwdDir.resolve("snippet.jsh"), "var x = 1;");

		List<String> candidates = complete("");

		assertThat(candidates, hasItem("snippet.jsh"));
	}

	@Test
	void testCompletesMarkdownFiles() throws IOException {
		Files.writeString(cwdDir.resolve("literate.md"), "# hello");

		List<String> candidates = complete("");

		assertThat(candidates, hasItem("literate.md"));
	}

	@Test
	void testCompletesDirectoriesWithTrailingSlash() throws IOException {
		Files.createDirectory(cwdDir.resolve("subdir"));

		List<String> candidates = complete("");

		assertThat(candidates, hasItem("subdir/"));
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

		assertThat(candidates, hasItem("myalias"));
		assertThat(candidates, hasItem("otheralias"));
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

		assertThat(candidates, hasItem("myalias"));
		assertThat(candidates, not(hasItem("otheralias")));
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
		assertThat(candidates, hasItem("myalias"));
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

	// --- Helpers ---

	private List<String> complete(String partial) {
		return completeRaw(partial);
	}

	private List<String> completeRaw(String partial) {
		StubCompleterInvocation inv = new StubCompleterInvocation(partial);
		completer.complete(inv);
		return inv.getValues();
	}

	/**
	 * Minimal stub of CompleterInvocation for testing.
	 */
	private static class StubCompleterInvocation implements CompleterInvocation {
		private final String given;
		private final List<String> values = new ArrayList<>();
		private boolean appendSpace = true;

		StubCompleterInvocation(String given) {
			this.given = given;
		}

		@Override
		public String getGivenCompleteValue() {
			return given;
		}

		@Override
		public Command getCommand() {
			return null;
		}

		@Override
		public List<TerminalString> getCompleterValues() {
			List<TerminalString> result = new ArrayList<>();
			for (String v : values) {
				result.add(new TerminalString(v));
			}
			return result;
		}

		@Override
		public void setCompleterValues(Collection<String> completerValues) {
			values.clear();
			values.addAll(completerValues);
		}

		@Override
		public void setCompleterValuesTerminalString(List<TerminalString> completerValues) {
			values.clear();
			for (TerminalString ts : completerValues) {
				values.add(ts.getCharacters());
			}
		}

		@Override
		public void clearCompleterValues() {
			values.clear();
		}

		@Override
		public void addAllCompleterValues(Collection<String> completerValues) {
			values.addAll(completerValues);
		}

		@Override
		public void addCompleterValue(String value) {
			values.add(value);
		}

		@Override
		public void addCompleterValueTerminalString(TerminalString value) {
			values.add(value.getCharacters());
		}

		@Override
		public boolean isAppendSpace() {
			return appendSpace;
		}

		@Override
		public void setAppendSpace(boolean appendSpace) {
			this.appendSpace = appendSpace;
		}

		@Override
		public void setIgnoreOffset(boolean ignoreOffset) {
		}

		@Override
		public boolean doIgnoreOffset() {
			return false;
		}

		@Override
		public void setOffset(int offset) {
		}

		@Override
		public int getOffset() {
			return 0;
		}

		@Override
		public void setIgnoreStartsWith(boolean ignoreStartsWith) {
		}

		@Override
		public boolean isIgnoreStartsWith() {
			return false;
		}

		@Override
		public AeshContext getAeshContext() {
			return null;
		}

		List<String> getValues() {
			return values;
		}
	}
}
