package dev.jbang.source;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import dev.jbang.dependencies.DependencyResolver;
import dev.jbang.util.Util;

/**
 * This class combines source files, resources and dependencies that are
 * considered to be a single unit. Together with the information in a
 * <code>Project</code> and possibly other <code>SourceSets</code> it can be
 * turned into something that can be executed.
 */
public class SourceSet {
	private final List<ResourceRef> sources = new ArrayList<>();
	private final List<RefTarget> resources = new ArrayList<>();
	private final List<String> dependencies = new ArrayList<>();
	private final List<String> classPaths = new ArrayList<>();
	private final List<String> compileOptions = new ArrayList<>();

	@Nonnull
	public List<ResourceRef> getSources() {
		return sources;
	}

	@Nonnull
	public SourceSet addSource(ResourceRef source) {
		sources.add(source);
		return this;
	}

	@Nonnull
	public SourceSet addSources(Collection<ResourceRef> sources) {
		this.sources.addAll(sources);
		return this;
	}

	@Nonnull
	public List<RefTarget> getResources() {
		return resources;
	}

	@Nonnull
	public SourceSet addResource(RefTarget resource) {
		resources.add(resource);
		return this;
	}

	@Nonnull
	public SourceSet addResources(Collection<RefTarget> resources) {
		this.resources.addAll(resources);
		return this;
	}

	@Nonnull
	public List<String> getDependencies() {
		return dependencies;
	}

	@Nonnull
	public SourceSet addDependency(String dependency) {
		dependencies.add(dependency);
		return this;
	}

	@Nonnull
	public SourceSet addDependencies(Collection<String> dependencies) {
		this.dependencies.addAll(dependencies);
		return this;
	}

	@Nonnull
	public List<String> getClassPaths() {
		return classPaths;
	}

	@Nonnull
	public SourceSet addClassPath(String classPath) {
		classPaths.add(classPath);
		return this;
	}

	@Nonnull
	public SourceSet addClassPaths(Collection<String> classPaths) {
		this.classPaths.addAll(classPaths);
		return this;
	}

	@Nonnull
	public List<String> getCompileOptions() {
		return compileOptions;
	}

	@Nonnull
	public SourceSet addCompileOption(String option) {
		compileOptions.add(option);
		return this;
	}

	@Nonnull
	public SourceSet addCompileOptions(Collection<String> options) {
		compileOptions.addAll(options);
		return this;
	}

	@Nonnull
	public DependencyResolver updateDependencyResolver(DependencyResolver resolver) {
		return resolver.addDependencies(dependencies).addClassPaths(classPaths);
	}

	public void copyResourcesTo(Path dest) {
		for (RefTarget file : resources) {
			file.copy(dest);
		}
	}

	public String getStableId() {
		Stream<String> srcs = sources.stream().map(src -> Util.readFileContent(src.getFile()));
		Stream<String> ress = resources.stream().map(res -> Util.readFileContent(res.getSource().getFile()));
		Stream<String> files = Stream.concat(srcs, ress);
		return Util.getStableID(files);
	}

}
