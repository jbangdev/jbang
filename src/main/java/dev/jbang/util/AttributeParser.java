package dev.jbang.util;

import java.util.*;

//handle {} similar to how Asciidoc(tor) interpret attribute lists: https://docs.asciidoctor.org/asciidoc/latest/attributes/positional-and-named-attributes/
//Derived from https://github.com/yupiik/tools-maven-plugin/blob/d1e8b97ebcae032684a0fafac426bcc25819089f/asciidoc-java/src/main/java/io/yupiik/asciidoc/parser/Parser.java#L1634

public class AttributeParser {

	public static Map<String, List<String>> parseAttributeList(String input, String defaultKey) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		List<String> unnamed = new ArrayList<>();

		for (String token : tokenize(input)) {
			if (token.isEmpty())
				continue;

			if (token.contains("=")) {
				String[] kv = token.split("=", 2);
				String key = kv[0].trim();
				String value = unquote(kv[1].trim());

				if (key.equals(defaultKey)) {
					unnamed.add(value);
				} else {
					result.put(key, Collections.singletonList(value));
				}
			} else if (token.contains("%")) {
				String[] parts = token.split("%");
				int startIdx = 0;

				if (!parts[0].isEmpty()) {
					unnamed.add(unquote(parts[0]));
					startIdx = 1;
				}

				for (int i = startIdx; i < parts.length; i++) {
					if (!parts[i].isEmpty()) {
						result.put(parts[i], Collections.singletonList("true"));
					}
				}
			} else {
				unnamed.add(unquote(token));
			}
		}

		if (!unnamed.isEmpty()) {
			result.put(defaultKey, unnamed);
		}

		return result;
	}

	private static List<String> tokenize(String input) {
		if (input == null)
			return Collections.emptyList();
		List<String> tokens = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inSingleQuote = false, inDoubleQuote = false;

		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);

			if (c == ',' && !inSingleQuote && !inDoubleQuote) {
				tokens.add(current.toString().trim());
				current.setLength(0);
			} else {
				if (c == '"' && !inSingleQuote) {
					if (i == 0 || input.charAt(i - 1) != '\\') {
						inDoubleQuote = !inDoubleQuote;
					}
				} else if (c == '\'' && !inDoubleQuote) {
					if (i == 0 || input.charAt(i - 1) != '\\') {
						inSingleQuote = !inSingleQuote;
					}
				}
				current.append(c);
			}
		}

		if (current.length() > 0) {
			tokens.add(current.toString().trim());
		}

		return tokens;
	}

	private static String unquote(String s) {
		if (s.length() < 2)
			return s;

		char first = s.charAt(0);
		char last = s.charAt(s.length() - 1);

		if ((first == '"' || first == '\'') && last != first) {
			return s;
		}

		if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
			String inner = s.substring(1, s.length() - 1);
			return first == '"' ? inner.replace("\\\"", "\"") : inner.replace("\\'", "'");
		}

		return s;
	}

	private static String quoteValue(String value) {
		boolean needsQuote = value.contains(",") || value.contains(" ") || value.contains("\"") || value.contains("'");
		if (!needsQuote)
			return value;

		boolean useDouble = !value.contains("\"") || value.contains("'");
		String escaped = value.replace(useDouble ? "\"" : "'", useDouble ? "\\\"" : "\\'");
		return useDouble ? "\"" + escaped + "\"" : "'" + escaped + "'";
	}

	public static String toStringRep(Map<String, List<String>> attributes, String defaultKey) {
		List<String> parts = new ArrayList<>();

		// Positional values first
		List<String> positional = attributes.get(defaultKey);
		if (positional != null) {
			for (String value : positional) {
				parts.add(quoteValue(value));
			}
		}

		// Other keys
		for (Map.Entry<String, List<String>> entry : attributes.entrySet()) {
			String key = entry.getKey();
			if (key.equals(defaultKey))
				continue;

			List<String> values = entry.getValue();
			for (String value : values) {
				if ("true".equals(value)) {
					parts.add("%" + key);
				} else {
					parts.add(key + "=" + quoteValue(value));
				}
			}
		}

		return String.join(",", parts);
	}
}
