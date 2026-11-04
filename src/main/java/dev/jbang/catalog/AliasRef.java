package dev.jbang.catalog;

/**
 * Represents a user-provided alias reference that may contain an inline
 * version.
 * <p>
 * This parser exists to separate <em>parsing</em> from <em>resolution</em>:
 * parsing must be side-effect free (no catalog loading, no network access) so
 * it can safely run in early CLI probing and unit tests.
 * 
 * We do this so we have access to the raw alias reference for error messages
 * and logging, while also have access to optional version and a version of the
 * resolved alias.
 * 
 * <p>
 * Supported syntax:
 * <ul>
 * <li>{@code alias} - plain alias</li>
 * <li>{@code alias:version} - alias with an inline requested version</li>
 * <li>{@code alias@catalogRef} - alias qualified with a catalog reference</li>
 * <li>{@code alias:version@catalogRef} - alias qualified with catalog reference
 * and version</li>
 * </ul>
 */
public final class AliasRef {
	/**
	 * The original alias reference as provided by the user.
	 */
	public final String rawAlias;
	/**
	 * The alias reference with any inline {@code :version} removed.
	 * <p>
	 * Examples:
	 * <ul>
	 * <li>{@code hello:v1.0} -&gt; {@code hello}</li>
	 * <li>{@code hello:v1.0@org/repo/main} -&gt; {@code hello@org/repo/main}</li>
	 * <li>{@code hello@org/repo/main} -&gt; {@code hello@org/repo/main}</li>
	 * </ul>
	 */
	public final String alias;
	/**
	 * The requested version from inline {@code :version} syntax, or {@code null}
	 * when no inline version was present.
	 */
	public final String requestedVersion;

	private AliasRef(String rawAlias, String alias, String requestedVersion) {
		this.rawAlias = rawAlias;
		this.alias = alias;
		this.requestedVersion = requestedVersion;
	}

	/**
	 * Parse a alias reference (alias[:version][@catalog]) into {@link AliasRef}
	 * without performing any alias or catalog resolution.
	 * <p>
	 * 
	 * @param userAlias raw user input (e.g. {@code tool:1.0@org/repo/main})
	 * @return a parsed reference where {@link #alias} can be used for alias lookup
	 *         and {@link #requestedVersion} (if non-null) can be applied by the
	 *         caller
	 * @throws IllegalArgumentException if the token is syntactically invalid (e.g.
	 *                                  empty alias or empty version)
	 */
	public static AliasRef parse(String userAlias) {
		if (userAlias == null) {
			throw new IllegalArgumentException("Invalid alias syntax: null");
		}
		if (userAlias.startsWith(":") || userAlias.startsWith("@")) {
			throw new IllegalArgumentException("Invalid alias syntax: '" + userAlias + "'");
		}

		int colonIdx = userAlias.indexOf(':');
		String alias = userAlias;
		String version = null;

		int atIdx = userAlias.indexOf('@');
		if (colonIdx > 0 && (atIdx == -1 || colonIdx < atIdx)) {
			String beforeColon = userAlias.substring(0, colonIdx);
			String afterColon = userAlias.substring(colonIdx + 1);

			int versionAtIdx = afterColon.indexOf('@');
			if (versionAtIdx == 0) {
				throw new IllegalArgumentException(
						"Invalid alias syntax: '" + userAlias + "'. Expected format: alias:version@catalog");
			} else if (versionAtIdx > 0) {
				version = afterColon.substring(0, versionAtIdx);
				alias = beforeColon + afterColon.substring(versionAtIdx);
			} else {
				version = afterColon;
				alias = beforeColon;
			}

			if (alias.isEmpty() || version.isEmpty()) {
				throw new IllegalArgumentException(
						"Invalid alias syntax: '" + userAlias + "'. Expected format: alias:version@catalog");
			}
		}

		return new AliasRef(userAlias, alias, version);
	}
}
