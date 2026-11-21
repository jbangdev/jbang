package dev.jbang.spi;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.annotations.JsonAdapter;

@JsonAdapter(IntegrationResult.Deserializer.class)
public class IntegrationResult {

	public final Path nativeImagePath;
	public final String mainClass;
	public final List<String> javaArgs;

	public IntegrationResult(Path nativeImagePath, String mainClass, List<String> javaArgs) {
		this.nativeImagePath = nativeImagePath;
		this.mainClass = mainClass;
		this.javaArgs = javaArgs;
	}

	public IntegrationResult merged(IntegrationResult ir) {
		if (ir.nativeImagePath == null && ir.mainClass == null && ir.javaArgs == null) {
			return this;
		} else if (nativeImagePath == null && mainClass == null && javaArgs == null) {
			return ir;
		} else {
			return new IntegrationResult(
					nativeImagePath != null ? nativeImagePath : ir.nativeImagePath,
					mainClass != null ? mainClass : ir.mainClass,
					javaArgs != null ? javaArgs : ir.javaArgs);
		}
	}

	static final class Deserializer implements JsonDeserializer<IntegrationResult> {
		@Override
		public IntegrationResult deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
			JsonFields fields = context.deserialize(json, JsonFields.class);
			return new IntegrationResult(fields.nativeImagePath, fields.mainClass, fields.javaArgs);
		}
	}

	private static final class JsonFields {
		Path nativeImagePath;
		String mainClass;
		List<String> javaArgs;
	}
}
