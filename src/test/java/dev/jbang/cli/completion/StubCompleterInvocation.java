package dev.jbang.cli.completion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.aesh.command.Command;
import org.aesh.command.completer.CompleterInvocation;
import org.aesh.console.AeshContext;
import org.aesh.terminal.formatting.TerminalString;

/**
 * Minimal stub of {@link CompleterInvocation} for unit-testing completers.
 */
class StubCompleterInvocation implements CompleterInvocation {
	private final String given;
	private final List<String> values = new ArrayList<>();
	private boolean appendSpace = true;

	StubCompleterInvocation(String given) {
		this.given = given;
	}

	@Override
	public String getGivenCompleteValue() {
		return given;
	}

	@Override
	public Command getCommand() {
		return null;
	}

	@Override
	public List<TerminalString> getCompleterValues() {
		return new ArrayList<>(); // unused in tests
	}

	@Override
	public void setCompleterValues(Collection<String> v) {
		values.clear();
		values.addAll(v);
	}

	@Override
	public void setCompleterValuesTerminalString(List<TerminalString> v) {
		values.clear();
		v.forEach(ts -> values.add(ts.getCharacters()));
	}

	@Override
	public void clearCompleterValues() {
		values.clear();
	}

	@Override
	public void addAllCompleterValues(Collection<String> v) {
		values.addAll(v);
	}

	@Override
	public void addCompleterValue(String value) {
		values.add(value);
	}

	@Override
	public void addCompleterValueTerminalString(TerminalString value) {
		values.add(value.getCharacters());
	}

	@Override
	public boolean isAppendSpace() {
		return appendSpace;
	}

	@Override
	public void setAppendSpace(boolean b) {
		this.appendSpace = b;
	}

	@Override
	public void setIgnoreOffset(boolean b) {
	}

	@Override
	public boolean doIgnoreOffset() {
		return false;
	}

	@Override
	public void setOffset(int offset) {
	}

	@Override
	public int getOffset() {
		return 0;
	}

	@Override
	public void setIgnoreStartsWith(boolean b) {
	}

	@Override
	public boolean isIgnoreStartsWith() {
		return false;
	}

	@Override
	public AeshContext getAeshContext() {
		return null;
	}

	List<String> getValues() {
		return values;
	}
}
