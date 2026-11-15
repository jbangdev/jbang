package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import dev.jbang.ExitException;
import dev.jbang.Main;

class TestCrema {
	@Test
	void parsesInheritedRunOptionsAndApplicationArguments() {
		Crema command = JBang.parseCommand("crema", "--main=example.Main", "app.java", "--flag", "two words");
		assertInstanceOf(Crema.class, command);
		assertEquals("example.Main", command.buildMixin.main);
		assertEquals("app.java", command.scriptMixin.scriptOrFile);
		assertEquals(Arrays.asList("--flag", "two words"), command.userParams);
		assertEquals("crema", Main.handleDefaultRun(new String[] { "crema", "app.java" })[0]);
	}

	@Test
	void ordinaryRuntimeFailsBeforeResolvingSource() {
		Crema command = JBang.parseCommand("crema", "does-not-exist.java");
		ExitException failure = assertThrows(ExitException.class, command::doCall);
		assertEquals(ExitException.EXIT_INVALID_INPUT, failure.getStatus());
		assertTrue(failure.getMessage().contains("nativeImageCrema"));
	}

	@Test
	void requiresScript() {
		Crema command = JBang.parseCommand("crema");
		ExitException failure = assertThrows(ExitException.class, command::doCall);
		assertTrue(failure.getMessage().contains("Missing required parameter"));
	}
}
