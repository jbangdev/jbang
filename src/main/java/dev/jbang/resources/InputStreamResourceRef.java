package dev.jbang.resources;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Function;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class InputStreamResourceRef implements ResourceRef {
	@Nullable
	protected final String originalResource;
	@NonNull
	protected final Function<String, InputStream> streamFactory;

	public InputStreamResourceRef(@Nullable String resource,
			@NonNull Function<String, InputStream> streamFactory) {
		this.originalResource = resource;
		this.streamFactory = streamFactory;
	}

	@Nullable
	@Override
	public String getOriginalResource() {
		return originalResource;
	}

	@Override
	public boolean exists() {
		try (InputStream is = getInputStream()) {
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	@NonNull
	@Override
	public InputStream getInputStream() {
		InputStream is = streamFactory.apply(getOriginalResource());
		if (is == null) {
			throw new ResourceNotFoundException(getOriginalResource(), "Could not obtain input stream resource");
		}
		return is;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ResourceRef))
			return false;
		ResourceRef that = (ResourceRef) o;
		return Objects.equals(getOriginalResource(), that.getOriginalResource());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getOriginalResource());
	}

	@Override
	public int compareTo(@NonNull ResourceRef o) {
		return toString().compareTo(o.toString());
	}

	@Override
	public String toString() {
		return getOriginalResource();
	}

}
