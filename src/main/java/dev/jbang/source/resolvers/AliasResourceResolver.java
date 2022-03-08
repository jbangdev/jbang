package dev.jbang.source.resolvers;

import java.io.File;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;

public class AliasResourceResolver implements ResourceResolver {
	@Nullable
	private final Catalog catalog;
	@Nonnull
	private final ResourceResolver resolver;

	public AliasResourceResolver(@Nullable Catalog catalog, @Nonnull ResourceResolver resolver) {
		this.catalog = catalog;
		this.resolver = resolver;
	}

	@Override
	public ResourceRef resolve(String resource, boolean trusted) {
		ResourceRef ref = resolver.resolve(resource, trusted);
		if (ref == null) {
			Alias alias = (catalog != null) ? Alias.get(catalog, resource) : Alias.get(resource);
			if (alias != null) {
				ResourceRef aliasRef = resolver.resolve(alias.resolve(), trusted);
				if (aliasRef == null) {
					throw new IllegalArgumentException(
							"Alias " + resource + " from " + alias.catalog.catalogRef + " failed to resolve "
									+ alias.scriptRef);
				}
				ref = new AliasedResourceRef(aliasRef.getOriginalResource(), aliasRef.getFile(), alias);
			}
		}
		return ref;
	}

	public static class AliasedResourceRef extends ResourceRef {
		@Nonnull
		private final Alias alias;

		public AliasedResourceRef(String ref, File file, @Nonnull Alias alias) {
			super(ref, file);
			this.alias = alias;
		}

		@Nonnull
		public Alias getAlias() {
			return alias;
		}
	}
}
