package dev.jbang.util;

import java.util.regex.Pattern;

/**
 * Very simple glob to regex converter
 * 
 * To use for glob pattern match strings, i.e. Demo* will match DemoApp,
 * DemoTest etc.
 */
public final class Glob {

	private Glob() {
	}

	public static Pattern toRegex(String glob) {
		StringBuilder regex = new StringBuilder(glob.length() * 2);
		regex.append('^');

		for (int i = 0; i < glob.length(); i++) {
			char c = glob.charAt(i);
			switch (c) {
			case '*':
				regex.append(".*");
				break;
			case '?':
				regex.append('.');
				break;
			// escape regex metacharacters
			case '\\':
			case '.':
			case '+':
			case '(':
			case ')':
			case '^':
			case '$':
			case '|':
			case '{':
			case '}':
			case '[':
			case ']':
				regex.append('\\').append(c);
				break;
			default:
				regex.append(c);
			}
		}

		regex.append('$');
		return Pattern.compile(regex.toString());
	}

	public static boolean matches(String glob, String value) {
		boolean matches = toRegex(glob).matcher(value).matches();
		if (matches) {
			Util.verboseMsg("Glob " + glob + " matches " + value);
		} else {
			Util.verboseMsg("Glob " + glob + " does not match " + value);
		}
		return matches;
	}

	public static boolean isGlob(String main) {
		return main != null && (main.contains("?") || main.contains("*"));
	}

}