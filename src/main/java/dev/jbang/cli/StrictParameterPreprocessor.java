package dev.jbang.cli;

import java.util.Map;
import java.util.Stack;

import picocli.CommandLine;

/**
 * preprocessor which strictly enforces you have to use a `=` to assign values.
 * ie. only `--open=xyz` and `-o=xyz` will be accepted. Useful when you have
 * option for which you like default value to be expressed without having it
 * pick up additional values on the command line. i.e. `jbang edit --open
 * myapp.java` should not treat `myapp.java` as editor to open but instead just
 * open the default editor.
 */
public class StrictParameterPreprocessor implements CommandLine.IParameterPreprocessor {

	@Override
	public boolean preprocess(Stack<String> args, CommandLine.Model.CommandSpec commandSpec,
			CommandLine.Model.ArgSpec argSpec, Map<String, Object> info) {
		if (" ".equals(info.get("separator"))) { // parameter was not attached to option
			// act as if the user specified fallback value
			args.push(((CommandLine.Model.OptionSpec) argSpec).fallbackValue());
		}
		return false;
	}
}
