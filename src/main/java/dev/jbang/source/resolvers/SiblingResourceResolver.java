package dev.jbang.source.resolvers;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Nonnull;

import dev.jbang.catalog.Catalog;
import dev.jbang.source.ResourceNotFoundException;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.ResourceResolver;
import dev.jbang.util.Util;

public class SiblingResourceResolver implements ResourceResolver {
	private final ResourceRef sibling;
	private final ResourceResolver resolver;

	public SiblingResourceResolver(ResourceRef sibling) {
		this.sibling = sibling;
		this.resolver = ResourceResolver.forResources();
	}

	public SiblingResourceResolver(ResourceRef sibling, ResourceResolver resolver) {
		this.sibling = sibling;
		this.resolver = resolver;
	}

	@Nonnull
	@Override
	public String description() {
		return String.format("%s via %s", sibling.getOriginalResource(), resolver.description());
	}

	@Override
	public ResourceRef resolve(String resource, boolean trusted) {
		try {
			String originalResource = sibling.getOriginalResource();
			String sr;
			if (Util.isURL(resource)) {
				sr = resource;
			} else if (sibling.isURL()) {
				sr = new URI(Util.swizzleURL(originalResource)).resolve(resource).toString();
			} else if (Util.isClassPathRef(resource)) {
				sr = resource;
			} else if (sibling.isClasspath()) {
				sr = Paths.get(originalResource.substring(11)).resolveSibling(resource).toString();
				sr = "classpath:" + sr;
			} else if (Catalog.isValidCatalogReference(resource)) {
				sr = resource;
			} else {
				Path baseDir = originalResource != null ? Paths.get(originalResource) : Util.getCwd().resolve("dummy");
				sr = baseDir.resolveSibling(resource).toString();
			}
			ResourceRef result = resolver.resolve(sr, true);
			if (result == null) {
				throw new ResourceNotFoundException(resource, "Could not find " + resource);
			}
			return result;
		} catch (URISyntaxException e) {
			throw new ResourceNotFoundException(resource, "Syntax error when trying to find " + resource, e);
		}
	}
}
