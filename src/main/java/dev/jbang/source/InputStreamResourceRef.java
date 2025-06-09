package dev.jbang.source;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nonnull;

import dev.jbang.util.Util;

public class InputStreamResourceRef implements ResourceRef {
	@Nonnull
	protected final String originalResource;
	@Nonnull
	protected final Function<String, InputStream> streamFactory;

	public InputStreamResourceRef(@Nonnull String resource,
			@Nonnull Function<String, InputStream> streamFactory) {
		this.originalResource = resource;
		this.streamFactory = streamFactory;
	}

	@Nonnull
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

	@Nonnull
	@Override
	public InputStream getInputStream() {
		InputStream is = streamFactory.apply(getOriginalResource());
		if (is == null) {
			throw new ResourceNotFoundException(getOriginalResource(), "Could not obtain input stream resource");
		}
		return is;
	}

	@Nonnull
	@Override
	public String getExtension() {
		return Util.extension(getOriginalResource());
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
	public int compareTo(ResourceRef o) {
		if (o == null) {
			return 1;
		}
		return toString().compareTo(o.toString());
	}

	@Override
	public String toString() {
		return getOriginalResource();
	}

}
