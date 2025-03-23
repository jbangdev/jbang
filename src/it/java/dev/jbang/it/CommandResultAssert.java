package dev.jbang.it;

import org.assertj.core.api.AbstractAssert;

public class CommandResultAssert extends AbstractAssert<CommandResultAssert, CommandResult> {

	public CommandResultAssert(CommandResult actual) {
		super(actual, CommandResultAssert.class);
	}

	public static CommandResultAssert assertThat(CommandResult actual) {
		return new CommandResultAssert(actual);
	}

	public CommandResultAssert failed() {
		isNotNull();
		if (actual.exit == 0) {
			failWithMessage("Expected exit code to not be 0 but was <%s>", actual.exit);
		}
		return this;
	}

	public CommandResultAssert succeeded() {
		return exitedWith(0);
	}

	public CommandResultAssert exitedWith(int expected) {
		isNotNull();
		if (actual.exit != expected) {
			failWithMessage("Expected exit code to be <%s> but was <%s>", expected, actual.exit);
		}
		return this;
	}

	public CommandResultAssert hasExitCode(int expected) {
		return exitedWith(expected);
	}

	public CommandResultAssert hasErrorContaining(String substring) {
		isNotNull();
		if (!actual.err.contains(substring)) {
			failWithMessage("Expected error output to contain <%s> but was <%s>", substring, actual.err);
		}
		return this;
	}

	public CommandResultAssert errContains(String substring) {
		return hasErrorContaining(substring);
	}

	public CommandResultAssert errEquals(String expected) {
		isNotNull();
		if (!actual.err.equals(expected)) {
			failWithMessage("Expected error output to be <%s> but was <%s>", expected, actual.err);
		}
		return this;
	}

	public CommandResultAssert outEquals(String expected) {
		isNotNull();
		if (!actual.out.equals(expected)) {
			failWithMessage("Expected output to be <%s> but was <%s>", expected, actual.out);
		}
		return this;
	}

	public CommandResultAssert outContains(String substring) {
		isNotNull();
		if (!actual.out.contains(substring)) {
			failWithMessage("Expected output to contain <%s> but was <%s>", substring, actual.out);
		}
		return this;
	}

    public CommandResultAssert errNotContains(String string) {
        isNotNull();
		if (actual.err.contains(string)) {
			failWithMessage("Expected error output to not contain <%s> but was <%s>", string, actual.err);
		}
		return this;
    }
}