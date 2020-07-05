package dev.jbang.cli;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import picocli.CommandLine;
import picocli.CommandLine.UnmatchedArgumentException;

public class DeprecatedMessageHandler implements CommandLine.IParameterExceptionHandler {
	private final CommandLine.IParameterExceptionHandler delegate;

	public DeprecatedMessageHandler(CommandLine.IParameterExceptionHandler parameterExceptionHandler) {
		this.delegate = parameterExceptionHandler;
	}

	static Map<String, String> oldFlags = new HashMap<String, String>() {
		{
			put("--alias", "jbang alias --help");
			put("--init", "jbang init --help");
			put("--edit", "jbang edit --help");
			put("--edit-live", "jbang edit --help");
			put("--trust", "jbang trust --help");
		}
	};

	@Override
	public int handleParseException(CommandLine.ParameterException ex, String[] args) throws Exception {

		if (ex instanceof UnmatchedArgumentException) {
			CommandLine cmd = ex.getCommandLine();
			PrintWriter writer = cmd.getErr();

			UnmatchedArgumentException uae = (UnmatchedArgumentException) ex;
			String s = uae.getUnmatched().get(0);
			if (s.contains("=")) {
				s = s.substring(0, s.indexOf("="));
			}

			if (oldFlags.containsKey(s)) {
				writer.printf("%s is a deprecated and now removed flag. See " + oldFlags.get(s)
						+ " for more details on its replacement.\n", s);
			}
		}
		return delegate.handleParseException(ex, args);
	}
}
