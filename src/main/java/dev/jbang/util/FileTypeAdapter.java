package dev.jbang.util;

import java.io.File;
import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class FileTypeAdapter extends TypeAdapter<File> {
	@Override
	public File read(JsonReader in) throws IOException {
		if (in.peek() == JsonToken.NULL) {
			in.nextNull();
			return null;
		}
		String path = in.nextString();
		return new File(path);
	}

	@Override
	public void write(JsonWriter out, File path) throws IOException {
		if (path == null) {
			out.nullValue();
			return;
		}
		out.value(path.getPath());
	}
}
