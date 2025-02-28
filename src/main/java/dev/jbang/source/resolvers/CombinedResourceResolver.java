package dev.jbang.source.resolvers;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;

/**
 * A <code>ResourceResolver</code> that, when given a resource string will
 * delegate the resolving of that string to a list of resolvers one by one until
 * one of them returns a result and that will then be the result of this
 * resolver.
 */
public class CombinedResourceResolver implements ResourceResolver {
	private final List<ResourceResolver> resolvers;

	public CombinedResourceResolver(ResourceResolver... resolvers) {
		this.resolvers = Arrays.asList(resolvers);
	}

	@Override
	public ResourceRef resolve(String resource, boolean trusted) {
		return resolvers.stream()
				.map(r -> r.resolve(resource, trusted))
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
	}

	@Override
	public String description() {
		return String.format("Chain of [%s]",
				resolvers.stream().map(r -> r.description()).collect(Collectors.joining(", ")));
	}
}
