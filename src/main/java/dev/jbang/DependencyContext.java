package dev.jbang;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class to capture context for dependencies, i.e. optional repositories,
 * dependencies and classpath that should be considered.
 * 
 */
public class DependencyContext {

	private List<String> repositories;
	private List<String> dependencies;
	private List<String> classpaths;

	public DependencyContext(List<String> repositories, List<String> dependencies, List<String> classpaths) {
		this.setRepositories(repositories);
		this.setDependencies(dependencies);
		this.setClasspaths(classpaths);
	}

	public DependencyContext() {
		this.repositories = Collections.emptyList();
		this.dependencies = Collections.emptyList();
		this.classpaths = Collections.emptyList();
	}

	List<String> safeList(List<String> list) {
		if (list == null) {
			return Collections.emptyList();
		} else {
			return Collections.unmodifiableList(list);
		}
	}

	public List<String> getRepositories() {
		return repositories;
	}

	// TODO: should have properties inherited rather than gobal system
	public List<MavenRepo> getRepositoriesAsMavenRepo() {
		return repositories	.stream()
							.map(PropertiesValueResolver::replaceProperties)
							.map(DependencyUtil::toMavenRepo)
							.collect(Collectors.toList());
	}

	public DependencyContext setRepositories(List<String> repositories) {
		this.repositories = safeList(repositories);
		return this;
	}

	public List<String> getDependencies() {
		return dependencies;
	}

	public DependencyContext setDependencies(List<String> dependencies) {
		this.dependencies = safeList(dependencies);
		return this;
	}

	public List<String> getClasspaths() {
		return classpaths;
	}

	public DependencyContext setClasspaths(List<String> classpaths) {
		this.classpaths = safeList(classpaths);
		return this;
	}
}