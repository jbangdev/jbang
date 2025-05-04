package dev.jbang.util;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class PathTypeAdapter extends TypeAdapter<Path> {
	@Override
	public Path read(JsonReader in) throws IOException {
		if (in.peek() == JsonToken.NULL) {
			in.nextNull();
			return null;
		}
		String path = in.nextString();
		return Paths.get(path);
	}

	@Override
	public void write(JsonWriter out, Path path) throws IOException {
		if (path == null) {
			out.nullValue();
			return;
		}
		out.value(path.toString());
	}
}
