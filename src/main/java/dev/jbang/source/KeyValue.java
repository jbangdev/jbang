package dev.jbang.source;

public class KeyValue {
	final String key;
	final String value;

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

	public String toManifestString() {
		return getKey() + ": " + value;
	}

	@Override
	public String toString() {
		return getKey() + "=" + getValue() == null ? "" : getValue();
	}
}
