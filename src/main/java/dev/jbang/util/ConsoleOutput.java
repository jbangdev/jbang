package dev.jbang.util;

public class ConsoleOutput {

	private static final boolean ANSI_ENABLED = isAnsiEnabled();

	private static boolean isAnsiEnabled() {
		String term = System.getenv("TERM");
		if (System.console() == null) {
			return false;
		}
		if ("dumb".equals(term)) {
			return false;
		}
		return true;
	}

	private static String ansi(String code, String text) {
		if (ANSI_ENABLED) {
			return "\033[" + code + "m" + text + "\033[0m";
		}
		return text;
	}

	public static String yellow(String text) {
		return ansi("33", text);
	}

	public static String cyan(String text) {
		return ansi("36", text);
	}

	public static String magenta(String text) {
		return ansi("35", text);
	}

	public static String faint(String text) {
		return ansi("2", text);
	}

	public static String bold(String text) {
		return ansi("1", text);
	}
}
