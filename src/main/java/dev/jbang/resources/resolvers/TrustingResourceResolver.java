package dev.jbang.resources.resolvers;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;

public class TrustingResourceResolver implements ResourceResolver {
	private final ResourceResolver resolver;

	public TrustingResourceResolver(ResourceResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public @Nullable ResourceRef resolve(String resource, boolean trusted) {
		return resolver.resolve(resource, trusted);
	}

	@Override
	public @Nullable ResourceRef resolve(String resource) {
		return resolve(resource, true);
	}

	@Override
	public @NonNull String description() {
		return "A trusting resource resolver";
	}
}
