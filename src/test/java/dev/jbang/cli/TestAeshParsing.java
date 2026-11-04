package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aesh.command.CommandDefinition;
import org.aesh.command.impl.parser.CommandLineParser;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.registry.CommandRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.jbang.BaseTest;
import dev.jbang.Configuration;
import dev.jbang.Main;
import dev.jbang.catalog.Catalog;
import dev.jbang.util.Util;

class TestAeshParsing extends BaseTest {

	// --- Help rendering ---

	@Test
	void testGlobalOptionsAreInOwnHelpSection() {
		String help = JBang.renderHelp("run");
		assertThat(help, containsString("Global options:"));
		// Global flags live under the Global options section
		assertThat(help.indexOf("Global options:"), greaterThanOrEqualTo(0));
		assertThat(help, containsString("--[no-]verbose"));
		assertThat(help, containsString("--netrc"));
		assertThat(help, containsString("--config"));
	}

	@Test
	void testHelpOptionIsInGlobalOptions() {
		String help = JBang.renderHelp("run");
		// -h/--help is provided by us (generateHelp=false) and grouped under Global
		// options, appearing exactly once.
		assertThat(help, containsString("-h, --help"));
		assertThat(help.indexOf("-h, --help"), equalTo(help.lastIndexOf("-h, --help")));
		assertThat(help.indexOf("-h, --help"), greaterThan(help.indexOf("Global options:")));
	}

	@Test
	void testOptionsAreGroupedIntoMeaningfulSections() {
		String help = JBang.renderHelp("run");
		assertThat(help, containsString("Source options:"));
		assertThat(help, containsString("Dependency options:"));
		assertThat(help, containsString("Build options:"));
		assertThat(help, containsString("JDK options:"));
		assertThat(help, containsString("Runtime options:"));
		assertThat(help, containsString("Native image options:"));
	}

	@Test
	void testRunHelpHasPivotExamplesSection() {
		String help = JBang.renderHelp("run");
		assertThat(help, containsString("Examples:"));
		assertThat(help, containsString("jbang run org.example:myapp:1.2.3"));
	}

	@ParameterizedTest
	@CsvSource({ "export, jbang export fatjar hello.java",
			"edit, jbang edit --no-open hello.java",
			"jdk, jbang jdk install 21",
			"alias, jbang alias add --name hello hello.java",
			"init, jbang init --template=cli mytool.java",
			"build, jbang build --native hello.java",
			"cache, jbang cache clear --all" })
	void testCommandHelpHasExamplesSection(String command, String example) {
		String help = JBang.renderHelp(command);
		assertThat(help, containsString("Examples:"));
		assertThat(help, containsString(example));
	}

