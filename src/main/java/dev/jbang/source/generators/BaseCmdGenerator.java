package dev.jbang.source.generators;

import java.io.IOException;
import java.util.*;

import dev.jbang.source.*;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.Util;

public abstract class BaseCmdGenerator<T extends CmdGenerator> implements CmdGenerator {
	protected final BuildContext ctx;

	protected List<String> arguments = Collections.emptyList();
	protected Map<String, String> debugString;
	protected String flightRecorderString;

	protected Util.Shell shell = Util.getShell();

	@SuppressWarnings("unchecked")
	public T arguments(List<String> arguments) {
		this.arguments = arguments != null ? arguments : Collections.emptyList();
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T shell(Util.Shell shell) {
		this.shell = shell;
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T debugString(Map<String, String> debugString) {
		this.debugString = debugString != null && !debugString.isEmpty() ? debugString : null;
		return (T) this;
	}

	@SuppressWarnings("unchecked")
	public T flightRecorderString(String flightRecorderString) {
		this.flightRecorderString = flightRecorderString != null && !flightRecorderString.isEmpty()
				? flightRecorderString
				: null;
		return (T) this;
	}

	public BaseCmdGenerator(BuildContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public String generate() throws IOException {
		List<String> fullArgs = generateCommandLineList();
		return generateCommandLineString(fullArgs);
	}

	protected abstract List<String> generateCommandLineList() throws IOException;

	protected String generateCommandLineString(List<String> fullArgs) throws IOException {
		CommandBuffer cb = CommandBuffer.of(fullArgs);
		return cb.shell(shell).asCommandLine();
	}
}
