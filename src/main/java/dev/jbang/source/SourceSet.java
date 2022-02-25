package dev.jbang.source;

import java.nio.file.Path;
import java.util.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.dependencies.DependencyResolver;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.dependencies.ModularClassPath;
import dev.jbang.util.JavaUtil;

public class SourceSet {
	private final List<ScriptSource> sources = new ArrayList<>();
	private final List<RefTarget> resources = new ArrayList<>();
	private final List<String> dependencies = new ArrayList<>();
	private final List<MavenRepo> repositories = new ArrayList<>();
	private final List<String> classPaths = new ArrayList<>();
	private final List<String> compileOptions = new ArrayList<>();
	private final List<String> runtimeOptions = new ArrayList<>();
	private final List<KeyValue> agentOptions = new ArrayList<>();
	private String javaVersion;
	private String description;
	private String gav;

	private ModularClassPath mcp;

	public static SourceSet forScript(ScriptSource mainSource) {
		return new SourceSet(mainSource);
	}

	private SourceSet(ScriptSource mainSource) {
		addSource(mainSource);
		this.description = mainSource.getDescription().orElse(null);
		this.gav = mainSource.getGav().orElse(null);
	}

	@Nonnull
	public List<ScriptSource> getSources() {
		return Collections.unmodifiableList(sources);
	}

	@Nonnull
	public SourceSet addSource(ScriptSource source) {
		HashSet<ResourceRef> refs = new HashSet<>();
		sources.stream().map(ScriptSource::getResourceRef).forEach(refs::add);
		addSource(source, javaVersion, refs);
		return this;
	}

	@Nonnull
	public SourceSet addSources(Collection<ScriptSource> sources) {
		HashSet<ResourceRef> refs = new HashSet<>();
		this.sources.stream().map(ScriptSource::getResourceRef).forEach(refs::add);
		for (ScriptSource source : sources) {
			addSource(source, javaVersion, refs);
		}
		return this;
	}

	@Nonnull
	private void addSource(ScriptSource source, String javaVersion, Set<ResourceRef> refs) {
		if (!refs.contains(source.getResourceRef())) {
			refs.add(source.getResourceRef());
			sources.add(source);
			resources.addAll(source.collectFiles());
			dependencies.addAll(source.collectDependencies());
			repositories.addAll(source.collectRepositories());
			compileOptions.addAll(source.getCompileOptions());
			runtimeOptions.addAll(source.getRuntimeOptions());
			agentOptions.addAll(source.collectAgentOptions());
			String version = source.getJavaVersion();
			if (version != null && JavaUtil.checkRequestedVersion(version)) {
				if (new JavaUtil.RequestedVersionComparator().compare(javaVersion, version) > 0) {
					javaVersion = version;
					this.javaVersion = version;
				}
			}
			for (ScriptSource includedSource : source.collectSources()) {
				addSource(includedSource, javaVersion, refs);
			}
		}
	}

	@Nonnull
	public List<RefTarget> getResources() {
		return Collections.unmodifiableList(resources);
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
		return Collections.unmodifiableList(dependencies);
	}

	@Nonnull
	public SourceSet addDependency(String dependency) {
		dependencies.add(dependency);
		mcp = null; // invalidate cached resolved classpath
		return this;
	}

	@Nonnull
	public SourceSet addDependencies(Collection<String> dependencies) {
		this.dependencies.addAll(dependencies);
		mcp = null; // invalidate cached resolved classpath
		return this;
	}

	@Nonnull
	public List<MavenRepo> getRepositories() {
		return Collections.unmodifiableList(repositories);
	}

	@Nonnull
	public SourceSet addRepository(MavenRepo repository) {
		repositories.add(repository);
		mcp = null; // invalidate cached resolved classpath
		return this;
	}

	@Nonnull
	public SourceSet addRepositories(Collection<MavenRepo> repositories) {
		this.repositories.addAll(repositories);
		mcp = null; // invalidate cached resolved classpath
		return this;
	}

	@Nonnull
	public List<String> getClassPaths() {
		return Collections.unmodifiableList(classPaths);
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
		return Collections.unmodifiableList(compileOptions);
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
	public List<String> getRuntimeOptions() {
		return Collections.unmodifiableList(runtimeOptions);
	}

	@Nonnull
	public SourceSet addRuntimeOption(String option) {
		runtimeOptions.add(option);
		return this;
	}

	@Nonnull
	public SourceSet addRuntimeOptions(Collection<String> options) {
		runtimeOptions.addAll(options);
		return this;
	}

	@Nonnull
	public List<KeyValue> getAgentOptions() {
		return Collections.unmodifiableList(agentOptions);
	}

	@Nonnull
	public SourceSet addAgentOption(KeyValue option) {
		agentOptions.add(option);
		return this;
	}

	@Nonnull
	public SourceSet addAgentOptions(Collection<KeyValue> options) {
		agentOptions.addAll(options);
		return this;
	}

	@Nullable
	public Optional<String> getJavaVersion() {
		return Optional.ofNullable(javaVersion);
	}

	@Nonnull
	public SourceSet setJavaVersion(String javaVersion) {
		this.javaVersion = javaVersion;
		return this;
	}

	@Nullable
	public Optional<String> getDescription() {
		return Optional.ofNullable(description);
	}

	@Nonnull
	public SourceSet setDescription(String description) {
		this.description = description;
		return this;
	}

	@Nullable
	public Optional<String> getGav() {
		return Optional.ofNullable(gav);
	}

	@Nonnull
	public SourceSet setGav(String gav) {
		this.gav = gav;
		return this;
	}

	@Nonnull
	public ModularClassPath getClassPath() {
		if (mcp == null) {
			DependencyResolver resolver = new DependencyResolver();
			updateDependencyResolver(resolver);
			mcp = resolver.resolve();
		}
		return mcp;
	}

	@Nonnull
	private DependencyResolver updateDependencyResolver(DependencyResolver resolver) {
		return resolver.addRepositories(repositories).addDependencies(dependencies).addClassPaths(classPaths);
	}

	public void copyResourcesTo(Path dest) {
		for (RefTarget file : resources) {
			file.copy(dest);
		}
	}

	@Nonnull
	public ScriptSource getMainSource() {
		return sources.get(0);
	}
}
