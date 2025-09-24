package dev.jbang.source;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;

import dev.jbang.dependencies.DependencyResolver;
import dev.jbang.resources.ResourceRef;
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
	private final List<String> nativeOptions = new ArrayList<>();

	@NonNull
	public List<ResourceRef> getSources() {
		return Collections.unmodifiableList(sources);
	}

	@NonNull
	public SourceSet addSource(ResourceRef source) {
		sources.add(source);
		return this;
	}

	@NonNull
	public SourceSet addSources(Collection<ResourceRef> sources) {
		this.sources.addAll(sources);
		return this;
	}

	@NonNull
	public List<RefTarget> getResources() {
		return Collections.unmodifiableList(resources);
	}

	@NonNull
	public SourceSet addResource(RefTarget resource) {
		resources.add(resource);
		return this;
	}

	@NonNull
	public SourceSet addResources(Collection<RefTarget> resources) {
		this.resources.addAll(resources);
		return this;
	}

	@NonNull
	public List<String> getDependencies() {
		return Collections.unmodifiableList(dependencies);
	}

	@NonNull
	public SourceSet addDependency(String dependency) {
		dependencies.add(dependency);
		return this;
	}

	@NonNull
	public SourceSet addDependencies(Collection<String> dependencies) {
		this.dependencies.addAll(dependencies);
		return this;
	}

	@NonNull
	public List<String> getClassPaths() {
		return Collections.unmodifiableList(classPaths);
	}

	@NonNull
	public SourceSet addClassPath(String classPath) {
		classPaths.add(classPath);
		return this;
	}

	@NonNull
	public SourceSet addClassPaths(Collection<String> classPaths) {
		this.classPaths.addAll(classPaths);
		return this;
	}

	@NonNull
	public List<String> getCompileOptions() {
		return Collections.unmodifiableList(compileOptions);
	}

	@NonNull
	public SourceSet addCompileOption(String option) {
		compileOptions.add(option);
		return this;
	}

	@NonNull
	public SourceSet addCompileOptions(Collection<String> options) {
		compileOptions.addAll(options);
		return this;
	}

	@NonNull
	public List<String> getNativeOptions() {
		return Collections.unmodifiableList(nativeOptions);
	}

	@NonNull
	public SourceSet addNativeOption(String option) {
		nativeOptions.add(option);
		return this;
	}

	@NonNull
	public SourceSet addNativeOptions(Collection<String> options) {
		nativeOptions.addAll(options);
		return this;
	}

	@NonNull
	public DependencyResolver updateDependencyResolver(DependencyResolver resolver) {
		return resolver.addDependencies(dependencies).addClassPaths(classPaths);
	}

	public void copyResourcesTo(Path dest) {
		for (RefTarget file : resources) {
			file.copy(dest);
		}
	}

	protected Stream<String> getStableIdInfo() {
		Stream<String> srcs = sources.stream().map(this::safeFileContents);
		Stream<String> ress = resources.stream().map(res -> safeFileContents(res.getSource()));
		return Stream.concat(srcs, ress);
	}

	private String safeFileContents(ResourceRef ref) {
		try {
			return Util.readFileContent(ref.getFile());
		} catch (Exception e) {
			return "";
		}
	}

}
