package dev.jbang.dependencies;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.util.Util;

public class DependencyResolver {
	private final Set<MavenRepo> repositories;
	private final Set<String> dependencies;
	private final Set<ArtifactInfo> artifacts;

	public DependencyResolver() {
		repositories = new LinkedHashSet<>();
		dependencies = new LinkedHashSet<>();
		artifacts = new LinkedHashSet<>();
	}

	public DependencyResolver addRepository(MavenRepo repository) {
		repositories.add(repository);
		return this;
	}

	public DependencyResolver addRepositories(List<MavenRepo> repositories) {
		this.repositories.addAll(repositories);
		return this;
	}

	public DependencyResolver addDependency(String dependency) {
		dependencies.add(dependency);
		return this;
	}

	public DependencyResolver addDependencies(List<String> dependencies) {
		this.dependencies.addAll(dependencies);
		return this;
	}

	public DependencyResolver addArtifact(ArtifactInfo artifact) {
		artifacts.add(artifact);
		return this;
	}

	public DependencyResolver addArtifacts(List<ArtifactInfo> artifacts) {
		this.artifacts.addAll(artifacts);
		return this;
	}

	public DependencyResolver addClassPath(String classPath) {
		return addArtifact(DependencyCache.findArtifactByPath(new File(classPath)));
	}

	public DependencyResolver addClassPaths(List<String> classPaths) {
		for (String cp : classPaths) {
			addClassPath(cp);
		}
		return this;
	}

	public DependencyResolver addClassPaths(String classPaths) {
		return addClassPaths(Arrays.asList(classPaths.split(" ")));
	}

	public ModularClassPath resolve() {
		ModularClassPath mcp = DependencyUtil.resolveDependencies(
				new ArrayList<>(dependencies), new ArrayList<>(repositories),
				Util.isOffline(), Util.isFresh(), !Util.isQuiet());
		List<ArtifactInfo> arts = Stream.concat(mcp.getArtifacts().stream(), artifacts.stream())
										.collect(Collectors.toList());
		return new ModularClassPath(arts);
	}
}
