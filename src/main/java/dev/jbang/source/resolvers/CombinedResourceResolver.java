package dev.jbang.source.resolvers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import dev.jbang.dependencies.Detector;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;
import dev.jbang.util.PropertiesValueResolver;

/**
 * A <code>ResourceResolver</code> that, when given a resource string will
 * delegate the resolving of that string to a list of resolvers one by one until
 * one of them returns a result and that will then be the result of this
 * resolver.
 */
public class CombinedResourceResolver implements ResourceResolver {
	private final List<ResourceResolver> resolvers;

	private Properties contextProperties = new Properties();

	public CombinedResourceResolver(ResourceResolver... resolvers) {
		new Detector().detect(contextProperties, Collections.emptyList());

		this.resolvers = Arrays.asList(resolvers);
	}

	@Override
	public ResourceRef resolve(String resource, boolean trusted) {
		final String resolvedResource = PropertiesValueResolver.replaceProperties(resource, contextProperties);
		return resolvers.stream()
			.map(r -> r.resolve(resolvedResource, trusted))
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
	}

	@Nonnull
	@Override
	public String description() {
		return String.format("Chain of [%s]",
				resolvers.stream().map(r -> r.description()).collect(Collectors.joining(", ")));
	}
}
