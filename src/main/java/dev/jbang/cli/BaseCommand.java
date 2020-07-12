package dev.jbang.cli;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.jbang.ExitException;

import picocli.CommandLine;

public abstract class BaseCommand implements Callable<Integer> {

	static {
		Logger logger = Logger.getLogger("org.jboss.shrinkwrap.resolver");
		logger.setLevel(Level.SEVERE);
	}

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	@CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display help/info")
	boolean helpRequested;

	static boolean verbose = false;

	void info(String msg) {
		spec.commandLine().getErr().println("[jbang] " + msg);
	}

	void warn(String msg) {
		info("[WARNING] " + msg);
	}

	boolean isVerbose() {
		return verbose;
	}

	@Override
	public Integer call() throws IOException {
		try {
			return doCall();
		} catch (ExitException e) {
			if (isVerbose()) {
				e.printStackTrace();
			} else {
				info(e.getMessage());
				info("Run with --verbose for more details");
			}

			return e.getStatus();
		}
	}

	public abstract Integer doCall() throws IOException;
}
