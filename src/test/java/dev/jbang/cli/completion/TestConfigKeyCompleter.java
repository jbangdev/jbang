package dev.jbang.cli.completion;

import static dev.jbang.util.TestUtil.clearSettingsCaches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.aesh.command.completer.CompleterInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.Configuration;

public class TestConfigKeyCompleter extends BaseTest {

	static final String testConfig = "" +
			"one=footop\n" +
			"two=bar\n" +
			"custom.mykey=hello\n";

	Path testConfigFile = null;

	@BeforeEach
	void initEach() throws IOException {
		testConfigFile = cwdDir.resolve(Configuration.JBANG_CONFIG_PROPS);
		Files.write(testConfigFile, testConfig.getBytes());
		clearSettingsCaches();
	}

	@Test
	void testGetAvailableKeysContainsExtraOptions() {
		Map<String, String> keys = ConfigKeyCompleter.getAvailableKeys();
		assertThat(keys, hasKey("cache-evict"));
		assertThat(keys, hasKey("connection-timeout"));
	}

	@Test
	void testGetAvailableKeysContainsCliOptions() {
		Map<String, String> keys = ConfigKeyCompleter.getAvailableKeys();
		// These come from the CLI command tree — top-level jbang options
		assertThat(keys, hasKey("jbang.verbose"));
		assertThat(keys, hasKey("jbang.offline"));
		// Subcommand options should also appear
		assertThat("Keys should include subcommand options",
				keys.keySet().stream().anyMatch(k -> k.contains(".")), is(true));
	}

	@Test
	void testCompleteAllKeysOnEmptyInput() {
		ConfigKeyCompleter completer = new ConfigKeyCompleter();
		TestCompleterInvocation inv = new TestCompleterInvocation("");
		completer.complete(inv);

		List<String> values = stripDescriptions(inv.candidates);
		// Should contain both available keys and user-set keys
		assertThat(values, hasItem("cache-evict"));
		assertThat(values, hasItem("one"));
		assertThat(values, hasItem("two"));
		assertThat(values, hasItem("custom.mykey"));
	}

	@Test
	void testCompleteWithPartialMatch() {
		ConfigKeyCompleter completer = new ConfigKeyCompleter();
		TestCompleterInvocation inv = new TestCompleterInvocation("cache");
		completer.complete(inv);

		List<String> values = stripDescriptions(inv.candidates);
		assertThat(values, hasItem("cache-evict"));
		// Should not contain unrelated keys
		assertThat(values, not(hasItem("connection-timeout")));
		assertThat(values, not(hasItem("one")));
	}

	@Test
	void testCompleteWithPrefixMatchesUserDefined() {
		ConfigKeyCompleter completer = new ConfigKeyCompleter();
		TestCompleterInvocation inv = new TestCompleterInvocation("custom");
		completer.complete(inv);

		List<String> values = stripDescriptions(inv.candidates);
		assertThat(values, hasItem("custom.mykey"));
	}

	@Test
	void testUnsetCompleterOnlyShowsSetKeys() {
		ConfigUnsetKeyCompleter completer = new ConfigUnsetKeyCompleter();
		TestCompleterInvocation inv = new TestCompleterInvocation("");
		completer.complete(inv);

		List<String> values = stripDescriptions(inv.candidates);
		// Should contain keys from the config file and built-in defaults
		assertThat(values, hasItem("one"));
		assertThat(values, hasItem("two"));
		assertThat(values, hasItem("custom.mykey"));
	}

	@Test
	void testUnsetCompleterPartialMatch() {
		ConfigUnsetKeyCompleter completer = new ConfigUnsetKeyCompleter();
		TestCompleterInvocation inv = new TestCompleterInvocation("o");
		completer.complete(inv);

		List<String> values = stripDescriptions(inv.candidates);
		assertThat(values, hasItem("one"));
		assertThat(values, not(hasItem("two")));
	}

