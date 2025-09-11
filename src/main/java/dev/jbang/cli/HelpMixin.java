package dev.jbang.cli;

import picocli.CommandLine;

public class HelpMixin {

	@CommandLine.Option(names = { "-h",
			"--help" }, usageHelp = true, description = "Display help/info. Use 'jbang <command> -h' for detailed usage.")
	boolean helpRequested;
}
