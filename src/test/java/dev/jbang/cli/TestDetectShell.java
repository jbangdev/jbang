package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.aesh.util.completer.ShellCompletionGenerator.ShellType;
import org.junit.jupiter.api.Test;

class TestDetectShell {

	@Test
	void testDetectBashFromVersion() {
		Map<String, String> env = new HashMap<>();
		env.put("BASH_VERSION", "5.2.15");
		assertEquals(ShellType.BASH, Completion.detectShell(env::get));
	}

	@Test
	void testDetectZshFromVersion() {
		Map<String, String> env = new HashMap<>();
		env.put("ZSH_VERSION", "5.9");
		assertEquals(ShellType.ZSH, Completion.detectShell(env::get));
	}

	@Test
	void testDetectFishFromVersion() {
		Map<String, String> env = new HashMap<>();
		env.put("FISH_VERSION", "3.6.1");
		assertEquals(ShellType.FISH, Completion.detectShell(env::get));
	}

	@Test
	void testDetectPwshFromPSModulePath() {
		Map<String, String> env = new HashMap<>();
		env.put("PSModulePath", "/some/path");
		assertEquals(ShellType.PWSH, Completion.detectShell(env::get));
	}

	@Test
	void testPwshTakesPriorityOverBashVersion() {
		// On macOS/Linux the bash wrapper script sets BASH_VERSION even
		// when the user's shell is PowerShell. PSModulePath must win.
		Map<String, String> env = new HashMap<>();
		env.put("PSModulePath", "/some/path");
		env.put("BASH_VERSION", "5.2.15");
		assertEquals(ShellType.PWSH, Completion.detectShell(env::get));
	}

	@Test
	void testDetectBashFromShellVar() {
		Map<String, String> env = new HashMap<>();
		env.put("SHELL", "/bin/bash");
		assertEquals(ShellType.BASH, Completion.detectShell(env::get));
	}

	@Test
	void testDetectZshFromShellVar() {
		Map<String, String> env = new HashMap<>();
		env.put("SHELL", "/bin/zsh");
		assertEquals(ShellType.ZSH, Completion.detectShell(env::get));
	}

	@Test
	void testDetectFishFromShellVar() {
		Map<String, String> env = new HashMap<>();
		env.put("SHELL", "/usr/bin/fish");
		assertEquals(ShellType.FISH, Completion.detectShell(env::get));
	}

	@Test
	void testDetectPwshFromShellVar() {
		Map<String, String> env = new HashMap<>();
		env.put("SHELL", "/usr/local/bin/pwsh");
		assertEquals(ShellType.PWSH, Completion.detectShell(env::get));
	}

	@Test
	void testReturnsNullForEmptyEnv() {
		Map<String, String> env = new HashMap<>();
		assertNull(Completion.detectShell(env::get));
	}

	@Test
	void testReturnsNullForUnknownShell() {
		Map<String, String> env = new HashMap<>();
		env.put("SHELL", "/bin/csh");
		assertNull(Completion.detectShell(env::get));
	}
}
