package dev.jbang.cli;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.*;

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

	@CommandLine.Mixin
	HelpMixin helpMixin;

	@CommandLine.Option(names = { "--config" }, description = "Path to config file to be used instead of the default")
	void setConfig(Path config) {
		if (Files.isReadable(config)) {
			Configuration.instance(Configuration.get(config));
		} else {
			warn("Configuration file does not exist or could not be read: " + config);
		}
	}

	@CommandLine.Option(names = { "--insecure" }, description = "Enable insecure trust of all SSL certificates.")
	void setInsecure(boolean insecure) {
		if (insecure) {
			enableInsecure();
		}
	}

	public PrintStream realOut = new PrintStream(new FileOutputStream(FileDescriptor.out));

	void debug(String msg) {
		if (isVerbose()) {
			if (spec != null) {
				PrintWriter err = spec.commandLine().getErr();
				err.print(Util.getMsgHeader());
				err.println(msg);
			} else {
				Util.verboseMsg(msg);
			}
		}
	}

	void info(String msg) {
		if (!isQuiet()) {
			if (spec != null) {
				PrintWriter err = spec.commandLine().getErr();
				err.print(Util.getMsgHeader());
				err.println(msg);
			} else {
				Util.infoMsg(msg);
			}
		}
	}

	void warn(String msg) {
		if (!isQuiet()) {
			if (spec != null) {
				PrintWriter err = spec.commandLine().getErr();
				err.print(Util.getMsgHeader());
				err.println(msg);
			} else {
				Util.warnMsg(msg);
			}
		}
	}

	void error(String msg, Throwable th) {
		if (spec != null) {
			PrintWriter err = spec.commandLine().getErr();
			err.print(Util.getMsgHeader());
			err.println(msg);
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
	public Integer call() throws IOException {
		return doCall();
	}

	public abstract Integer doCall() throws IOException;
}
