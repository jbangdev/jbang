package dev.jbang.source.generators;

import static dev.jbang.source.builders.BaseBuilder.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.source.*;
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
		String args = String.join(" ", escapeOSArguments(fullArgs, shell));
		// Check if we need to use @-files on Windows
		boolean useArgsFile = false;
		if (args.length() > COMMAND_LINE_LENGTH_LIMIT && Util.getShell() != Util.Shell.bash) {
			// @file is only available from java 9 onwards.
			String requestedJavaVersion = ctx.getJavaVersion() != null ? ctx.getJavaVersion()
					: getCode().getJavaVersion().orElse(null);
			int actualVersion = JavaUtil.javaVersion(requestedJavaVersion);
			useArgsFile = actualVersion >= 9;
		}
		if (useArgsFile) {
			// @-files avoid problems on Windows with very long command lines
			final String javaCmd = escapeOSArgument(fullArgs.get(0), shell);
			final File argsFile = File.createTempFile("jbang", ".args");
			try (PrintWriter pw = new PrintWriter(argsFile)) {
				// write all arguments except the first to the file
				for (int i = 1; i < fullArgs.size(); ++i) {
					pw.println(escapeArgsFileArgument(fullArgs.get(i)));
				}
			}

			return javaCmd + " @" + argsFile;
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
