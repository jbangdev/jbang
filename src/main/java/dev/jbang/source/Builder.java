package dev.jbang.source;

import java.io.IOException;

public interface Builder<T> {
	T build() throws IOException;
}
