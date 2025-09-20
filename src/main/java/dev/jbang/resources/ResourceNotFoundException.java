package dev.jbang.resources;

/**
 * Used when something could not be found.
 */
public class ResourceNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	String resource;

	public ResourceNotFoundException(String resource, String message, Throwable cause) {
		super(message, cause);
		this.resource = resource;
	}

	public ResourceNotFoundException(String resource, String message) {
		this(resource, message, null);
	}

	public String getResourceDescription() {
		return resource;
	}
}
