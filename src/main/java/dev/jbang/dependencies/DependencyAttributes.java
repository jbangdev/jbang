package dev.jbang.dependencies;

import java.util.*;

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

}
