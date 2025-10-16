package dev.jbang.resources.resolvers;

import java.util.function.Function;

import org.jspecify.annotations.NonNull;

import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.dependencies.JitPackUtil;
import dev.jbang.dependencies.ModularClassPath;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;

/**
 * A <code>ResourceResolver</code> that, when given a resource string which
 * looks like a Maven GAV, will try to resolve that dependency and return a
 * reference to the downloaded artifact JAR.
 */
public class GavResourceResolver implements ResourceResolver {
	private final Function<String, ModularClassPath> depResolver;

	public GavResourceResolver(Function<String, ModularClassPath> depResolver) {
		this.depResolver = depResolver;
	}

	@NonNull
	@Override
	public String description() {
		return "Maven GAV";
	}

	@Override
	public ResourceRef resolve(String resource) {
		ResourceRef result = null;

		if (DependencyUtil.looksLikeAGav(resource) || JitPackUtil.possibleMatch(resource)) {
			result = ResourceRef.forLazyFileResource(resource, ref -> {
				ModularClassPath mcp = depResolver.apply(resource);
				// We possibly get a whole bunch of artifacts, but we're only interested in
				// the one we asked for which we assume is always the first one in the list
				// (hopefully we're right).
				return mcp.getArtifacts().get(0).getFile();
			}, null);
		}

		return result;
	}
}
