package dev.jbang.dependencies;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.util.Util;

public class DependencyResolver {
	private final Set<MavenRepo> repositories;
	private final Set<String> dependencies;
	private final Set<String> classPaths;

	public DependencyResolver() {
		repositories = new LinkedHashSet<>();
		dependencies = new LinkedHashSet<>();
		classPaths = new LinkedHashSet<>();
	}

	public DependencyResolver addRepository(MavenRepo repository) {
		MavenRepo repo = repositories.stream()
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

	public DependencyResolver addClassPath(String classPath) {
		classPaths.add(classPath);
		return this;
	}

	public DependencyResolver addClassPaths(List<String> classPaths) {
		for (String cp : classPaths) {
			addClassPath(cp);
		}
		return this;
	}

	public ModularClassPath resolve() {
		ModularClassPath mcp = DependencyUtil.resolveDependencies(
				new ArrayList<>(dependencies), new ArrayList<>(repositories),
				Util.isOffline(), Util.isIgnoreTransitiveRepositories(), Util.isFresh(), !Util.isQuiet(),
				Util.downloadSources());
		if (classPaths.isEmpty()) {
			return mcp;
		} else {
			// WARN need File here because it's more lenient about paths than Path!
			Stream<ArtifactInfo> cpas = classPaths
				.stream()
				.map(p -> new ArtifactInfo(null, new File(p).toPath()));
			List<ArtifactInfo> arts = Stream.concat(mcp.getArtifacts().stream(), cpas)
				.collect(Collectors.toList());
			return new ModularClassPath(arts);
		}
	}
}
