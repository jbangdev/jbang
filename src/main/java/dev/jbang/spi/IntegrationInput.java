package dev.jbang.spi;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

@JsonAdapter(IntegrationInput.Deserializer.class)
public class IntegrationInput {
	@SerializedName(value = "integration")
	public final String integrationClassName;
	public final Path source;
	public final Path classes;
	public final Path pom;
	public final Map<String, String> repositories;
	public final Map<String, Path> dependencies;
	public final List<String> comments;
	@SerializedName(value = "native")
	public final boolean nativeRequested;
	public final boolean verbose;

	public IntegrationInput(String integrationClassName, Path source, Path classes, Path pom,
			Map<String, String> repositories, Map<String, Path> dependencies, List<String> comments,
			boolean nativeRequested, boolean verbose) {
		this.integrationClassName = integrationClassName;
		this.source = source;
		this.classes = classes;
		this.pom = pom;
		this.repositories = repositories;
		this.dependencies = dependencies;
		this.comments = comments;
		this.nativeRequested = nativeRequested;
		this.verbose = verbose;
	}

	static final class Deserializer implements JsonDeserializer<IntegrationInput> {
		@Override
		public IntegrationInput deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
			JsonFields fields = context.deserialize(json, JsonFields.class);
			return new IntegrationInput(fields.integrationClassName, fields.source, fields.classes, fields.pom,
					fields.repositories, fields.dependencies, fields.comments, fields.nativeRequested, fields.verbose);
		}
	}

	private static final class JsonFields {
		@SerializedName(value = "integration")
		String integrationClassName;
		Path source;
		Path classes;
		Path pom;
		Map<String, String> repositories;
		Map<String, Path> dependencies;
		List<String> comments;
		@SerializedName(value = "native")
		boolean nativeRequested;
		boolean verbose;
	}
}
