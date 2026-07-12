package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.qameta.allure.Description;

/**
 * Integration tests for scriptOrFile tab-completion via
 * {@code jbang --aesh-complete}. These exercise the real CLI entry point
 * (including native image) and verify that the ScriptRefCompleter produces the
 * expected candidates.
 */
public class CompletionIT extends BaseIT {

	private Path workDir;

	@BeforeEach
	void createWorkDir() throws IOException {
		workDir = scratch().resolve("completion-test");
		Files.createDirectories(workDir);
	}

	// ---- helper to run completion in a controlled directory ----

	private CommandResult complete(String subcommand, String partial) {
		// Run jbang --aesh-complete inside workDir so file completion is deterministic
		return run(workDir, baseEnv, prefixShellArgs(
				java.util.Arrays.asList("jbang --aesh-complete -- " + subcommand + " \"" + partial + "\"")));
	}

	// ---- File completion tests ----

	@Test
	@Description("Java files should appear in completion candidates")
	void completesJavaFiles() throws IOException {
		Files.writeString(workDir.resolve("hello.java"), "//JAVA 11\nclass hello {}");
		Files.writeString(workDir.resolve("world.java"), "//JAVA 11\nclass world {}");
		Files.writeString(workDir.resolve("readme.txt"), "not a script");

		assertThat(complete("run", ""))
			.succeeded()
			.outContains("hello.java")
			.outContains("world.java")
			.outNotContains("readme.txt");
	}

	@Test
	@Description("Partial prefix should filter file candidates")
	void completesWithPartialPrefix() throws IOException {
		Files.writeString(workDir.resolve("hello.java"), "class hello {}");
		Files.writeString(workDir.resolve("help.java"), "class help {}");
		Files.writeString(workDir.resolve("world.java"), "class world {}");

		assertThat(complete("run", "hel"))
			.succeeded()
			.outContains("hello.java")
			.outContains("help.java")
			.outNotContains("world.java");
	}

	@Test
	@Description("Subdirectory entries should have trailing slash")
	void completesDirectoriesWithSlash() throws IOException {
		Files.createDirectories(workDir.resolve("subdir"));

		assertThat(complete("run", ""))
			.succeeded()
			.outContains("subdir/");
	}

	@Test
	@Description("Files inside subdirectories should be completed")
	void completesFilesInSubdirectory() throws IOException {
		Path sub = Files.createDirectories(workDir.resolve("src"));
		Files.writeString(sub.resolve("App.java"), "class App {}");

		assertThat(complete("run", "src/"))
			.succeeded()
			.outContains("src/App.java");
	}

	@Test
	@Description("Hidden files should not appear in completion")
	void skipsHiddenFiles() throws IOException {
		Files.writeString(workDir.resolve(".hidden.java"), "class hidden {}");
		Files.writeString(workDir.resolve("visible.java"), "class visible {}");

		assertThat(complete("run", ""))
			.succeeded()
			.outContains("visible.java")
			.outNotContains(".hidden.java");
	}

	@Test
	@Description("Kotlin, Groovy, JShell, and Markdown files are completed")
	void completesAllScriptExtensions() throws IOException {
		Files.writeString(workDir.resolve("app.kt"), "fun main() {}");
		Files.writeString(workDir.resolve("script.groovy"), "println 'hi'");
		Files.writeString(workDir.resolve("snippet.jsh"), "var x = 1;");
		Files.writeString(workDir.resolve("literate.md"), "# hello");

		CommandResult result = complete("run", "");
		assertThat(result).succeeded()
			.outContains("app.kt")
			.outContains("script.groovy")
			.outContains("snippet.jsh")
			.outContains("literate.md");
	}

	// ---- Navigation hints ----

	@Test
	@Description("Empty input should show navigation hints (@ and URL prefixes)")
	void emptyInputShowsNavigationHints() {
		assertThat(complete("run", ""))
			.succeeded()
			.outContains("@")
			.outContains("https://github.com/")
			.outContains("https://gitlab.com/");
	}

	@Test
	@Description("Typing 'htt' should offer URL hints")
	void httpPrefixOffersUrlHints() {
		assertThat(complete("run", "htt"))
			.succeeded()
			.outContains("https://github.com/")
			.outContains("https://gitlab.com/")
			.outContains("https://bitbucket.org/");
	}

	@Test
	@Description("Non-matching prefix should not show URL hints")
	void nonMatchingPrefixOmitsUrlHints() throws IOException {
		Files.writeString(workDir.resolve("hello.java"), "class hello {}");

		assertThat(complete("run", "hel"))
			.succeeded()
			.outNotContains("https://github.com/")
			.outNotContains("@");
	}

	// ---- Alias completion ----

