package dev.jbang.util;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import dev.jbang.resources.ResourceRef;

import io.quarkus.qute.Engine;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateLocator;
import io.quarkus.qute.Variant;

public class TemplateEngine {
	final Engine engine;

	static TemplateEngine instance;

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
		return Optional.of(new ResourceRefTemplateLocation(ResourceRef.forResource(ref)));
	}

	public Template getTemplate(ResourceRef templateRef) {
		return engine.getTemplate(templateRef.getOriginalResource());
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
