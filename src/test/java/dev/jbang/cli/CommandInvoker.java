package dev.jbang.cli;

/**
 * Test helper that runs a parsed command the same way {@code execute()} does:
 * first {@link BaseCommand#validateArguments()}, then
 * {@link BaseCommand#doCall()}.
 * <p>
 * Lives in the {@code dev.jbang.cli} package so it can reach the
 * package-visible {@code validateArguments()} hook without widening its
 * visibility in production code.
 */
public class CommandInvoker {

	private CommandInvoker() {
	}

	public static Integer validateAndCall(BaseCommand cmd) throws Exception {
		cmd.validateArguments();
		return cmd.doCall();
	}
}
