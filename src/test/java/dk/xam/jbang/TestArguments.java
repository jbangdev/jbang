package dk.xam.jbang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

class TestArguments {

	@Test
	public void testBasicArguments() {
		var arg = new Arguments();
		new CommandLine(arg).parseArgs("-h", "--debug", "myfile.java");

		assert arg.helpRequested;
		assert arg.debug;
		assertEquals(1, arg.scripts.size());

	}

	@Test
	public void testArgumentsForJbang() {

		var a = new Arguments("test.java");
		assertEquals(0, a.argsForJbang.size());

		a = new Arguments("--debug", "test.java");
		assertEquals(1, a.argsForJbang.size());

		a = new Arguments("test.java", "-h");
		assertEquals(0, a.argsForJbang.size());

		a = new Arguments("-", "--help");
		assertEquals(0, a.argsForJbang.size());

		a = new Arguments("--init", "x.java", "y.java");
		assertEquals(3, a.argsForJbang.size());

	}

}
