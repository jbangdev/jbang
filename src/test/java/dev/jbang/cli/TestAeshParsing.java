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

	// --- Hidden options not in help ---

	@Test
	void testHiddenOptionNotInHelp() throws Exception {
		CaptureResult<Integer> result = captureOutput(() -> JBang.execute("run", "--help"));
		String output = result.normalizedOut() + result.normalizedErr();
		assertThat(output, not(containsString("jdk-providers")));
	}

	// --- Help output contains expected options ---

	@Test
	void testHelpShowsVisibleOptions() throws Exception {
		CaptureResult<Integer> result = captureOutput(() -> JBang.execute("run", "--help"));
		String output = result.normalizedOut() + result.normalizedErr();
		assertThat(output, containsString("--verbose"));
		assertThat(output, containsString("--quiet"));
		assertThat(output, containsString("--offline"));
		assertThat(output, containsString("--fresh"));
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
