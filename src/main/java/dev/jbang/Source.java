package dev.jbang;

import java.nio.file.Path;
import java.util.Optional;

public class Source {
	private Path resolvedPath;
	private Optional<String> javaPackage;

	public Source(Path resolvedPath, Optional<String> javaPackage) {
		this.resolvedPath = resolvedPath;
		this.javaPackage = javaPackage;
	}

	public Path getResolvedPath() {
		return resolvedPath;
	}

	public void setResolvedPath(Path resolvedPath) {
		this.resolvedPath = resolvedPath;
	}

	public Optional<String> getJavaPackage() {
		return javaPackage;
	}

	public void setJavaPackage(Optional<String> javaPackage) {
		this.javaPackage = javaPackage;
	}

	@Override
	public String toString() {
		return "Source [javaPackage=" + javaPackage + ", resolvedPath=" + resolvedPath + "]";
	}

}
