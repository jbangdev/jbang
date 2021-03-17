package dev.jbang.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import dev.jbang.Main;

import io.quarkus.qute.Engine;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateLocator;
import io.quarkus.qute.Variant;

public class TemplateEngine {
	final Engine engine;

	static TemplateEngine instance;

	TemplateEngine() {
		engine = Engine	.builder()
						.addDefaults()
						.removeStandaloneLines(true)
						.addValueResolver(new ReflectionValueResolver())
						.addLocator(this::locate)
						.build();
	}

	private URL locatePath(String path) {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (cl == null) {
			cl = Main.class.getClassLoader();
		}
		return cl.getResource(path);
	}

	/**
	 * @param path
	 * @return the optional reader
	 */
	private Optional<TemplateLocator.TemplateLocation> locate(String path) {
		Path p = Paths.get(path);
		if (p.isAbsolute() || Files.isReadable(p)) {
			return Optional.of(new FileTemplateLocation(p));
		} else {
			URL resource = locatePath(path);
			if (resource != null) {
				return Optional.of(new ResourceTemplateLocation(resource));
			}
		}
		return Optional.empty();
	}

	public Template getTemplate(String templateName) {
		return engine.getTemplate(templateName);
	}

	static class ResourceTemplateLocation implements TemplateLocator.TemplateLocation {
		private final URL resource;
		private Optional<Variant> variant = Optional.empty();

		public ResourceTemplateLocation(URL resource) {
			this.resource = resource;
			this.variant = Optional.empty();
		}

		@Override
		public Reader read() {
			try {
				return new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				return null;
			}
		}

		@Override
		public Optional<Variant> getVariant() {
			return variant;
		}

	}

	static class FileTemplateLocation implements TemplateLocator.TemplateLocation {
		private final Path file;
		private Optional<Variant> variant = Optional.empty();

		public FileTemplateLocation(Path file) {
			this.file = file;
			this.variant = Optional.empty();
		}

		@Override
		public Reader read() {
			try {
				return Files.newBufferedReader(file);
			} catch (IOException e) {
				return null;
			}
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
