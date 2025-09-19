package dev.jbang.source;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;

import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;
import dev.jbang.source.parser.Directives;
import dev.jbang.source.sources.*;
import dev.jbang.source.sources.KotlinSource;
import dev.jbang.source.sources.MarkdownSource;
import dev.jbang.util.Util;

/**
 * A Script represents a Source (something runnable) in the form of a source
 * file. It's code that first needs to be compiled before it can be executed.
 * The Script extracts as much information from the source file as it can, like
 * all `//`-directives (eg. `//SOURCES`, `//DEPS`, etc.)
 *
 * NB: The Script contains/returns no other information than that which can be
 * induced from the source file. So all Scripts that refer to the same source
 * file will contain/return the exact same information.
 */
public abstract class Source {

	private final ResourceRef resourceRef;
	private final Supplier<String> contentsSupplier;
	private final Supplier<Directives> directivesSupplier;
	private Directives directives;
	private String contents;

	public enum Type {
		java("java", "java"), jshell("jsh", "java"),
		kotlin("kt", "kotlin"), groovy("groovy", "groovy"),
		markdown("md", "java");

		public final String extension;
		public final String sourceFolder;

		Type(String extension, String sourceFolder) {
			this.extension = extension;
			this.sourceFolder = sourceFolder;
		}

		public static List<String> extensions() {
			return Arrays.stream(values()).map(v -> v.extension).collect(Collectors.toList());
		}
	}

	protected Source(ResourceRef resourceRef, Function<String, String> replaceProperties) {
		this.resourceRef = resourceRef;
		this.contentsSupplier = () -> Util.readString(resourceRef.getInputStream());
		this.directivesSupplier = () -> new Directives.Extended(getContents(), replaceProperties);
	}

	protected Source(ResourceRef resourceRef, String contents, Function<String, String> replaceProperties) {
		this.resourceRef = resourceRef;
		this.contentsSupplier = () -> contents;
		this.contents = contents;
		this.directivesSupplier = () -> new Directives.Extended(getContents(), replaceProperties);
	}

	@NonNull
	public Directives getDirectives() {
		if (directives == null) {
			directives = directivesSupplier.get();
		}
		return directives;
	}

	protected String getContents() {
		if (contents == null) {
			contents = contentsSupplier.get();
		}
		return contents;
	}

	public abstract @NonNull Type getType();

	protected List<String> collectBinaryDependencies() {
		return getDirectives().binaryDependencies();
	}

	protected List<String> collectSourceDependencies() {
		return getDirectives().sourceDependencies();
	}

	protected abstract List<String> getCompileOptions();

	protected abstract List<String> getNativeOptions();

	protected abstract List<String> getRuntimeOptions();

	public abstract Builder<CmdGeneratorBuilder> getBuilder(BuildContext ctx);

	public ResourceRef getResourceRef() {
		return resourceRef;
	}

	@NonNull
	public Optional<String> getJavaPackage() {
		if (getContents() != null) {
			return Util.getSourcePackage(getContents());
		} else {
			return Optional.empty();
		}
	}

	public boolean isAgent() {
		return getDirectives().isAgent();
	}

	public boolean enableCDS() {
		return getDirectives().enableCDS();
	}

	public boolean enablePreview() {
		return getDirectives().enablePreview();
	}

	public boolean disableIntegrations() {
		return getDirectives().disableIntegrations();
	}

	// Used only by tests
	static Source forResource(String resource, Function<String, String> replaceProperties) {
		return forResource(ResourceResolver.forResources(), resource, replaceProperties);
	}

	static Source forResource(ResourceResolver resolver, String resource,
			Function<String, String> replaceProperties) {
		ResourceRef resourceRef = resolver.resolve(resource);
		if (resourceRef == null) {
			resourceRef = ResourceRef.forUnresolvable(resource, "not found from " + resolver.description());
		}
		return forResourceRef(resourceRef, replaceProperties);
	}

	public static Source forResourceRef(ResourceRef resourceRef, Function<String, String> replaceProperties) {
		String ext = resourceRef.getExtension();
		if (ext.equals("kt")) {
			return new KotlinSource(resourceRef, replaceProperties);
		} else if (ext.equals("groovy")) {
			return new GroovySource(resourceRef, replaceProperties);
		} else if (ext.equals("jsh")) {
			return new JshSource(resourceRef, replaceProperties);
		} else if (ext.equals("md")) {
			return MarkdownSource.create(resourceRef, replaceProperties);
		} else {
			return new JavaSource(resourceRef, replaceProperties);
		}
	}
}
