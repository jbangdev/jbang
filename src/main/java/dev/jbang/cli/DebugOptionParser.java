package dev.jbang.cli;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.parser.OptionParser;
import org.aesh.command.parser.OptionParserException;
import org.aesh.parser.ParsedLineIterator;

/**
 * Peeks ahead at --debug to pick up --debug=5000, --debug 5000, --debug *:5000
 * as debug parameters but not --debug somefile.java
 */
public class DebugOptionParser implements OptionParser {

	private static final Pattern DEBUG_VALUE_PATTERN = Pattern
		.compile("(?:(.*?:)?(\\d+\\??))|(?:\\S*=\\S+\\??)");

	private static String fullPrefix(ProcessedOption option) {
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
			addOptionValue(option, value);
			iter.pollParsedWord();
			return;
		}

		iter.pollParsedWord();

		if (iter.hasNextWord()) {
			String nextWord = iter.peekWord();
			if (!nextWord.startsWith("-")) {
				Matcher m = DEBUG_VALUE_PATTERN.matcher(nextWord);
				if (m.matches()) {
					addOptionValue(option, nextWord);
					iter.pollParsedWord();
					return;
				}
			}
		}

		// Bare --debug: resolve via DefaultValueProvider.fallbackValue(),
		// then annotation fallbackValue, mirroring aesh's applyOptionalFallback().
		String fallback = resolveFallback(option);
		addOptionValue(option, fallback != null ? fallback : "");
	}

	private static String resolveFallback(ProcessedOption option) {
		if (option.parent() != null && option.parent().getDefaultValueProvider() != null) {
			try {
				String val = option.parent().getDefaultValueProvider().fallbackValue(option);
				if (val != null) {
					return val;
				}
			} catch (Exception e) {
				// ignore
			}
		}
		return option.hasFallbackValue() ? option.getFallbackValue() : null;
	}

	private void addOptionValue(ProcessedOption option, String value) {
		if (!option.getValues().isEmpty()) {
			String existing = String.join(",", option.getValues());
			option.getValues().clear();
			option.addValue(existing + "," + value);
		} else {
			option.addValue(value);
		}
	}
}
