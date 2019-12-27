package dk.xam.jbang;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestArguments {

	@Test
	public void testBasicArguments() {
		var arg = new Arguments();
		new CommandLine(arg).parseArgs("-h --debug myfile.java");

		assert !arg.helpRequested;
		assert arg.debug;
		assertEquals(1,arg.scripts.size());

	}
}
