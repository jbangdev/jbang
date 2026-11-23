package dev.jbang.spi;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class IntegrationInput {
	@SerializedName(value = "integration")
	private String integrationClassName;
	private Path source;
	private Path classes;
	private Path pom;
	private Map<String, String> repositories;
	private Map<String, Path> dependencies;
	private List<String> comments;
	@SerializedName(value = "native")
	private boolean nativeRequested;
	private boolean verbose;

	public IntegrationInput(String integrationClassName, Path source, Path classes, Path pom,
			Map<String, String> repositories, Map<String, Path> dependencies, List<String> comments,
			boolean nativeRequested, boolean verbose) {
		this.integrationClassName = integrationClassName;
		this.source = source;
		this.classes = classes;
		this.pom = pom;
		this.repositories = repositories;
		this.dependencies = dependencies;
		this.comments = comments;
		this.nativeRequested = nativeRequested;
		this.verbose = verbose;
	}

	public String integrationClassName() {
		return integrationClassName;
	}

	public Path source() {
		return source;
	}

	public Path classes() {
		return classes;
	}

	public Path pom() {
		return pom;
	}

	public Map<String, String> repositories() {
		return repositories;
	}

	public Map<String, Path> dependencies() {
		return dependencies;
	}

	public List<String> comments() {
		return comments;
	}

	public boolean nativeRequested() {
		return nativeRequested;
	}

	public boolean verbose() {
		return verbose;
	}
}
