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
			System.out.println(script);
		} catch (Exception e) {
			throw new ExitException(EXIT_INTERNAL_ERROR, "Failed to generate completion script: " + e.getMessage(),
					e);
		}

		return EXIT_OK;
	}
}
