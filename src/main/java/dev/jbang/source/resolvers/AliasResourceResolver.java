package dev.jbang.source.resolvers;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.source.DirectResourceRef;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;

public class AliasResourceResolver implements ResourceResolver {
	@Nullable
	private final Catalog catalog;
	@Nonnull
	private final Function<Alias, ResourceResolver> resolverFactory;

	public AliasResourceResolver(@Nullable Catalog catalog,
			@Nonnull Function<Alias, ResourceResolver> resolverFactory) {
		this.catalog = catalog;
		this.resolverFactory = resolverFactory;
	}

	@Nonnull
	@Override
	public String description() {
		return String.format("Alias resolver from catalog %s using %s",
				Objects.toString(catalog.getScriptBase(), "<none>"),
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
				ref = new AliasedResourceRef(aliasRef.getOriginalResource(), aliasRef.getFile(), alias);
			}
		}
		return ref;
	}

	public static class AliasedResourceRef extends DirectResourceRef {
		@Nonnull
		private final Alias alias;

		public AliasedResourceRef(String ref, Path file, @Nonnull Alias alias) {
			super(ref, file);
			this.alias = alias;
		}

		@Nonnull
		public Alias getAlias() {
			return alias;
		}
	}
}
