package dev.jbang.search;

import java.util.regex.Pattern;

/**
 * Classifies raw user input into a search intent, determining the best query
 * strategy for local and Central searches.
 */
public class SearchIntentClassifier {

	/** The type of search to perform. */
	public enum IntentType {
		/** groupId:artifactId:version — exact lookup */
		EXACT_GAV,
		/** groupId:artifactId — find versions */
		GROUP_ARTIFACT,
		/** fc:com.example.Foo — fully qualified class name */
		FULLY_QUALIFIED_CLASS,
		/** c:ObjectMapper — simple class name */
		SIMPLE_CLASS,
		/** Java import statement pasted in */
		JAVA_IMPORT,
		/** javac "package X does not exist" error */
		PACKAGE_ERROR,
		/** javac "cannot find symbol" error */
		SYMBOL_ERROR,
		/** General keyword search */
		KEYWORD
	}

	/** Result of classifying user input. */
	public static class Intent {
		public final IntentType type;
		/** The normalized query to use for local fuzzy search. */
		public final String localQuery;
		/**
		 * The query to send to Central (may include c:/fc: prefix or g:/a: Solr
		 * syntax).
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
		 * Returns true if this intent type is best served by Central search (class
		 * lookups, imports, compiler errors) rather than local fuzzy matching.
		 */
		public boolean prefersCentral() {
			switch (type) {
			case FULLY_QUALIFIED_CLASS:
			case SIMPLE_CLASS:
			case JAVA_IMPORT:
			case PACKAGE_ERROR:
			case SYMBOL_ERROR:
				return true;
			default:
				return false;
			}
		}
	}

	// Patterns
	private static final Pattern GAV3 = Pattern.compile(
			"^[a-zA-Z][a-zA-Z0-9._-]*:[a-zA-Z][a-zA-Z0-9._-]*:.+$");
	private static final Pattern GA = Pattern.compile(
			"^[a-zA-Z][a-zA-Z0-9._-]*:[a-zA-Z][a-zA-Z0-9._-]*$");
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

		// Check for Java import statement
		java.util.regex.Matcher importMatcher = IMPORT.matcher(trimmed);
		if (importMatcher.matches()) {
			String fqcn = importMatcher.group(2);
			// Strip trailing wildcard: import java.util.* → java.util
			if (fqcn.endsWith(".*")) {
				String pkg = fqcn.substring(0, fqcn.length() - 2);
				return new Intent(IntentType.JAVA_IMPORT, pkg, pkg,
						"import → package " + pkg);
			}
			return new Intent(IntentType.JAVA_IMPORT, fqcn, "fc:" + fqcn,
					"import → class " + fqcn);
		}

		// Check for javac package error
		java.util.regex.Matcher pkgMatcher = PACKAGE_ERROR.matcher(trimmed);
		if (pkgMatcher.find()) {
			String pkg = pkgMatcher.group(1);
			return new Intent(IntentType.PACKAGE_ERROR, pkg, pkg,
					"error → package " + pkg);
		}

		// Check for javac symbol error
		java.util.regex.Matcher symMatcher = SYMBOL_ERROR.matcher(trimmed);
		if (symMatcher.find()) {
			String sym = symMatcher.group(1);
			if (SIMPLE_CLASS.matcher(sym).matches()) {
				return new Intent(IntentType.SYMBOL_ERROR, sym, "c:" + sym,
						"error → class " + sym);
			}
			return new Intent(IntentType.SYMBOL_ERROR, sym, sym,
					"error → " + sym);
		}

		// Check for explicit c: or fc: prefix (pass through)
		if (trimmed.startsWith("fc:") || trimmed.startsWith("c:")) {
			return new Intent(IntentType.KEYWORD, trimmed, trimmed, trimmed);
		}

		// Check for exact GAV (g:a:v)
		if (GAV3.matcher(trimmed).matches()) {
			String[] parts = trimmed.split(":", 3);
			return new Intent(IntentType.EXACT_GAV, trimmed,
					trimmed,
					"GAV " + parts[0] + ":" + parts[1] + ":" + parts[2]);
		}

		// Check for group:artifact
		if (GA.matcher(trimmed).matches()) {
			return new Intent(IntentType.GROUP_ARTIFACT, trimmed,
					trimmed,
					"group:artifact");
		}

		// Check for FQCN (com.example.Foo)
		if (FQCN.matcher(trimmed).matches()) {
			return new Intent(IntentType.FULLY_QUALIFIED_CLASS, trimmed,
					"fc:" + trimmed,
					"class " + trimmed);
		}

		// Check for simple class name (ObjectMapper, JsonParser)
		if (SIMPLE_CLASS.matcher(trimmed).matches()) {
			return new Intent(IntentType.SIMPLE_CLASS, trimmed,
					"c:" + trimmed,
					"class " + trimmed);
		}

		// Default: keyword search
		return new Intent(IntentType.KEYWORD, trimmed, trimmed, "");
	}
}
