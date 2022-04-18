package dev.jbang.source;

import java.util.function.Function;

import dev.jbang.catalog.Catalog;
import dev.jbang.dependencies.ModularClassPath;
import dev.jbang.source.resolvers.*;

/**
 * An interface used for analysing a resource string, resolving what exact file
 * it refers to and returning a <code>ResourceRef</code> for it.
 */
public interface ResourceResolver {
	/**
	 * Given a resource string either returns a `ResourceRef` that identifies that
	 * resource or <code>null</code> if the resolver wasn't able to handle the
	 * resource string.
	 *
	 * The method should _only_ return <code>null</code> if it does not recognize
	 * the resource string, thereby informing the caller it should probably try some
	 * other resolver. In case the resolver _does_ recognize the resource string as
	 * having a supported format but something goes wrong and no reference can be
	 * created it's normal to throw an exception.
	 *
	 * The <code>trusted</code> argument is used to indicate if a resolve request
	 * should be considered trusted or not. Untrusted requests might cause a
	 * resolver to check a request against some kind of trust provider and throwing
	 * an exception if the request was considered unauthorized. Trusted requests
	 * should never do that.
	 *
	 * @param resource The resource string to resolve
	 * @param trusted  If the request should be considered trusted or not
	 * @return A <code>ResourceRef</code> or <code>null</code>
	 */
	default ResourceRef resolve(String resource, boolean trusted) {
		return resolve(resource);
	}

	/**
	 * Given a resource string either returns a `ResourceRef` that identifies that
	 * resource or <code>null</code> if the resolver wasn't able to handle the
	 * resource string.
	 *
	 * The method should _only_ return <code>null</code> if it does not recognize
	 * the resource string, thereby informing the caller it should probably try some
	 * other resolver. In case the resolver _does_ recognize the resource string as
	 * having a supported format but something goes wrong and no reference can be
	 * created it's normal to throw an exception.
	 *
	 * Calls to this method are always considered untrusted.
	 *
	 * @param resource The resource string to resolve
	 * @return A <code>ResourceRef</code> or <code>null</code>
	 */
	default ResourceRef resolve(String resource) {
		return resolve(resource, false);
	}

	String description();

	/**
	 * Factory method that returns a resource resolver that knows how to deal with
	 * script/source files. It should be passed a function for dealing with Maven
	 * GAVs.
	 *
	 * @param depResolver A function which, given a GAV string, returns a
	 *                    <code>ModularClassPath</code>
	 * @return A <code>ResourceRef</code> or <code>null</code>
	 */
	static ResourceResolver forScripts(Function<String, ModularClassPath> depResolver, Catalog catalog) {
		return new AliasResourceResolver(catalog,
				new CombinedResourceResolver(
						new RenamingScriptResourceResolver(),
						new LiteralScriptResourceResolver(),
						new RemoteResourceResolver(false),
						new ClasspathResourceResolver(),
						new GavResourceResolver(depResolver),
						new FileResourceResolver()));
	}

	/**
	 * Factory method that returns a resource resolver that knows how to deal with
	 * simple resource files (so it's _not_ meant for resolving sources/scripts).
	 *
	 * @return A <code>ResourceRef</code> or <code>null</code>
	 */
	static ResourceResolver forResources() {
		return new CombinedResourceResolver(
				new RemoteResourceResolver(true),
				new ClasspathResourceResolver(),
				new FileResourceResolver());
	}
}
