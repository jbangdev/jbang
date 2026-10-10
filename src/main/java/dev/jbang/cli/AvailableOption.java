package dev.jbang.cli;

import java.util.Objects;

public class AvailableOption implements Comparable<AvailableOption> {
	public final String key;
	public final String description;

	public AvailableOption(String key, String description) {
		this.key = key;
		this.description = description;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		AvailableOption that = (AvailableOption) o;
		return key.equals(that.key);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key);
	}

	@Override
	public int compareTo(AvailableOption o) {
		return key.compareTo(o.key);
	}
}
