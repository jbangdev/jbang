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

	@Override
	public void parse(ParsedLineIterator iter, ProcessedOption option) throws OptionParserException {
		String word = iter.peekWord();

		String prefix, optName;
		if (option.isLongNameUsed()) {
			prefix = "--";
			optName = option.name();
		} else {
			prefix = "-";
			optName = option.shortName();
		}

		String fullPrefix = prefix + optName;

		if (word.startsWith(fullPrefix + "=")) {
			String value = word.substring(fullPrefix.length() + 1);
			option.addValue(value);
		} else {
			option.addValue("");
		}

		iter.pollParsedWord();
	}
}
