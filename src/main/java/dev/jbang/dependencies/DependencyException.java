package dev.jbang.dependencies;

@SuppressWarnings("serial")
public class DependencyException extends RuntimeException {

	public DependencyException(Exception e) {
		super(e);
	}

}
