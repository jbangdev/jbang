package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;
import static java.lang.System.lineSeparator;

import org.junit.jupiter.api.Test;

public class InfoIT extends AbstractHelpBaseIT {

	@Test
	public void shouldPrintNiceDocs() {
		assertThat(shell("jbang info docs docsexample.java"))
			.outContains("This is a description")
			.errIsExactly("[jbang] Use --open to open the documentation file in the default browser." + lineSeparator())
			.outContains("main:")
			.outContains("  https://xam.dk/notthere")
			.outContains("  does-not-exist.txt (not found)")
			.outContains("javadoc:")
			.outContains("  /tmp/this_exists.txt")
			.succeeded();
	}

	@Override
	protected String commandName() {
		return "info";
	}
}