	@ParameterizedTest
	@CsvSource({ "export local, jbang export local hello.java",
			"export portable, jbang export portable hello.java",
			"export fatjar, jbang export fatjar hello.java",
			"export native, jbang export native hello.java",
			"export jlink, jbang export jlink hello.java -- --strip-debug",
			"export mavenrepo, jbang export mavenrepo -g org.acme hello.java",
			"export gradle, jbang export gradle hello.java",
			"export maven, jbang export maven hello.java",
			"jdk install, jbang jdk install 21 /opt/java/jdk-21",
			"jdk list, jbang jdk list --available",
			"jdk default, jbang jdk default 21",
			"jdk java-env, eval $(jbang jdk java-env 21)",
			"jdk exec, jbang jdk exec -j 21 mvn package",
			"alias add, jbang alias add --name greet hello.java World",
			"alias list, jbang alias list --show-origin",
			"cache clear, jbang cache clear --jar --url" })
	void testSubcommandHelpHasExamplesSection(String commandPath, String example) {
		String help = JBang.renderHelp(commandPath.split(" "));
		assertThat(help, containsString("Examples:"));
		assertThat(help, containsString(example));
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void testOptionSectionsAreOrderedOptionsFirstGlobalLast() throws Exception {
		// Every command and subcommand: the command's own (ungrouped) "Options:"
		// come first and "Global options:" come last.
		CommandRegistry registry = AeshCommandRegistryBuilder.builder().command(JBang.class).create();
		String root = (String) registry.getAllCommandNames().iterator().next();
		List<String> problems = new ArrayList<>();
		checkOptionSections(registry.getCommand(root, root).getParser(), root, problems);
		assertThat(problems, empty());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void checkOptionSections(CommandLineParser parser, String path, List<String> problems) {
		List<String> headers = new ArrayList<>();
		for (String line : parser.printHelp().split("\\R")) {
			String l = line.replaceAll("\u001B\\[[;\\d]*m", "");
			if (l.equals("Options:") || l.endsWith(" options:")) {
				headers.add(l);
			}
		}
		if (headers.contains("Options:") && headers.indexOf("Options:") != 0) {
			problems.add(path + ": 'Options:' is not first " + headers);
		}
		if (!headers.isEmpty() && !headers.get(headers.size() - 1).equals("Global options:")) {
			problems.add(path + ": 'Global options:' is not last " + headers);
		}
		for (Object child : parser.getAllChildParsers()) {
			CommandLineParser cp = (CommandLineParser) child;
			checkOptionSections(cp, path + " " + cp.getProcessedCommand().name(), problems);
		}
	}

	// --- Mutual exclusion (exclusiveWith) ---

	@Test
	void testVerboseAndQuietAreMutuallyExclusive() {
		assertThrows(RuntimeException.class,
				() -> JBang.parseCommand("run", "--verbose", "--quiet", "test.java"));
	}

	@Test
	void testOfflineAndFreshAreMutuallyExclusive() {
		assertThrows(RuntimeException.class,
				() -> JBang.parseCommand("run", "--offline", "--fresh", "test.java"));
	}

	@Test
	void testVerboseAloneWorks() {
		JBang.parseCommand("run", "--verbose", "test.java");
		assertThat(Util.isVerbose(), is(true));
		assertThat(Util.isQuiet(), is(false));
	}

	@Test
	void testQuietAloneWorks() {
		JBang.parseCommand("run", "--quiet", "test.java");
		assertThat(Util.isQuiet(), is(true));
		assertThat(Util.isVerbose(), is(false));
	}

	@Test
	void testOfflineAloneWorks() {
		JBang.parseCommand("run", "--offline", "test.java");
		assertThat(Util.isOffline(), is(true));
		assertThat(Util.isFresh(), is(false));
	}

	@Test
	void testFreshAloneWorks() {
		JBang.parseCommand("run", "--fresh", "test.java");
		assertThat(Util.isFresh(), is(true));
		assertThat(Util.isOffline(), is(false));
	}

	// --- StrictOptionParser (optional value with = syntax) ---

	@Test
	void testModuleWithEqualsValue() {
		Run run = JBang.parseCommand("run", "--module=mymod", "test.java");
		assertThat(run.buildMixin.module, equalTo("mymod"));
	}

	@Test
	void testModuleWithEqualsEmpty() {
		Run run = JBang.parseCommand("run", "--module=", "test.java");
		assertThat(run.buildMixin.module, equalTo(""));
	}

	@Test
	void testModuleNotSpecified() {
		Run run = JBang.parseCommand("run", "test.java");
		assertThat(run.buildMixin.module, is(nullValue()));
	}

	@Test
	void testCodeWithEqualsValue() {
		Run run = JBang.parseCommand("run", "-c=System.out.println(42)");
		assertThat(run.literalScript, equalTo("System.out.println(42)"));
	}

	// --- Inherited options on subcommands ---

	@Test
	void testVerboseInheritedOnBuild() {
		JBang.parseCommand("build", "--verbose", "test.java");
		assertThat(Util.isVerbose(), is(true));
	}

	@Test
	void testOfflineInheritedOnInit() {
		JBang.parseCommand("init", "--offline", "test.java");
		assertThat(Util.isOffline(), is(true));
	}

	@Test
	void testFreshInheritedOnEdit() {
		JBang.parseCommand("edit", "--fresh", "test.java");
		assertThat(Util.isFresh(), is(true));
	}

	// --- Negatable options ---

	@Test
	void testNegatableIntegrationsTrue() {
		Run run = JBang.parseCommand("run", "--integrations", "test.java");
		assertThat(run.buildMixin.getIntegrations(), is(true));
	}

	@Test
	void testNegatableIntegrationsFalse() {
		Run run = JBang.parseCommand("run", "--no-integrations", "test.java");
		assertThat(run.buildMixin.getIntegrations(), is(false));
	}

	@Test
	void testNegatableIntegrationsDefault() {
		Run run = JBang.parseCommand("run", "test.java");
		assertThat(run.buildMixin.getIntegrations(), is(nullValue()));
	}

	// --- @OptionGroup (map options like -D) ---

	@Test
	void testPropertyOptionSingle() {
		Run run = JBang.parseCommand("run", "-Dfoo=bar", "test.java");
		assertThat(run.dependencyInfoMixin.properties, hasEntry("foo", "bar"));
	}

	@Test
	void testPropertyOptionMultiple() {
		Run run = JBang.parseCommand("run", "-Dfoo=bar", "-Dbaz=qux", "test.java");
		assertThat(run.dependencyInfoMixin.properties, hasEntry("foo", "bar"));
		assertThat(run.dependencyInfoMixin.properties, hasEntry("baz", "qux"));
		assertThat(run.dependencyInfoMixin.properties, is(aMapWithSize(2)));
	}

	// --- @OptionList with valueSeparator ---

	@Test
	void testDepsCommaSeparated() {
		Run run = JBang.parseCommand("run", "--deps", "org.foo:bar:1.0,org.baz:qux:2.0", "test.java");
		assertThat(run.dependencyInfoMixin.dependencies, hasSize(2));
		assertThat(run.dependencyInfoMixin.dependencies, contains("org.foo:bar:1.0", "org.baz:qux:2.0"));
	}

	@Test
	void testDepsMultipleFlags() {
		Run run = JBang.parseCommand("run", "--deps", "org.foo:bar:1.0", "--deps", "org.baz:qux:2.0", "test.java");
		assertThat(run.dependencyInfoMixin.dependencies, hasSize(2));
		assertThat(run.dependencyInfoMixin.dependencies, hasItems("org.foo:bar:1.0", "org.baz:qux:2.0"));
	}

	// --- DefaultValueProvider with config ---

	static final String TEST_CONFIG = "init.template = mytemplate\n";

	@Test
	void testDefaultValueProviderAppliesConfigDefaults() throws IOException {
		Files.write(jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS), TEST_CONFIG.getBytes());
		Configuration.instance(null);

		Init init = JBang.parseCommand("init", "dummy");
		assertThat(init.initTemplate, equalTo("mytemplate"));
	}

	@Test
	void testDefaultValueProviderExplicitOverridesConfig() throws IOException {
		Files.write(jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS), TEST_CONFIG.getBytes());
		Configuration.instance(null);

		Init init = JBang.parseCommand("init", "--template", "other", "dummy");
		assertThat(init.initTemplate, equalTo("other"));
	}

