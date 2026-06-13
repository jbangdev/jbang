package dev.jbang.cli;

import java.util.LinkedHashMap;
import java.util.Map;

import org.aesh.command.converter.Converter;
import org.aesh.command.converter.ConverterInvocation;
import org.aesh.command.validator.OptionValidatorException;

/**
 * Converts the raw --debug string (e.g. "4004", "*:5000",
 * "suspend=n,address=host:5000") into a structured Map&lt;String, String&gt;.
 * Bare values (no '=') are treated as an "address" entry.
 */
public class DebugConverter implements Converter<Map<String, String>, ConverterInvocation> {

	@Override
	public Map<String, String> convert(ConverterInvocation input) throws OptionValidatorException {
		String raw = input.getInput();
		Map<String, String> result = new LinkedHashMap<>();
		for (String part : raw.split(",")) {
			if (part.contains("=")) {
				String[] kv = part.split("=", 2);
				result.put(kv[0], kv[1]);
			} else {
				result.put("address", part);
			}
		}
		return result;
	}
}
