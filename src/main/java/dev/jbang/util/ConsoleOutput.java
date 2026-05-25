package dev.jbang.util;

import org.aesh.terminal.utils.ANSI;

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

	private static String wrap(String prefix, String text) {
		if (ANSI_ENABLED) {
			return prefix + text + ANSI.RESET;
		}
		return text;
	}

	public static String yellow(String text) {
		return wrap(ANSI.YELLOW_TEXT, text);
	}

	public static String cyan(String text) {
		return wrap(ANSI.CYAN_TEXT, text);
	}

	public static String magenta(String text) {
		return wrap(ANSI.MAGENTA_TEXT, text);
	}

	// ANSI.FAINT not available until terminal-api 3.8
	public static String faint(String text) {
		return wrap(ANSI.START + "2m", text);
	}

	public static String bold(String text) {
		return wrap(ANSI.BOLD, text);
	}
}
