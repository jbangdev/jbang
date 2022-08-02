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
		MavenRepo repo = repositories	.stream()
										.filter(r -> r.getId().equals(repository.getId()))
										.findFirst()
										.orElse(null);
		if (repo != null && !repo.getUrl().equals(repository.getUrl())) {
			throw new IllegalArgumentException("Repository with duplicate id and different url: "
					+ repository + " vs " + repo);
		}
		if (repo == null) {
			repositories.add(repository);
		}
		return this;
	}

	public DependencyResolver addRepositories(List<MavenRepo> repositories) {
		for (MavenRepo repo : repositories) {
			addRepository(repo);
		}
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
		// WARN need File here because it's more lenient about paths than Path!
		return addArtifact(DependencyCache.findArtifactByPath(new File(classPath).toPath()));
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
		if (artifacts.isEmpty()) {
			return mcp;
		} else {
			List<ArtifactInfo> arts = Stream.concat(mcp.getArtifacts().stream(), artifacts.stream())
											.collect(Collectors.toList());
			return new ModularClassPath(arts);
		}
	}
}
