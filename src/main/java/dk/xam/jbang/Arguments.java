package dk.xam.jbang;

import static picocli.CommandLine.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Command(versionProvider = VersionProvider.class)
class Arguments {

	@Option(names = {"-d", "--debug"}, arity = "0", description = "Launch with java debug enabled.")
	boolean debug;

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Display help/info")
	boolean helpRequested;

	@Option(names = {"--version"}, versionHelp = true, arity = "0", description = "Display version info")
	boolean versionRequested;

	@Parameters
	List<String> scripts;

	final List<String> argsForScript = new ArrayList<>();
	final List<String> argsForJbang = new ArrayList<>();

	Arguments(String... args) {
		argsForJbang(args);
	}
	/**
	 * return the list arguments that relate to jbang. First arguments that starts
	 * with '-' and is not just '-' for stdin goes to jbang, rest goes to the
	 * script.
	 *
	 * @param args
	 * @return the list arguments that relate to jbang
	 */
	void argsForJbang(String... args) {

		if (args.length > 0 && "--init".equals(args[0])) {
			argsForJbang.addAll(Arrays.asList(args));
			return;
		}

		boolean found = false;
		for (var a : args) {
			if (!found && a.startsWith("-") && a.length() > 1) {
				this.argsForJbang.add(a);
			} else {
				found = true;
				this.argsForScript.add(a);
			}
		}
	}
}
