package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import org.aesh.command.CommandDefinition;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Configuration;
import dev.jbang.Main;
import dev.jbang.util.Util;

class TestAeshParsing extends BaseTest {

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
		assertThat(Util.isVerbose(Util.VerboseCategory.COMMANDS), is(true));
		assertThat(Util.isVerbose(Util.VerboseCategory.INTERNALS), is(true));
		assertThat(Util.isQuiet(), is(false));
	}

	@Test
	void testVerboseCategories() {
		JBang.parseCommand("run", "--verbose=commands,network", "test.java");
		assertThat(Util.isVerbose(Util.VerboseCategory.COMMANDS), is(true));
		assertThat(Util.isVerbose(Util.VerboseCategory.NETWORK), is(true));
		assertThat(Util.isVerbose(Util.VerboseCategory.BUILD), is(false));
		assertThat(Util.isVerbose(Util.VerboseCategory.INTERNALS), is(false));
	}

	@Test
	void testVerboseCategoryExclusion() {
		JBang.parseCommand("run", "--verbose=-internals", "test.java");
		assertThat(Util.isVerbose(Util.VerboseCategory.COMMANDS), is(true));
		assertThat(Util.isVerbose(Util.VerboseCategory.NETWORK), is(true));
		assertThat(Util.isVerbose(Util.VerboseCategory.INTERNALS), is(false));
	}

	@Test
	void testUnknownVerboseCategoryFails() {
		assertThrows(RuntimeException.class,
				() -> JBang.parseCommand("run", "--verbose=unknown", "test.java"));
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
	void testVerboseCategoriesFromConfig() throws IOException {
		Files.write(jbangTempDir.resolve(Configuration.JBANG_CONFIG_PROPS),
				"verbose = commands,network\n".getBytes());
		Configuration.instance(null);

		JBang.parseCommand("run", "test.java");
		assertThat(Util.isVerbose(Util.VerboseCategory.COMMANDS), is(true));
		assertThat(Util.isVerbose(Util.VerboseCategory.NETWORK), is(true));
		assertThat(Util.isVerbose(Util.VerboseCategory.INTERNALS), is(false));
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
	void testVerboseCategoryFiltersMessages() throws Exception {
		JBang.parseCommand("run", "--verbose=commands", "test.java");
		CaptureResult<Void> result = captureOutput(() -> {
			Util.verboseMsg(Util.VerboseCategory.COMMANDS, "command message");
			Util.verboseMsg(Util.VerboseCategory.NETWORK, "network message");
			Util.verboseMsg("internal message");
			return null;
		});
		assertThat(result.normalizedErr(), containsString("command message"));
		assertThat(result.normalizedErr(), not(containsString("network message")));
		assertThat(result.normalizedErr(), not(containsString("internal message")));
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
