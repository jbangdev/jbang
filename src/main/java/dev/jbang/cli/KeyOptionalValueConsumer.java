package dev.jbang.cli;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import picocli.CommandLine;

public class KeyOptionalValueConsumer implements CommandLine.IParameterConsumer {

	Pattern p = Pattern.compile("(\\S*?)(=(\\S+))?");

	@Override
	public void consumeParameters(Stack<String> args, CommandLine.Model.ArgSpec argSpec,
			CommandLine.Model.CommandSpec commandSpec) {
		String arg = args.pop();
		Matcher m = p.matcher(arg);
		if (m.matches()) {

			Map<String, Optional<String>> kv = argSpec.getValue();

			if (kv == null) {
				kv = new LinkedHashMap<>();
			}

			kv.put(m.group(1), Optional.ofNullable(m.group(3)));

			argSpec.setValue(kv);
		} else {

		}
	}
}
