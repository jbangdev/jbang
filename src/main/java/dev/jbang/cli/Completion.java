package dev.jbang.cli;

import java.io.IOException;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.aesh.util.completer.ShellCompletionGenerator;
import org.aesh.util.completer.ShellCompletionGenerator.ShellType;

@CommandDefinition(name = "completion", description = "Generate bash/zsh or fish completion script for ${ROOT-COMMAND-NAME}.", generateHelp = true)
public class Completion extends BaseCommand {

	@Override
	public Integer doCall() throws IOException {
		return completion();
	}

	@Option(shortName = 's', name = "shell", description = "The shell to generate the completion script for. Supported shells: bash, zsh, and fish")
	private ShellType shellType = ShellType.BASH;

	public int completion() throws IOException {
		try {
			String script = ShellCompletionGenerator.generateDynamic(shellType, JBang.class, "jbang");
			if (shellType == ShellType.FISH) {
				// Workaround for aesh bug: the generated fish script uses
				// commandline -cop which excludes the current token.
				// Replace with a fixed version that uses commandline -ct.
				// See https://github.com/aeshell/aesh/issues/436
				script = fixFishCompletionScript(script);
			}
			System.out.println(script);
		} catch (Exception e) {
			throw new ExitException(EXIT_INTERNAL_ERROR, "Failed to generate completion script: " + e.getMessage(),
					e);
		}

		return EXIT_OK;
	}

	/**
	 * Fixes the aesh-generated fish completion script which fails to pass the
	 * current token being completed to {@code --aesh-complete}.
	 * <p>
	 * The generated script uses {@code commandline -cop} (completed-only parts)
	 * which excludes the token at the cursor. This replaces it with a version that
	 * appends {@code commandline -ct} (current token) so partial-word completion
	 * works.
	 *
	 * @see <a href="https://github.com/aeshell/aesh/issues/436">aesh #436</a>
	 */
	static String fixFishCompletionScript(String script) {
		int start = script.indexOf("function __jbang_complete");
		// Find the matching "end" line for the function
		int end = script.indexOf("\nend\n", start);
		if (start < 0 || end < 0) {
			return script; // structure not recognised, return as-is
		}
		end += "\nend\n".length();
		String fixed = "function __jbang_complete\n"
				+ "    set -l tokens (commandline -cop)\n"
				+ "    set -l current (commandline -ct)\n"
				+ "    jbang --aesh-complete -- $tokens[2..] $current\n"
				+ "end\n";
		return script.substring(0, start) + fixed + script.substring(end);
	}
}
