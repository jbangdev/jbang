package dev.jbang.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.jbang.Configuration;
import dev.jbang.util.Util;

import picocli.CommandLine;

public abstract class BaseCommand implements Callable<Integer> {

	public static final int EXIT_OK = 0;
	public static final int EXIT_GENERIC_ERROR = 1;
	public static final int EXIT_INVALID_INPUT = 2;
	public static final int EXIT_UNEXPECTED_STATE = 3;
	public static final int EXIT_INTERNAL_ERROR = 4;
	public static final int EXIT_EXECUTE = 255;

	private static final Logger logger = Logger.getLogger("org.jboss.shrinkwrap.resolver");
	static {
		logger.setLevel(Level.SEVERE);
	}

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	@CommandLine.Option(names = { "-h",
			"--help" }, usageHelp = true, description = "Display help/info. Use 'jbang <command> -h' for detailed usage.")
	boolean helpRequested;

	@CommandLine.Option(names = { "--config" }, description = "Path to config file to be used instead of the default")
	void setConfig(Path config) {
		if (Files.isReadable(config)) {
			Configuration.instance(Configuration.get(config));
		} else {
			warn("Configuration file does not exist or could not be read: " + config);
		}
	}

	void debug(String msg) {
		if (isVerbose()) {
			if (spec != null) {
				spec.commandLine().getErr().println("[jbang] " + msg);
			} else {
				Util.verboseMsg(msg);
			}
		}
	}

	void info(String msg) {
		if (!isQuiet()) {
			if (spec != null) {
				spec.commandLine().getErr().println("[jbang] " + msg);
			} else {
				Util.infoMsg(msg);
			}
		}
	}

	void warn(String msg) {
		if (!isQuiet()) {
			if (spec != null) {
				spec.commandLine().getErr().println("[jbang] " + msg);
			} else {
				Util.warnMsg(msg);
			}
		}
	}

	void error(String msg, Throwable th) {
		if (spec != null) {
			spec.commandLine().getErr().println("[jbang] " + msg);
		} else {
			Util.errorMsg(msg, th);
		}
	}

	boolean isVerbose() {
		return Util.isVerbose();
	}

	boolean isQuiet() {
		return Util.isQuiet();
	}

	@Override
	public Integer call() throws IOException {
		return doCall();
	}

	public abstract Integer doCall() throws IOException;
}
