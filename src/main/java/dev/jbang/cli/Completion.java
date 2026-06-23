package dev.jbang.cli;

import java.io.IOException;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.util.completer.ShellCompletionGenerator;
import org.aesh.util.completer.ShellCompletionGenerator.ShellType;

import dev.jbang.cli.Completion.CompletionHelpSectionsProvider;

@CommandDefinition(name = "completion", description = "Generate bash/zsh or fish completion script for jbang.", generateHelp = true, helpSectionProvider = CompletionHelpSectionsProvider.class)
public class Completion extends BaseCommand {

	static class CompletionHelpSectionsProvider extends ExternalCommandsProvider {
		@Override
		public String getHeader() {
			return "Run the following to enable TAB completion in the current shell:\n"
					+ "\n"
					+ "  bash/zsh:  source <(jbang completion)\n"
					+ "  fish:      jbang completion | source\n"
					+ "  pwsh:      (jbang completion) -join \"`n\" | Invoke-Expression\n"
					+ "\n"
					+ "The shell is auto-detected. To make it permanent, see the\n"
					+ "comments at the end of the generated script.";
		}
	}

	@Override
	public Integer doCall() throws IOException {
		return completion();
	}

	@Option(shortName = 's', name = "shell", description = "The shell to generate the completion script for. Supported shells: bash, zsh, fish, and pwsh. Default: auto-detected from current shell.")
	ShellType shellOption;

	@Argument(paramLabel = "shell", index = "0", arity = "0..1", description = "Shell name (bash, zsh, fish, pwsh). Overrides auto-detection.")
	ShellType shellArg;

	public int completion() throws IOException {
		ShellType shellType = shellOption != null ? shellOption : shellArg;
		if (shellType == null) {
			shellType = detectShell();
		}
		if (shellType == null) {
			throw new ExitException(EXIT_INVALID_INPUT,
					"Could not detect your shell. Please specify one, e.g.: jbang completion bash");
		}
		try {
			String script = ShellCompletionGenerator.generateDynamic(shellType, JBang.class, "jbang");
			System.out.println(script);
			System.out.println(usageHint(shellType));
		} catch (Exception e) {
			throw new ExitException(EXIT_INTERNAL_ERROR, "Failed to generate completion script: " + e.getMessage(),
					e);
		}

		return EXIT_OK;
	}

	private static String usageHint(ShellType type) {
		switch (type) {
		case BASH:
			return "\n# --- How to enable jbang completions for bash ---\n"
					+ "#\n"
					+ "# Try it now (current shell only, no config change):\n"
					+ "#   source <(jbang completion)\n"
					+ "#\n"
					+ "# Make it permanent \u2014 pick ONE:\n"
					+ "#\n"
					+ "#   a) Per-user: add to your ~/.bashrc:\n"
					+ "#        source <(jbang completion)\n"
					+ "#\n"
					+ "#   b) System-wide: save to the completions directory:\n"
					+ "#        jbang completion | sudo tee /etc/bash_completion.d/jbang > /dev/null\n";
		case ZSH:
			return "\n# --- How to enable jbang completions for zsh ---\n"
					+ "#\n"
					+ "# Try it now (current shell only, no config change):\n"
					+ "#   source <(jbang completion zsh)\n"
					+ "#\n"
					+ "# Make it permanent \u2014 pick ONE:\n"
					+ "#\n"
					+ "#   a) Per-user: add to your ~/.zshrc:\n"
					+ "#        source <(jbang completion zsh)\n"
					+ "#\n"
					+ "#   b) Save to a directory in your fpath:\n"
					+ "#        jbang completion zsh > ~/.zsh/completions/_jbang\n"
					+ "#      (ensure ~/.zsh/completions is in your fpath before compinit)\n";
		case FISH:
			return "\n# --- How to enable jbang completions for fish ---\n"
					+ "#\n"
					+ "# Try it now (current shell only, no config change):\n"
					+ "#   jbang completion fish | source\n"
					+ "#\n"
					+ "# Make it permanent:\n"
					+ "#   jbang completion fish > ~/.config/fish/completions/jbang.fish\n";
		case PWSH:
			return "\n# --- How to enable jbang completions for PowerShell ---\n"
					+ "#\n"
					+ "# Try it now (current session only, no config change):\n"
					+ "#   (jbang completion pwsh) -join \"`n\" | Invoke-Expression\n"
					+ "#\n"
					+ "# Make it permanent \u2014 pick ONE:\n"
					+ "#\n"
					+ "#   a) Add to your $PROFILE:\n"
					+ "#        (jbang completion pwsh) -join \"`n\" | Invoke-Expression\n"
					+ "#\n"
					+ "#   b) Save to a file and dot-source it from $PROFILE:\n"
					+ "#        jbang completion pwsh | Out-File -Encoding utf8 ~/jbang-completion.ps1\n"
					+ "#        Add to $PROFILE:  . ~/jbang-completion.ps1\n";
		default:
			return "";
		}
	}

	static ShellType detectShell() {
		// Check shell-specific environment variables first
		if (System.getenv("FISH_VERSION") != null)
			return ShellType.FISH;
		if (System.getenv("ZSH_VERSION") != null)
			return ShellType.ZSH;
		if (System.getenv("BASH_VERSION") != null)
			return ShellType.BASH;

		// Fall back to $SHELL
		String shell = System.getenv("SHELL");
		if (shell == null || shell.isEmpty()) {
			// No $SHELL \u2014 check if running inside PowerShell (last resort)
			if (System.getenv("PSModulePath") != null)
				return ShellType.PWSH;
			return null;
		}
		if (shell.contains("fish"))
			return ShellType.FISH;
		if (shell.contains("zsh"))
			return ShellType.ZSH;
		if (shell.contains("bash"))
			return ShellType.BASH;
		if (shell.contains("pwsh") || shell.contains("powershell"))
			return ShellType.PWSH;
		return null;
	}
}