	@Test
	void testDefaultValueProviderSkipsDebug() throws IOException {
		String config = "run.debug = 5005\n";
		Files.write(jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS), config.getBytes());
		Configuration.instance(null);

		Run run = JBang.parseCommand("run", "test.java");
		assertThat(run.runMixin.debugString, is(nullValue()));
	}

	@Test
	void testDefaultValueProviderSkipsJfr() throws IOException {
		String config = "run.jfr = myrecording\n";
		Files.write(jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS), config.getBytes());
		Configuration.instance(null);

		Run run = JBang.parseCommand("run", "test.java");
		assertThat(run.runMixin.flightRecorderString, emptyOrNullString());
	}

	// --- Help output contains expected options ---

	@Test
	void testHelpShowsVisibleOptions() throws Exception {
		CaptureResult<Integer> result = captureOutput(() -> JBang.execute("run", "--help"));
		String output = result.normalizedOut() + result.normalizedErr();
		assertThat(output, containsString("verbose"));
		assertThat(output, containsString("quiet"));
		assertThat(output, containsString("offline"));
		assertThat(output, containsString("fresh"));
	}

	// --- Boolean options with explicit =true/=false values (#2504) ---

	@Test
	void testNoVerboseDisablesVerbose() {
		Util.setVerbose(true);
		JBang.parseCommand("run", "--no-verbose", "test.java");
		assertThat(Util.isVerbose(), is(false));
	}

	@Test
	void testNoOfflineDisablesOffline() {
		Util.setOffline(true);
		JBang.parseCommand("run", "--no-offline", "test.java");
		assertThat(Util.isOffline(), is(false));
	}

	@Test
	void testNoFreshDisablesFresh() {
		Util.setFresh(true);
		JBang.parseCommand("run", "--no-fresh", "test.java");
		assertThat(Util.isFresh(), is(false));
	}

	@Test
	void testUnspecifiedVerboseDefaultsToFalse() {
		// When --verbose is not specified, verbose should be false
		// (beforeParse resets all flags)
		JBang.parseCommand("run", "test.java");
		assertThat(Util.isVerbose(), is(false));
	}

	// --- Boolean =true/=false syntax ---

	@Test
	void testVerboseEqualsTrue() {
		JBang.parseCommand("run", "--verbose=true", "test.java");
		assertThat(Util.isVerbose(), is(true));
	}

	@Test
	void testVerboseEqualsFalse() {
		Util.setVerbose(true);
		JBang.parseCommand("run", "--verbose=false", "test.java");
		assertThat(Util.isVerbose(), is(false));
	}

	@Test
	void testForceEqualsTrue() {
		Init init = JBang.parseCommand("init", "--force=true", "dummy");
		assertThat(init.force, is(true));
	}

	@Test
	void testForceEqualsFalse() {
		Init init = JBang.parseCommand("init", "--force=false", "dummy");
		assertThat(init.force, is(false));
	}

	// --- Java version validation ---

	@Test
	void testJavaVersionValid() {
		Run run = JBang.parseCommand("run", "--java", "17", "test.java");
		assertThat(run.buildMixin.javaVersion, equalTo("17"));
	}

	@Test
	void testJavaVersionValidPlus() {
		Run run = JBang.parseCommand("run", "--java", "11+", "test.java");
		assertThat(run.buildMixin.javaVersion, equalTo("11+"));
	}

	@Test
	void testJavaVersionInvalid() {
		assertThrows(RuntimeException.class,
				() -> JBang.parseCommand("run", "--java", "abc", "test.java"));
	}

	// --- Debug option with custom OptionParser and fallback (#511) ---

	@Test
	void testBareDebugGetsFallbackValue() {
		// --debug without a value should get the annotation fallbackValue "4004"
		// DebugConverter converts "4004" to Map{"address" -> "4004"}
		Run run = JBang.parseCommand("run", "--debug", "test.java");
		assertThat(run.runMixin.debugString, hasEntry("address", "4004"));
	}

	@Test
	void testDebugWithExplicitPort() {
		Run run = JBang.parseCommand("run", "--debug", "5005", "test.java");
		assertThat(run.runMixin.debugString, hasEntry("address", "5005"));
	}

	@Test
	void testDebugWithEqualsPort() {
		Run run = JBang.parseCommand("run", "--debug=5005", "test.java");
		assertThat(run.runMixin.debugString, hasEntry("address", "5005"));
	}

	@Test
	void testBareDebugGetsConfigFallback() throws IOException {
		// Config-set debug should be used as fallback when --debug is bare
		String config = "run.debug = 9009\n";
		Files.write(jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS), config.getBytes());
		Configuration.instance(null);

		Run run = JBang.parseCommand("run", "--debug", "test.java");
		assertThat(run.runMixin.debugString, hasEntry("address", "9009"));
	}

	@Test
	void testDebugExplicitOverridesConfig() throws IOException {
		// Explicit value should override config
		String config = "run.debug = 9009\n";
		Files.write(jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS), config.getBytes());
		Configuration.instance(null);

		Run run = JBang.parseCommand("run", "--debug=7007", "test.java");
		assertThat(run.runMixin.debugString, hasEntry("address", "7007"));
	}

	@ParameterizedTest
	@CsvSource({ "-d5005, 5005", "-d=5005, 5005", "-d 5005, 5005", "--debug 5005, 5005",
			"--debug=5005, 5005", "-dlocalhost:5005, localhost:5005", "-d, 4004" })
	void testDebugShortAndLongForms(String debugArgs, String address) {
		List<String> args = new ArrayList<>();
		args.add("run");
		args.addAll(Arrays.asList(debugArgs.split(" ")));
		args.add("test.java");
		Run run = JBang.parseCommand(args.toArray(new String[0]));
		assertThat(run.runMixin.debugString, hasEntry("address", address));
		assertThat(run.scriptMixin.scriptOrFile, equalTo("test.java"));
	}

	@ParameterizedTest
	@CsvSource({ "-dfoo, foo", "-dx, x" })
	void testDebugInvalidAttachedValueFails(String arg, String value) {
		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> JBang.parseCommand("run", arg, "test.java"));
		Throwable cause = ex;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		assertThat(cause.getMessage(), containsString("Invalid value '" + value + "' for option -d/--debug"));
	}

	@ParameterizedTest
	@CsvSource({ "'-d5005,suspend=n,5006', 5006, n",
			"'--debug=address=4444,address=2323', 2323, ",
			"-d 5005 -d 6006, 6006, ",
			"-d 5005 -d suspend=n, 5005, n" })
	void testDebugValuesAreAppliedInOrder(String debugArgs, String address, String suspend) {
		// Debug values (within one comma-separated value and across repeated -d
		// options) are applied in order, so later entries override earlier ones.
		List<String> args = new ArrayList<>();
		args.add("run");
		args.addAll(Arrays.asList(debugArgs.split(" ")));
		args.add("test.java");
		Run run = JBang.parseCommand(args.toArray(new String[0]));
		assertThat(run.runMixin.debugString, hasEntry("address", address));
		if (suspend != null) {
			assertThat(run.runMixin.debugString, hasEntry("suspend", suspend));
		}
	}

	@Test
	void testBareDebugFollowedByOtherOption() {
		// a bare -d must not swallow the following option
		Run run = JBang.parseCommand("run", "-d", "--java", "21", "test.java");
		assertThat(run.runMixin.debugString, hasEntry("address", "4004"));
		assertThat(run.buildMixin.javaVersion, equalTo("21"));
		assertThat(run.scriptMixin.scriptOrFile, equalTo("test.java"));
	}

	@Test
	void testDebugNotSpecified() {
		Run run = JBang.parseCommand("run", "test.java");
		assertThat(run.runMixin.debugString, is(nullValue()));
	}

	// --- #2587: --runtime-option with space separator ---

	@Test
	void testRuntimeOptionWithSpaceSeparator() {
		// --runtime-option -Dstdout.encoding=UTF-8 should work (space between option
		// and value)
		Run run = JBang.parseCommand("run", "--runtime-option", "-Dstdout.encoding=UTF-8", "test.java");
		assertThat(run.runMixin.javaRuntimeOptions, hasSize(1));
		assertThat(run.runMixin.javaRuntimeOptions, contains("-Dstdout.encoding=UTF-8"));
	}

	@Test
	void testMultipleRuntimeOptionsWithSpaceSeparator() {
		// Multiple --runtime-option with space should all be parsed
		Run run = JBang.parseCommand("run",
				"--runtime-option", "-Dstdout.encoding=UTF-8",
				"--runtime-option", "-Dpython.console.encoding=UTF-8",
				"test.java");
		assertThat(run.runMixin.javaRuntimeOptions, hasSize(2));
		assertThat(run.runMixin.javaRuntimeOptions,
				contains("-Dstdout.encoding=UTF-8", "-Dpython.console.encoding=UTF-8"));
	}

	@Test
	void testRuntimeOptionShortWithSpaceSeparator() {
		// -R -Dkey=value (space between -R and value) should work
		Run run = JBang.parseCommand("run", "-R", "-Dstdout.encoding=UTF-8", "test.java");
		assertThat(run.runMixin.javaRuntimeOptions, hasSize(1));
		assertThat(run.runMixin.javaRuntimeOptions, contains("-Dstdout.encoding=UTF-8"));
	}

	// --- #2589: --deps comma-separated via DefaultValueProvider ---

	@Test
	void testDepsDefaultValueProviderCommaSeparated() throws IOException {
		// When run.deps is set in config with comma-delimited deps,
		// they should be split into separate list items (not treated as one GAV)
		String config = "run.deps = org.foo:bar:1.0,org.baz:qux:2.0\n";
		Files.write(jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS), config.getBytes());
		Configuration.instance(null);

		Run run = JBang.parseCommand("run", "test.java");
		assertThat(run.dependencyInfoMixin.dependencies, hasSize(2));
		assertThat(run.dependencyInfoMixin.dependencies,
				contains("org.foo:bar:1.0", "org.baz:qux:2.0"));
	}

	@Test
	void testDepsDefaultValueProviderSingleDep() throws IOException {
		// Single dep from config should still work
		String config = "run.deps = org.foo:bar:1.0\n";
		Files.write(jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS), config.getBytes());
		Configuration.instance(null);

		Run run = JBang.parseCommand("run", "test.java");
		assertThat(run.dependencyInfoMixin.dependencies, hasSize(1));
		assertThat(run.dependencyInfoMixin.dependencies, contains("org.foo:bar:1.0"));
	}

	@Test
	void testDepsExplicitOverridesDefaultValueProvider() throws IOException {
		// Explicit --deps should override config defaults
		String config = "run.deps = org.foo:bar:1.0,org.baz:qux:2.0\n";
		Files.write(jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS), config.getBytes());
		Configuration.instance(null);

		Run run = JBang.parseCommand("run", "--deps", "org.other:lib:3.0", "test.java");
		assertThat(run.dependencyInfoMixin.dependencies, hasItem("org.other:lib:3.0"));
	}

	// --- Command typo detection (#2628) ---

	@Test
	void testFindClosestSubcommand_closeTypo() {
		assertThat(Main.findClosestSubcommand("alis"), equalTo("alias"));
	}

	@Test
	void testFindClosestSubcommand_singleCharOff() {
		assertThat(Main.findClosestSubcommand("buidl"), equalTo("build"));
	}

	@Test
	void testFindClosestSubcommand_transposition() {
		assertThat(Main.findClosestSubcommand("rnу"), is(notNullValue()));
	}

	@Test
	void testFindClosestSubcommand_exactMatch() {
		// Exact match should return null (distance 0 is excluded)
		assertThat(Main.findClosestSubcommand("run"), is(nullValue()));
	}

	@Test
	void testFindClosestSubcommand_tooFar() {
		// Completely unrelated input should return null
		assertThat(Main.findClosestSubcommand("xyz"), is(nullValue()));
	}

	@Test
	void testFindClosestSubcommand_longerTypo() {
		assertThat(Main.findClosestSubcommand("completon"), equalTo("completion"));
	}

	@Test
	void testFindClosestSubcommand_missingChar() {
		assertThat(Main.findClosestSubcommand("edi"), equalTo("edit"));
	}

	@Test
	void testFindClosestSubcommand_extraChar() {
		assertThat(Main.findClosestSubcommand("initt"), equalTo("init"));
	}

	@ParameterizedTest
	@CsvSource({ "false, repl, repl", "true, repl, repl", "false, alis, alis", "true, alis, alis",
			"false, jbang-alis, alis", "true, jbang-alis, alis" })
	void testResolvedAliasDoesNotSuggestSubcommand(boolean userCatalog, String alias, String command) throws Exception {
		Files.writeString((userCatalog ? jbangTempDir : cwdDir).resolve(Catalog.JBANG_CATALOG_JSON),
				"{\"aliases\":{\"" + alias + "\":{\"script-ref\":\"./hello.java\"}}}");

		CaptureResult<String[]> result = captureOutput(
				() -> Main.handleDefaultRun(new String[] { command, "argument" }));

		assertThat(result.result, arrayContaining("run", alias, "argument"));
		assertThat(result.err, not(containsString("is not a jbang command")));
		assertThat(result.err, not(containsString("See 'jbang --help'")));
	}

	@Test
	void testUnresolvedTypoSuggestsSubcommand() throws Exception {
		CaptureResult<String[]> result = captureOutput(() -> Main.handleDefaultRun(new String[] { "alis" }));

		assertThat(result.result, arrayContaining("run", "alis"));
		assertThat(result.err, containsString("'alis' is not a jbang command. Did you mean 'alias'?"));
		assertThat(result.err, containsString("See 'jbang --help' for available commands."));
	}

	@ParameterizedTest
	@CsvSource({ "--aesh-completion, bash", "--aesh-doc, markdown", "--aesh-complete, --",
			"--aesh-completion-install, bash" })
	void testAeshBuiltinsPassThroughUnchanged(String builtin, String arg) {
		// aesh intercepts these only when they are args[0]; they must not be rewritten
		// into a default 'run' invocation.
		String[] result = Main.handleDefaultRun(new String[] { builtin, arg });
		assertThat(result, arrayContaining(builtin, arg));
	}

	// --- Subcommand list consistency ---

	@Test
	void testSubcommandNamesMatchAnnotation() {
		// Extract command names from @CommandDefinition on JBang
		CommandDefinition gcd = JBang.class.getAnnotation(CommandDefinition.class);
		assertThat("JBang must have @CommandDefinition", gcd, is(notNullValue()));

		Set<String> annotationNames = new HashSet<>();
		for (Class<?> cls : gcd.groupCommands()) {
			CommandDefinition cd = cls.getAnnotation(CommandDefinition.class);
			assertThat("Command class " + cls.getSimpleName() + " must have @CommandDefinition",
					cd, is(notNullValue()));
			annotationNames.add(cd.name());
		}

		assertThat("Main.getSubcommandNames() must match JBang @CommandDefinition.groupCommands",
				Main.getSubcommandNames(), equalTo(annotationNames));
	}
}