	@Test
	@Description("Aliases from jbang-catalog.json should appear in completion")
	void completesAliases() throws IOException {
		Files.writeString(workDir.resolve("jbang-catalog.json"),
				"{\n"
						+ "  \"aliases\": {\n"
						+ "    \"myalias\": { \"script-ref\": \"hello.java\" },\n"
						+ "    \"otheralias\": { \"script-ref\": \"world.java\" }\n"
						+ "  }\n"
						+ "}");

		assertThat(complete("run", ""))
			.succeeded()
			.outContains("myalias")
			.outContains("otheralias");
	}

	@Test
	@Description("Alias completion respects prefix filtering")
	void completesAliasesWithPrefix() throws IOException {
		Files.writeString(workDir.resolve("jbang-catalog.json"),
				"{\n"
						+ "  \"aliases\": {\n"
						+ "    \"myalias\": { \"script-ref\": \"hello.java\" },\n"
						+ "    \"otheralias\": { \"script-ref\": \"world.java\" }\n"
						+ "  }\n"
						+ "}");

		assertThat(complete("run", "my"))
			.succeeded()
			.outContains("myalias")
			.outNotContains("otheralias");
	}

	// ---- Catalog browsing ----

	@Test
	@Description("@ should list available catalog names")
	void atSignListsCatalogs() throws IOException {
		Files.writeString(workDir.resolve("jbang-catalog.json"),
				"{\n"
						+ "  \"catalogs\": {\n"
						+ "    \"testcat\": { \"catalog-ref\": \"jbang-catalog.json\" }\n"
						+ "  },\n"
						+ "  \"aliases\": {\n"
						+ "    \"foo\": { \"script-ref\": \"foo.java\" }\n"
						+ "  }\n"
						+ "}");

		assertThat(complete("run", "@"))
			.succeeded()
			.outContains("@testcat");
	}

	@Test
	@Description("@catalogName should list aliases in that catalog")
	void atCatalogListsAliases() throws IOException {
		// Create a self-referencing catalog with aliases
		Path catFile = workDir.resolve("jbang-catalog.json");
		Files.writeString(catFile,
				"{\n"
						+ "  \"catalogs\": {\n"
						+ "    \"testcat\": { \"catalog-ref\": \"" + catFile.toUri() + "\" }\n"
						+ "  },\n"
						+ "  \"aliases\": {\n"
						+ "    \"foo\": { \"script-ref\": \"foo.java\" },\n"
						+ "    \"bar\": { \"script-ref\": \"bar.java\" }\n"
						+ "  }\n"
						+ "}");

		assertThat(complete("run", "@testcat"))
			.succeeded()
			.outContains("foo@testcat")
			.outContains("bar@testcat");
	}

	// ---- GAV completion ----

	@Test
	@Description("Input with colon should trigger GAV mode and suppress file completion fallback")
	void gavModeWithColonSuppressesFiles() throws IOException {
		// Even with a java file that matches, colon forces GAV mode
		Files.writeString(workDir.resolve("dev.java"), "class dev {}");

		CommandResult result = complete("run", "dev.jba:");
		assertThat(result).succeeded()
			// Should contain the partial itself (GAV fallback) and not file candidates
			.outNotContains("dev.java");
	}

	@Test
	@Description("Input with 2+ dots should look like a GAV groupId, not a filename")
	void dottedInputTriggersGavMode() throws IOException {
		Files.writeString(workDir.resolve("com.example.test.java"), "class t {}");

		assertThat(complete("run", "com.example.test"))
			.succeeded()
			.outNotContains("com.example.test.java");
	}

	// ---- Completion works for multiple subcommands ----

	@Test
	@Description("Completion should work with 'build' subcommand")
	void completesForBuildSubcommand() throws IOException {
		Files.writeString(workDir.resolve("hello.java"), "class hello {}");

		assertThat(complete("build", ""))
			.succeeded()
			.outContains("hello.java");
	}

	@Test
	@Description("Completion should work with 'edit' subcommand")
	void completesForEditSubcommand() throws IOException {
		Files.writeString(workDir.resolve("hello.java"), "class hello {}");

		assertThat(complete("edit", ""))
			.succeeded()
			.outContains("hello.java");
	}

	// ---- URL completion should not trigger file completion ----

	@Test
	@Description("Full URL prefix should not offer file candidates")
	void fullUrlDoesNotOfferFiles() throws IOException {
		Files.writeString(workDir.resolve("hello.java"), "class hello {}");

		assertThat(complete("run", "https://github.com/"))
			.succeeded()
			.outNotContains("hello.java");
	}

	// ---- Completion script generation ----

	@Test
	@Description("'jbang completion -s bash' should generate a valid bash completion script")
	void generatesBashCompletionScript() {
		CommandResult result = shell("jbang completion -s bash");
		assertThat(result)
			.succeeded()
			.outContains("_complete_jbang")
			.outContains("--aesh-complete");
	}

	@Test
	@Description("'jbang completion -s zsh' should generate a valid zsh completion script")
	void generatesZshCompletionScript() {
		CommandResult result = shell("jbang completion -s zsh");
		assertThat(result)
			.succeeded()
			.outContains("jbang")
			.outContains("--aesh-complete");
	}
}
