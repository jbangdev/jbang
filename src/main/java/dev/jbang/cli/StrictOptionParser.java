package dev.jbang.cli;

import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.parser.OptionParser;
import org.aesh.command.parser.OptionParserException;
import org.aesh.parser.ParsedLineIterator;

/**
 * Option parser that only accepts values via = syntax. {@code --option=value}
 * uses "value"; {@code --option} (without =) uses empty string as sentinel for
 * "used without value".
 */
public class StrictOptionParser implements OptionParser {

	/**
	 * Returns the full prefix for the given option, e.g. "--name" or "-n".
	 */
	static String fullPrefix(ProcessedOption option) {
		if (option.isLongNameUsed()) {
			return "--" + option.name();
		}
		return "-" + option.shortName();
	}

	@Override
	public void parse(ParsedLineIterator iter, ProcessedOption option) throws OptionParserException {
		String word = iter.peekWord();
		String fp = fullPrefix(option);

		if (word.startsWith(fp + "=")) {
			String value = word.substring(fp.length() + 1);
			option.addValue(value);
		} else {
			option.addValue("");
		}

		iter.pollParsedWord();
	}
}
