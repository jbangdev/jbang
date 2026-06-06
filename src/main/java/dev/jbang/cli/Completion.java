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
	ShellType shellType = ShellType.BASH;

	public int completion() throws IOException {
		try {
			String script = ShellCompletionGenerator.generateDynamic(shellType, JBang.class, "jbang");
			if (shellType == ShellType.BASH) {
				script = fixBashNoSpace(script);
			} else if (shellType == ShellType.ZSH) {
				script = fixZshNoSpace(script);
			}
			System.out.println(script);
		} catch (Exception e) {
			throw new ExitException(EXIT_INTERNAL_ERROR, "Failed to generate completion script: " + e.getMessage(),
					e);
		}

		return EXIT_OK;
	}

	/**
	 * Patch the bash completion to suppress trailing space when the selected
	 * candidate ends with a navigational suffix ({@code :}, {@code .}, {@code /}).
	 */
	static String fixBashNoSpace(String script) {
		String nospace = "    # Suppress trailing space for navigational suffixes\n"
				+ "    if [[ ${#COMPREPLY[@]} -eq 1 ]]; then\n"
				+ "        case \"${COMPREPLY[0]}\" in\n"
				+ "            *:|*.|*/) compopt -o nospace ;;\n"
				+ "        esac\n"
				+ "    fi\n";
		// Insert before the closing brace of the function
		int closing = script.lastIndexOf("\n}\n");
		if (closing < 0) {
			closing = script.lastIndexOf("\n}");
		}
		if (closing >= 0) {
			return script.substring(0, closing) + "\n" + nospace + script.substring(closing);
		}
		return script;
	}

	/**
	 * Patch the zsh completion to split candidates into those that need a trailing
	 * space and those that don't (navigational suffixes like {@code :}, {@code .},
	 * {@code /}).
	 */
	static String fixZshNoSpace(String script) {
		String oldBlock = "    if (( ${#descriptions} > 0 )); then\n"
				+ "        _describe '' descriptions\n"
				+ "    else\n"
				+ "        compadd -a completions\n"
				+ "    fi";
		String newBlock = "    # Split into navigational (no trailing space) and final candidates\n"
				+ "    local -a nav_desc final_desc nav_comp final_comp\n"
				+ "    for d in \"${descriptions[@]}\"; do\n"
				+ "        local key=\"${d%%:*}\"\n"
				+ "        case \"$key\" in\n"
				+ "            *:|*.|*/) nav_desc+=(\"$d\") ;;\n"
				+ "            *) final_desc+=(\"$d\") ;;\n"
				+ "        esac\n"
				+ "    done\n"
				+ "    for c in \"${completions[@]}\"; do\n"
				+ "        case \"$c\" in\n"
				+ "            *:|*.|*/) nav_comp+=(\"$c\") ;;\n"
				+ "            *) final_comp+=(\"$c\") ;;\n"
				+ "        esac\n"
				+ "    done\n"
				+ "    (( ${#nav_desc} ))   && _describe -S '' '' nav_desc\n"
				+ "    (( ${#final_desc} )) && _describe '' final_desc\n"
				+ "    (( ${#nav_comp} ))   && compadd -S '' -a nav_comp\n"
				+ "    (( ${#final_comp} )) && compadd -a final_comp";
		if (script.contains(oldBlock)) {
			return script.replace(oldBlock, newBlock);
		}
		return script;
	}
}
