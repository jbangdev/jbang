package dev.jbang.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.*;

import org.aesh.command.Command;
import org.aesh.command.CommandLifecycle;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

import dev.jbang.Configuration;
import dev.jbang.util.Util;

public abstract class BaseCommand implements Command<CommandInvocation>, CommandLifecycle {

	public static final int EXIT_OK = 0;
	public static final int EXIT_GENERIC_ERROR = 1;
	public static final int EXIT_INVALID_INPUT = 2;
	public static final int EXIT_UNEXPECTED_STATE = 3;
	public static final int EXIT_INTERNAL_ERROR = 4;
	public static final int EXIT_EXECUTE = 255;

	private static final Logger logger = Logger.getLogger("dev.jbang.cli");
	static {
		logger.setLevel(Level.SEVERE);
	}

	@Option(name = "config", description = "Path to config file to be used instead of the default")
	String configPath;

	@Option(name = "insecure", hasValue = false, description = "Enable insecure trust of all SSL certificates.")
	boolean insecure;

	@Option(name = "verbose", hasValue = false, inherited = true, exclusiveWith = {
			"quiet" }, description = "jbang will be verbose on what it does.")
	boolean verbose;

	@Option(name = "quiet", hasValue = false, inherited = true, exclusiveWith = {
			"verbose" }, description = "jbang will be quiet, only print when error occurs.")
	boolean quiet;

	@Option(shortName = 'o', name = "offline", hasValue = false, inherited = true, exclusiveWith = {
			"fresh" }, description = "Work offline. Fail-fast if dependencies are missing.")
	boolean offline;

	@Option(name = "fresh", hasValue = false, inherited = true, exclusiveWith = {
			"offline" }, description = "Make sure we use fresh (i.e. non-cached) resources.")
	boolean fresh;

	@Option(name = "preview", hasValue = false, inherited = true, description = "Enable jbang preview features")
	boolean preview;

	@Option(shortName = 'x', name = "stacktrace", hasValue = false, inherited = true, description = "Print exceptions stacktraces to stderr (even when quiet).")
	boolean printExceptions;

	protected CommandInvocation commandInvocation;

	void debug(String msg) {
		if (isVerbose()) {
			Util.verboseMsg(msg);
		}
	}

	void info(String msg) {
		if (!isQuiet()) {
			Util.infoMsg(msg);
		}
	}

	void warn(String msg) {
		if (!isQuiet()) {
			Util.warnMsg(msg);
		}
	}

	void error(String msg, Throwable th) {
		Util.errorMsg(msg, th);
	}

	boolean isVerbose() {
		return Util.isVerbose();
	}

	boolean isQuiet() {
		return Util.isQuiet();
	}

	static private void enableInsecure() {
		try {
			// Create a trust manager that does not validate certificate chains
			TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				public void checkClientTrusted(X509Certificate[] certs, String authType) {
				}

				public void checkServerTrusted(X509Certificate[] certs, String authType) {
				}
			}
			};

			// Install the all-trusting trust manager
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

			// Create all-trusting host name verifier
			HostnameVerifier allHostsValid = (hostname, session) -> true;

			// Install the all-trusting host verifier
			HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
		} catch (NoSuchAlgorithmException | KeyManagementException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Override
	public CommandResult execute(CommandInvocation commandInvocation) throws InterruptedException {
		this.commandInvocation = commandInvocation;

		try {
			int exitCode = doCall();
			return CommandResult.valueOf(exitCode);
		} catch (ExitException e) {
			if (e.getStatus() != 0 && e.getMessage() != null) {
				Util.errorMsg(null, e);
			}
			return CommandResult.valueOf(e.getStatus());
		} catch (IOException e) {
			Util.errorMsg(null, e);
			return CommandResult.valueOf(EXIT_GENERIC_ERROR);
		} catch (Exception e) {
			Util.errorMsg(null, e);
			return CommandResult.valueOf(EXIT_INTERNAL_ERROR);
		}
	}

	protected Integer missingSubcommand() {
		if (commandInvocation != null) {
			System.err.println(commandInvocation.getHelpInfo());
		}
		return EXIT_INVALID_INPUT;
	}

	@Override
	public void afterParse() {
		if (verbose) {
			Util.setVerbose(true);
		}
		if (quiet) {
			Util.setQuiet(true);
		}
		if (offline) {
			Util.setOffline(true);
		}
		if (fresh) {
			Util.setFresh(true);
		}
		if (preview) {
			Util.setPreview(true);
		}
		if (printExceptions) {
			Util.setPrintExceptions(true);
		}

		if (configPath != null) {
			Path config = Paths.get(configPath);
			if (Files.isReadable(config)) {
				Configuration.instance(Configuration.get(config));
			} else {
				warn("Configuration file does not exist or could not be read: " + config);
			}
		}

		if (insecure) {
			enableInsecure();
		}
	}

	public abstract Integer doCall() throws IOException;
}
