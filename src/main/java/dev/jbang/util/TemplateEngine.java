package dev.jbang.util;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;

import io.quarkus.qute.Engine;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateLocator;
import io.quarkus.qute.Variant;

public class TemplateEngine {
	final Engine engine;

	static TemplateEngine instance;

	/**
	 * Per-thread resolver used by the Qute locator to resolve {#include} paths. Set
	 * before rendering, cleared after.
	 */
	private static final ThreadLocal<ResourceResolver> currentResolver = new ThreadLocal<>();

	TemplateEngine() {
		engine = Engine.builder()
			.addDefaults()
			.removeStandaloneLines(true)
			.addValueResolver(new ReflectionValueResolver())
			// .addResultMapper(new PropertyNotFoundThrowException())
			.addLocator(this::locate)
			.build();
	}

	/**
	 * @param ref
	 * @return the optional reader
	 */
	private Optional<TemplateLocator.TemplateLocation> locate(String ref) {
		ResourceResolver resolver = currentResolver.get();
		ResourceRef resourceRef = resolver != null ? resolver.resolve(ref) : ResourceRef.forResource(ref);
		if (resourceRef == null || !resourceRef.exists()) {
			return Optional.empty();
		}
		return Optional.of(new ResourceRefTemplateLocation(resourceRef));
	}

	public Template getTemplate(ResourceRef templateRef) {
		return engine.getTemplate(templateRef.getOriginalResource());
	}

	/**
	 * Returns the template, using the given resolver to resolve any {#include}
	 * directives found within the template.
	 */
	/**
	 * Sets a resolver for {#include} resolution during template rendering. Call
	 * before render(), clear with {@link #clearIncludeResolver()} after.
	 */
	public void setIncludeResolver(ResourceResolver resolver) {
		if (resolver != null) {
			currentResolver.set(resolver);
		}
	}

	public void clearIncludeResolver() {
		currentResolver.remove();
	}

	static class ResourceRefTemplateLocation implements TemplateLocator.TemplateLocation {
		private final ResourceRef resourceRef;
		private Optional<Variant> variant = Optional.empty();

		public ResourceRefTemplateLocation(ResourceRef resourceRef) {
			this.resourceRef = resourceRef;
			this.variant = Optional.empty();
		}

		@Override
		public Reader read() {
			return new InputStreamReader(resourceRef.getInputStream(), StandardCharsets.UTF_8);
		}

		@Override
		public Optional<Variant> getVariant() {
			return variant;
		}

	}

	public static TemplateEngine instance() {
		if (instance == null) {
			instance = new TemplateEngine();
		}
		return instance;
	}

}
