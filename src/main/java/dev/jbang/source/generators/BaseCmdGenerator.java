package dev.jbang.source.generators;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.source.*;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.Util;

public abstract class BaseCmdGenerator<T extends CmdGenerator> implements CmdGenerator {
	protected final Project project;
	protected final BuildContext ctx;

	protected List<String> arguments = Collections.emptyList();
	protected String debugString;
	protected String flightRecorderString;

	protected Util.Shell shell = Util.getShell();

	// 8192 character command line length limit imposed by CMD.EXE
	protected static final int COMMAND_LINE_LENGTH_LIMIT = 8000;

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
	public T debugString(String debugString) {
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

	public BaseCmdGenerator(Project prj, BuildContext ctx) {
		this.project = prj;
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
		return cb.asCommandLine(shell);
	}

	protected void addAgentsArgs(List<String> fullArgs) {
		project
				.getJavaAgents()
				.forEach(aprj -> {
					// for now we don't include any transitive dependencies. could consider putting
					// on bootclasspath...or not.
					String jar;
					BuildContext actx = ctx.forSubProject(aprj, "agents");
					if (actx.getJarFile() != null) {
						jar = actx.getJarFile().toString();
					} else if (aprj.isJar()) {
						jar = aprj.getResourceRef().getFile().toString();
						// should we log a warning/error if agent jar not present ?
					} else {
						jar = null;
					}
					if (jar == null) {
						throw new ExitException(BaseCommand.EXIT_INTERNAL_ERROR,
								"No jar found for agent " + aprj.getResourceRef().getOriginalResource());
					}
					List<String> opts = aprj.getRuntimeOptions()
											.stream()
											.map(s -> s.replace("$JAR$", jar))
											.collect(Collectors.toList());
					fullArgs.addAll(opts);
				});
	}
}
