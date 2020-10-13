package dev.jbang.cli;

import java.io.IOException;
import java.io.PrintStream;

import picocli.AutoComplete;
import picocli.CommandLine;

@CommandLine.Command(name = "completion", description = "Output auto-completion script for bash/zsh.\nUsage: source <(jbang completion)")
public class Completion extends BaseCommand {

	@Override
	public Integer doCall() throws IOException {
		return completion();
	}

	public int completion() throws IOException {
		String script = AutoComplete.bash(
				spec.parent().name(),
				spec.parent().commandLine());
		// not PrintWriter.println: scripts with Windows line separators fail in strange
		// ways!

		PrintStream out = System.out;
		out.print(script);
		out.print('\n');
		out.flush();
		return EXIT_OK;
	}
}
