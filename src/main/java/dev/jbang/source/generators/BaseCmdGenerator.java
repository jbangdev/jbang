package dev.jbang.source.generators;

import static dev.jbang.source.builders.BaseBuilder.*;

import java.io.IOException;
import java.util.*;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.source.*;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public abstract class BaseCmdGenerator implements CmdGenerator {
	protected final RunContext ctx;

	protected Util.Shell shell = Util.getShell();

	// 8192 character command line length limit imposed by CMD.EXE
	protected static final int COMMAND_LINE_LENGTH_LIMIT = 8000;

	public BaseCmdGenerator setShell(Util.Shell shell) {
		this.shell = shell;
		return this;
	}

	public BaseCmdGenerator(RunContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public String generate() throws IOException {
		List<String> fullArgs = generateCommandLineList();
		CommandBuffer cb = CommandBuffer.of(fullArgs);
		String args = cb.asCommandLine(shell);
		// Check if we can and need to use @-files on Windows
		boolean useArgsFile = false;
		if (!(getCode().isJShell() || ctx.getForceType() == Source.Type.jshell) &&
				args.length() > COMMAND_LINE_LENGTH_LIMIT && Util.getShell() != Util.Shell.bash) {
			// @file is only available from java 9 onwards.
			String requestedJavaVersion = ctx.getJavaVersionOr(getCode());
			int actualVersion = JavaUtil.javaVersion(requestedJavaVersion);
			useArgsFile = actualVersion >= 9;
		}
		if (useArgsFile) {
			return cb.asJavaArgsFile(shell);
		} else {
			return args;
		}
	}

	protected abstract Code getCode();

	protected abstract List<String> generateCommandLineList() throws IOException;

	protected void addAgentsArgs(List<String> fullArgs) {
		ctx	.getJavaAgents()
			.forEach(agent -> {
				// for now we don't include any transitive dependencies. could consider putting
				// on bootclasspath...or not.
				String jar = null;
				Code asrc = agent.source;
				if (asrc.getJarFile() != null) {
					jar = asrc.getJarFile().toString();
				} else if (asrc.isJar()) {
					jar = asrc.getResourceRef().getFile().toString();
					// should we log a warning/error if agent jar not present ?
				}
				if (jar == null) {
					throw new ExitException(BaseCommand.EXIT_INTERNAL_ERROR,
							"No jar found for agent " + asrc.getResourceRef().getOriginalResource());
				}
				fullArgs.add("-javaagent:" + jar
						+ (agent.context.getJavaAgentOption() != null
								? "=" + agent.context.getJavaAgentOption()
								: ""));

			});
	}
}
