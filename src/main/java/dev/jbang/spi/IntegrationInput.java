package dev.jbang.spi;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class IntegrationInput {
	@SerializedName(value = "integration")
	public final String integrationClassName;
	public final Path source;
	public final Path classes;
	public final Path pom;
	public final Map<String, String> repositories;
	public final Map<String, Path> dependencies;
	public final List<String> comments;
	@SerializedName(value = "native")
	public final boolean nativeRequested;
	public final boolean verbose;

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
}
