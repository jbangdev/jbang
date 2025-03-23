package dev.jbang.it;

import java.util.Arrays;
import java.util.List;

class CommandResult {
	List<String> command;

	String out;
	String err;
	int exit;

	public CommandResult(String out, String err, int exit, String... command) {
		this(out, err, exit, Arrays.asList(command));

	}

	public CommandResult(String out, String err, int exit, List<String> command) {
		this.command = command;
		this.out = out;
		this.err = err;
		this.exit = exit;
	}

	public List<String> command() {
		return command;
	}

	public String out() {
		return out;
	}

	public String err() {
		return err;
	}

	public int exitCode() {
		return exit;
	}

	@Override
	public String toString() {
		return String.format("Command: [%s]\nexit: %s\nout: %s\nerr: %s", command, exit, out, err);
	}
}