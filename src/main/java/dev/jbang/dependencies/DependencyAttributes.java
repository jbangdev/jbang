package dev.jbang.dependencies;

import java.util.*;

import dev.jbang.util.AttributeParser;

public class DependencyAttributes {

	private final Map<String, List<String>> attributes;

	// Java 8 Set.of() is sad :)
	static Set<String> defaultScopes = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("build", "run")));
	Set<String> scopes;

	public DependencyAttributes(Map<String, List<String>> attributes) {
		this.attributes = attributes;
	}

	// lazy computed; dont store hashset unless truly needed
	public boolean includeInScope(String scope) {
		if (scopes == null) {
			if (attributes.containsKey("scope")) {
				scopes = new HashSet<>(attributes.get("scope"));
			} else {
				scopes = defaultScopes;
			}
		}
		return scopes.contains(scope);
	}

	/**
	 * is there something configured? even if default ?
	 * 
	 * @return
	 */
	public boolean isDefault() {
		return !attributes.isEmpty();
	}

	public String toStringFormat() {
		return AttributeParser.toStringRep(attributes, "scope");
	}

	@Override
	public String toString() {
		return toStringFormat();
	}

	@Override
	public boolean equals(Object o) {
		// TODO: this wont make g:a:v and g:a:v{build,run} equal ...even if they are...
		if (o == null || getClass() != o.getClass())
			return false;
		DependencyAttributes that = (DependencyAttributes) o;
		return Objects.equals(attributes, that.attributes);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(attributes);
	}
}
