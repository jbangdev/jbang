package dev.jbang.source;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.dependencies.DependencyResolver;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.source.generators.JarCmdGenerator;
import dev.jbang.source.generators.JshCmdGenerator;
import dev.jbang.util.Util;

/**
 * This class gives access to all information necessary to turn source files
 * into something that can be executed. Typically, this means that it holds
 * references to source files, resources and dependencies which can be used by a
 * <code>Builder</code> to create a JAR file, for example.
 */
public class SourceSet implements Code {
	private final Source mainSource;

	private final List<ResourceRef> sources = new ArrayList<>();
	private final List<RefTarget> resources = new ArrayList<>();
	private final List<String> dependencies = new ArrayList<>();
	private final List<MavenRepo> repositories = new ArrayList<>();
	private final List<String> classPaths = new ArrayList<>();
	private final List<String> compileOptions = new ArrayList<>();
	private final List<String> runtimeOptions = new ArrayList<>();
	private final Map<String, String> manifestAttributes = new LinkedHashMap<>();
	private String javaVersion;
	private String description;
	private String gav;
	private String mainClass;

	private Path jarFile;
	private Jar jar;

	public static final String ATTR_PREMAIN_CLASS = "Premain-Class";
	public static final String ATTR_AGENT_CLASS = "Agent-Class";

	// TODO This should be refactored and removed
	public SourceSet(Source mainSource) {
		this.mainSource = mainSource;
	}

	@Nonnull
	public List<ResourceRef> getSources() {
		return Collections.unmodifiableList(sources);
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
		return this;
	}

	@Nonnull
	public SourceSet addDependencies(Collection<String> dependencies) {
		this.dependencies.addAll(dependencies);
		return this;
	}

	@Nonnull
	public List<MavenRepo> getRepositories() {
		return Collections.unmodifiableList(repositories);
	}

	@Nonnull
	public SourceSet addRepository(MavenRepo repository) {
		repositories.add(repository);
		return this;
	}

	@Nonnull
	public SourceSet addRepositories(Collection<MavenRepo> repositories) {
		this.repositories.addAll(repositories);
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
	public Map<String, String> getManifestAttributes() {
		return manifestAttributes;
	}

	public void setAgentMainClass(String agentMainClass) {
		manifestAttributes.put(ATTR_AGENT_CLASS, agentMainClass);
	}

	public void setPreMainClass(String preMainClass) {
		manifestAttributes.put(ATTR_PREMAIN_CLASS, preMainClass);
	}

	@Nullable
	public String getJavaVersion() {
		return javaVersion;
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

	@Override
	public String getMainClass() {
		return mainClass;
	}

	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	@Override
	public boolean enableCDS() {
		return getMainSource().enableCDS();
	}

	@Nonnull
	public DependencyResolver updateDependencyResolver(DependencyResolver resolver) {
		return resolver.addRepositories(repositories).addDependencies(dependencies).addClassPaths(classPaths);
	}

	public void copyResourcesTo(Path dest) {
		for (RefTarget file : resources) {
			file.copy(dest);
		}
	}

	@Nonnull
	public Source getMainSource() {
		return mainSource;
	}

	@Override
	@Nonnull
	public ResourceRef getResourceRef() {
		return sources.get(0);
	}

	@Override
	public Path getJarFile() {
		if (isJShell()) {
			return null;
		}
		if (jarFile == null) {
			Path baseDir = Settings.getCacheDir(Cache.CacheClass.jars);
			Path tmpJarDir = baseDir.resolve(getResourceRef().getFile().getFileName() + "." + getStableId());
			jarFile = tmpJarDir.getParent().resolve(tmpJarDir.getFileName() + ".jar");
		}
		return jarFile;
	}

	private String getStableId() {
		Stream<String> srcs = sources.stream().map(src -> Util.readFileContent(src.getFile()));
		Stream<String> ress = resources.stream().map(res -> Util.readFileContent(res.getSource().getFile()));
		Stream<String> files = Stream.concat(srcs, ress);
		return Util.getStableID(files);
	}

	@Override
	public Jar asJar() {
		if (jar == null) {
			Path f = getJarFile();
			if (f != null && Files.exists(f)) {
				jar = Jar.prepareJar(this);
			}
		}
		return jar;
	}

	@Override
	public SourceSet asSourceSet() {
		return this;
	}

	/**
	 * Returns a <code>Builder</code> that can be used to turn this
	 * <code>SourceSet</code> into executable code.
	 * 
	 * @param ctx A reference to a <code>RunContext</code>
	 * @return A <code>Builder</code>
	 */
	@Override
	public Builder builder(RunContext ctx) {
		return getMainSource().getBuilder(this, ctx);
	}

	/**
	 * Returns a <code>CmdGenerator</code> that can be used to generate the command
	 * line which, when used in a shell or any other CLI, would run this
	 * <code>SourceSet</code>'s code.
	 * 
	 * @param ctx A reference to a <code>RunContext</code>
	 * @return A <code>CmdGenerator</code>
	 */
	@Override
	public CmdGenerator cmdGenerator(RunContext ctx) {
		if (needsBuild(ctx)) {
			return new JarCmdGenerator(this, ctx);
		} else {
			return new JshCmdGenerator(this, ctx);
		}
	}
}
