package dk.xam.jbang;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

class TestArguments {

	@Test
	public void testBasicArguments() {
		var arg = new Main();
		new CommandLine(arg).parseArgs("-h", "--debug", "myfile.java");

		assert arg.helpRequested;
		assert arg.debug;
		assertEquals(1, arg.params.size());

	}

	@Test
	public void testArgumentsForJbang() {

		var a = Main.argsForJbang("test.java");
		assertThat(a, is(new String[] {"test.java"}));

		a = Main.argsForJbang("--debug", "test.java");
		assertThat(a, is(new String[]{"--debug", "--", "test.java"}));

		a = Main.argsForJbang("test.java", "-h");
		assertThat(a, is(new String[]{"test.java", "-h"}));

		a = Main.argsForJbang("-", "--help");
		assertThat(a, is(new String[]{"-", "--help"}));

		a = Main.argsForJbang("--init", "x.java", "y.java");
		assertThat(a, is(new String[]{"--init", "--", "x.java", "y.java"}));

		a = Main.argsForJbang("--debug", "test.java", "--debug", "wonka");
		assertThat(a, is(new String[]{"--debug", "--", "test.java", "--debug", "wonka"}));

	}

}
