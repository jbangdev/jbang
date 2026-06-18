package dev.jbang.search;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies raw user input into a search intent, determining the best query
 * strategy for local and Central searches.
 */
public class SearchIntentClassifier {

	/** The type of search to perform. */
	public enum IntentType {
		/** Class-based lookup — prefers Central search */
		CLASS,
		/** Everything else: GAV, keyword, general text */
		KEYWORD
	}

	/** Result of classifying user input. */
	public static class Intent {
		public final IntentType type;
		/** The normalized query to use for local fuzzy search. */
		public final String localQuery;
		/**
		 * The query to send to Central (may include c:/fc: prefix or Solr syntax).
		 */
		public final String centralQuery;
		/** Human-readable label explaining the classification. */
		public final String label;

		Intent(IntentType type, String localQuery, String centralQuery, String label) {
			this.type = type;
			this.localQuery = localQuery;
			this.centralQuery = centralQuery;
			this.label = label;
		}

		/**
		 * Returns true if this intent is best served by Central search rather than
		 * local fuzzy matching.
		 */
		public boolean prefersCentral() {
			return type == IntentType.CLASS;
		}
	}

	// Patterns
	private static final Pattern FQCN = Pattern.compile(
			"^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*\\.[A-Z][a-zA-Z0-9]*$");
	private static final Pattern SIMPLE_CLASS = Pattern.compile(
			"^[A-Z][a-zA-Z0-9]{2,}$");
	private static final Pattern IMPORT = Pattern.compile(
			"^\\s*import\\s+(static\\s+)?([a-zA-Z][a-zA-Z0-9._]*[a-zA-Z0-9*])\\s*;?\\s*$");
	private static final Pattern PACKAGE_ERROR = Pattern.compile(
			"(?:error:\\s*)?package\\s+([a-zA-Z][a-zA-Z0-9._]*)\\s+does not exist");
	private static final Pattern SYMBOL_ERROR = Pattern.compile(
			"(?:error:\\s*)?cannot find symbol.*?(?:class|variable)\\s+([a-zA-Z][a-zA-Z0-9]*)");

	/**
	 * Classify the given input string into a search intent.
	 *
	 * @param input raw user input (from the search box)
	 * @return the classified intent with normalized queries
	 */
	public static Intent classify(String input) {
		if (input == null || input.trim().isEmpty()) {
			return new Intent(IntentType.KEYWORD, "", "", "");
		}

		String trimmed = input.trim();

		// Java import statement
		Matcher importMatcher = IMPORT.matcher(trimmed);
		if (importMatcher.matches()) {
			String fqcn = importMatcher.group(2);
			if (fqcn.endsWith(".*")) {
				String pkg = fqcn.substring(0, fqcn.length() - 2);
				return new Intent(IntentType.CLASS, pkg, pkg,
						"import → package " + pkg);
			}
			return new Intent(IntentType.CLASS, fqcn, "fc:" + fqcn,
					"import → class " + fqcn);
		}

		// javac "package X does not exist" error
		Matcher pkgMatcher = PACKAGE_ERROR.matcher(trimmed);
		if (pkgMatcher.find()) {
			String pkg = pkgMatcher.group(1);
			return new Intent(IntentType.CLASS, pkg, pkg,
					"error → package " + pkg);
		}

		// javac "cannot find symbol" error
		Matcher symMatcher = SYMBOL_ERROR.matcher(trimmed);
		if (symMatcher.find()) {
			String sym = symMatcher.group(1);
			String centralQ = SIMPLE_CLASS.matcher(sym).matches() ? "c:" + sym : sym;
			return new Intent(IntentType.CLASS, sym, centralQ,
					"error → " + sym);
		}

		// Explicit fc: prefix
		if (trimmed.startsWith("fc:")) {
			String className = trimmed.substring(3);
			return new Intent(IntentType.CLASS, className, trimmed, trimmed);
		}

		// Explicit c: prefix
		if (trimmed.startsWith("c:")) {
			String className = trimmed.substring(2);
			return new Intent(IntentType.CLASS, className, trimmed, trimmed);
		}

		// Fully-qualified class name (com.example.Foo)
		if (FQCN.matcher(trimmed).matches()) {
			return new Intent(IntentType.CLASS, trimmed,
					"fc:" + trimmed, "class " + trimmed);
		}

		// Simple class name (ObjectMapper, JsonParser)
		if (SIMPLE_CLASS.matcher(trimmed).matches()) {
			return new Intent(IntentType.CLASS, trimmed,
					"c:" + trimmed, "class " + trimmed);
		}

		// Everything else: keywords, GAV coordinates, etc.
		return new Intent(IntentType.KEYWORD, trimmed, trimmed, "");
	}
}
