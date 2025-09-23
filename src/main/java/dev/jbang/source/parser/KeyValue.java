package dev.jbang.source.parser;

public class KeyValue {
	private final String key;
	private final String value;

	public KeyValue(String key, String value) {
		this.key = key;
		this.value = value;
	}

	public String getKey() {
		return key;
	}

	public String getValue() {
		return value;
	}

	public static KeyValue of(String line) {
		String[] split = line.split("=");
		String key;
		String value = null;

		if (split.length == 1) {
			key = split[0];
		} else if (split.length == 2) {
			key = split[0];
			value = split[1];
		} else {
			throw new IllegalStateException("Invalid key/value: " + line);
		}

		return new KeyValue(key, value);
	}

	public String toManifestString() {
		return getKey() + ": " + value;
	}

	@Override
	public String toString() {
		return getKey() + "=" + getValue() == null ? "" : getValue();
	}
}