	@Test
	void testSingleCandidateStripsDescription() {
		ConfigKeyCompleter completer = new ConfigKeyCompleter();
		TestCompleterInvocation inv = new TestCompleterInvocation("cache-ev");
		completer.complete(inv);

		// Single candidate should have description stripped
		assertThat(inv.candidates, hasSize(1));
		assertThat(inv.candidates.get(0), not(containsString("\t")));
		assertThat(inv.candidates.get(0), equalTo("cache-evict"));
	}

	@Test
	void testCompletionIncludesDescriptions() {
		ConfigKeyCompleter completer = new ConfigKeyCompleter();
		TestCompleterInvocation inv = new TestCompleterInvocation("cache");
		// Add a second match so descriptions are preserved
		TestCompleterInvocation inv2 = new TestCompleterInvocation("c");
		completer.complete(inv2);

		// With multiple candidates, descriptions should be present
		if (inv2.candidates.size() > 1) {
			boolean hasDescription = inv2.candidates.stream().anyMatch(c -> c.contains("\t"));
			assertThat("Candidates should include descriptions", hasDescription);
		}
	}

	private List<String> stripDescriptions(List<String> candidates) {
		List<String> result = new ArrayList<>();
		for (String c : candidates) {
			int tab = c.indexOf('\t');
			result.add(tab >= 0 ? c.substring(0, tab) : c);
		}
		return result;
	}

	/**
	 * Minimal CompleterInvocation stub for testing.
	 */
	static class TestCompleterInvocation implements CompleterInvocation {
		final String partial;
		final List<String> candidates = new ArrayList<>();
		boolean appendSpace = true;
		boolean ignoreStartsWith = false;
		boolean ignoreOffset = false;
		int offset = -1;

		TestCompleterInvocation(String partial) {
			this.partial = partial;
		}

		@Override
		public String getGivenCompleteValue() {
			return partial;
		}

		@Override
		public org.aesh.command.Command getCommand() {
			return null;
		}

		@Override
		public java.util.List<org.aesh.terminal.formatting.TerminalString> getCompleterValues() {
			java.util.List<org.aesh.terminal.formatting.TerminalString> result = new ArrayList<>();
			for (String c : candidates) {
				result.add(new org.aesh.terminal.formatting.TerminalString(c));
			}
			return result;
		}

		@Override
		public void setCompleterValues(java.util.Collection<String> values) {
			candidates.clear();
			candidates.addAll(values);
		}

		@Override
		public void setCompleterValuesTerminalString(
				java.util.List<org.aesh.terminal.formatting.TerminalString> values) {
			candidates.clear();
			for (org.aesh.terminal.formatting.TerminalString ts : values) {
				candidates.add(ts.toString());
			}
		}

		@Override
		public void clearCompleterValues() {
			candidates.clear();
		}

		@Override
		public void addAllCompleterValues(java.util.Collection<String> values) {
			candidates.addAll(values);
		}

		@Override
		public void addCompleterValue(String value) {
			candidates.add(value);
		}

		@Override
		public void addCompleterValueTerminalString(org.aesh.terminal.formatting.TerminalString value) {
			candidates.add(value.toString());
		}

		@Override
		public void setAppendSpace(boolean b) {
			appendSpace = b;
		}

		@Override
		public boolean isAppendSpace() {
			return appendSpace;
		}

		@Override
		public void setIgnoreStartsWith(boolean b) {
			ignoreStartsWith = b;
		}

		@Override
		public boolean isIgnoreStartsWith() {
			return ignoreStartsWith;
		}

		@Override
		public void setIgnoreOffset(boolean b) {
			ignoreOffset = b;
		}

		@Override
		public boolean doIgnoreOffset() {
			return ignoreOffset;
		}

		@Override
		public void setOffset(int i) {
			offset = i;
		}

		@Override
		public int getOffset() {
			return offset;
		}

		@Override
		public org.aesh.console.AeshContext getAeshContext() {
			return null;
		}
	}
}
