package dev.jbang.resources.resolvers;

import java.util.function.Function;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;

public class AliasResourceResolver implements ResourceResolver {
	@Nullable
	private final Catalog catalog;
	@NonNull
	private final Function<Alias, ResourceResolver> resolverFactory;

	public AliasResourceResolver(@Nullable Catalog catalog,
			@NonNull Function<Alias, ResourceResolver> resolverFactory) {
		this.catalog = catalog;
		this.resolverFactory = resolverFactory;
	}

	@NonNull
	@Override
	public String description() {
		return String.format("Alias resolver from catalog %s using %s",
				catalog != null ? catalog.getScriptBase() : "<none>",
				resolverFactory.apply(null).description());
	}

	@Override
	public ResourceRef resolve(String resource, boolean trusted) {
		ResourceResolver resolver = resolverFactory.apply(null);
		ResourceRef ref = resolver.resolve(resource, trusted);
		if (ref == null) {
			Alias alias = (catalog != null) ? Alias.get(catalog, resource) : Alias.get(resource);
			if (alias != null) {
				resolver = resolverFactory.apply(alias);
				ResourceRef aliasRef = resolver.resolve(alias.resolve(), trusted);
				if (aliasRef == null) {
					throw new IllegalArgumentException(
							"Alias " + resource + " from " + alias.catalog.catalogRef + " failed to resolve "
									+ alias.scriptRef);
				}
				ref = new AliasedResourceRef(aliasRef, alias);
			}
		}
		return ref;
	}

	public static class AliasedResourceRef extends ResourceRef.WrappedResourceRef {
		@NonNull
		private final Alias alias;

		public AliasedResourceRef(ResourceRef aliasRef, @NonNull Alias alias) {
			super(aliasRef);
			this.alias = alias;
		}

		@NonNull
		public Alias getAlias() {
			return alias;
		}
	}
}
