package dk.xam.jbang;

import org.sonatype.aether.resolution.DependencyResolutionException;

@SuppressWarnings("serial")
public class DependencyException extends RuntimeException {

	public DependencyException(DependencyResolutionException e) {
		super(e);
	}

	
}
