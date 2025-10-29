package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static java.lang.System.lineSeparator;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import dev.jbang.util.JavaUtil;

public class CacheIT extends AbstractHelpBaseIT {

// 	Feature: cache

// Scenario: clear cache default
// When command('jbang cache clear')
// * match exit == 0
// * match err == "[jbang] Clearing cache for urls\n[jbang] Clearing cache for jars\n[jbang] Clearing cache for kotlincs\n[jbang] Clearing cache for groovycs\n[jbang] Clearing cache for scripts\n[jbang] Clearing cache for stdins\n[jbang] Clearing cache for deps\n"
	@Test
	public void clearCacheDefault() {
		assertThat(shell(
				"jbang cache clear"))
			.succeeded()
			.errContains(
					"[jbang] Clearing cache for urls\n[jbang] Clearing cache for jars\n[jbang] Clearing cache for scripts\n[jbang] Clearing cache for stdins\n[jbang] Clearing cache for deps\n"
						.replace(
								"\n", lineSeparator()));
	}

// Scenario: clear cache default
// When command('jbang cache clear --all')
// * match exit == 0
// * match err == "[jbang] Clearing cache for urls\n[jbang] Clearing cache for jars\n[jbang] Clearing cache for jdks\n[jbang] Clearing cache for kotlincs\n[jbang] Clearing cache for groovycs\n[jbang] Clearing cache for projects\n[jbang] Clearing cache for scripts\n[jbang] Clearing cache for stdins\n[jbang] Clearing cache for deps\n"
	@Test
	public void clearCacheAll() {
		assumeTrue(!JavaUtil.inNativeImage()); // remove when https://github.com/jbangdev/jbang-devkitman/issues/87

		assertThat(shell("jbang cache clear --all"))
			.succeeded()
			.errContains(
					"[jbang] Clearing cache for urls\n[jbang] Clearing cache for jars\n[jbang] Clearing cache for jdks\n[jbang] Clearing cache for kotlincs\n[jbang] Clearing cache for groovycs\n[jbang] Clearing cache for projects\n[jbang] Clearing cache for scripts\n[jbang] Clearing cache for stdins\n[jbang] Clearing cache for deps\n"
						.replace(
								"\n", lineSeparator()));
	}

// Scenario: clear cache default
// When command('jbang cache clear --all --no-jdk --no-url --no-jar --no-project --no-script --no-stdin --no-deps --no-kotlinc --groovyc')
// * match exit == 0
// * match err == ""
	@Test
	public void clearNoCache() {
		assertThat(shell(
				"jbang cache clear --all --no-jdk --no-url --no-jar --no-project --no-script --no-stdin --no-deps --no-kotlinc --groovyc"))
			.succeeded()
			.errContains(
					"");
	}

	@Override
	protected String commandName() {
		return "cache";
	}
}