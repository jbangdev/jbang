package dev.jbang.source.resolvers;

import javax.annotation.Nonnull;

import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;

/**
 * A {@code LazyResourceResolver} is a wrapper around an existing
 * {@link ResourceResolver} that enables lazy resolution of resources. This
 * means that resource resolution is deferred until access to the resource
 * content is explicitly requested, at which point the resource will be handed
 * over to the wrapped resolver for resolution.
 */
public class LazyResourceResolver implements ResourceResolver {
	private final ResourceResolver wrappedResolver;

	public LazyResourceResolver(ResourceResolver wrappedResolver) {
		this.wrappedResolver = wrappedResolver;
	}

	@Nonnull
	@Override
	public String description() {
		return "Lazy Resource resolver";
	}

	@Override
	public ResourceRef resolve(String resource, boolean trusted) {
		return ResourceRef.lazy(wrappedResolver, resource);
	}

	public static ResourceResolver lazy(ResourceResolver resolver) {
		if (resolver instanceof LazyResourceResolver) {
			return resolver;
		}
		return new LazyResourceResolver(resolver);
	}
}
