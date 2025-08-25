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

	@CommandLine.Option(names = { "-s",
			"--shell" }, description = {
					"Generate bash/zsh or fish completion script for ${ROOT-COMMAND-NAME:-the root command of this command}.",
					"Run the following command to give `${ROOT-COMMAND-NAME:-$PARENTCOMMAND}` TAB completion in the current shell:",
					"",
					"  bash/zsh: source <(${PARENT-COMMAND-FULL-NAME:-$PARENTCOMMAND} ${COMMAND-NAME})",
					"",
					"  fish: eval (<(${PARENT-COMMAND-FULL-NAME:-$PARENTCOMMAND} ${COMMAND-NAME} --shell fish)" })
	private String shell = "bash";

	public int completion() throws IOException {

		String script;
		if (shell.equals("bash")) {
			script = AutoComplete.bash(
					spec.parent().name(),
					spec.parent().commandLine());
		} else if (shell.equals("fish")) {
			script = AutoComplete.fish(
					spec.parent().name(),
					spec.parent().commandLine());
		} else {
			throw new IllegalArgumentException("Unsupported shell: " + shell);
		}

		// not PrintWriter.println: scripts with Windows line separators fail in strange
		// ways!

		PrintStream out = System.out;
		out.print(script);
		out.print('\n');
		out.flush();
		return EXIT_OK;
	}
}
