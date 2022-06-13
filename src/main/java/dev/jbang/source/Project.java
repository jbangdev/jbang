package dev.jbang.source;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.Cache;
import dev.jbang.Settings;
import dev.jbang.dependencies.DependencyResolver;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.source.generators.JarCmdGenerator;
import dev.jbang.source.generators.JshCmdGenerator;

/**
 * This class gives access to all information necessary to turn source files
 * into something that can be executed. Typically, this means that it holds
 * references to source files, resources and dependencies which can be used by a
 * <code>Builder</code> to create a JAR file, for example.
 */
public class Project implements Code {
	private final Source mainSource;

	private final SourceSet mainSourceSet = new SourceSet();
	private final List<MavenRepo> repositories = new ArrayList<>();
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
	public Project(Source mainSource) {
		this.mainSource = mainSource;
	}

	@Nonnull
	public SourceSet getMainSourceSet() {
		return mainSourceSet;
	}

	@Nonnull
	public List<MavenRepo> getRepositories() {
		return Collections.unmodifiableList(repositories);
	}

	@Nonnull
	public Project addRepository(MavenRepo repository) {
		repositories.add(repository);
		return this;
	}

	@Nonnull
	public Project addRepositories(Collection<MavenRepo> repositories) {
		this.repositories.addAll(repositories);
		return this;
	}

	@Nonnull
	public List<String> getRuntimeOptions() {
		return Collections.unmodifiableList(runtimeOptions);
	}

	@Nonnull
	public Project addRuntimeOption(String option) {
		runtimeOptions.add(option);
		return this;
	}

	@Nonnull
	public Project addRuntimeOptions(Collection<String> options) {
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
	public Project setJavaVersion(String javaVersion) {
		this.javaVersion = javaVersion;
		return this;
	}

	@Nullable
	public Optional<String> getDescription() {
		return Optional.ofNullable(description);
	}

	@Nonnull
	public Project setDescription(String description) {
		this.description = description;
		return this;
	}

	@Nullable
	public Optional<String> getGav() {
		return Optional.ofNullable(gav);
	}

	@Nonnull
	public Project setGav(String gav) {
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
		resolver.addRepositories(repositories);
		return getMainSourceSet().updateDependencyResolver(resolver);
	}

	@Nonnull
	public Source getMainSource() {
		return mainSource;
	}

	@Override
	@Nonnull
	public ResourceRef getResourceRef() {
		return mainSource.getResourceRef();
	}

	@Override
	public Path getJarFile() {
		if (isJShell()) {
			return null;
		}
		if (jarFile == null) {
			Path baseDir = Settings.getCacheDir(Cache.CacheClass.jars);
			Path tmpJarDir = baseDir.resolve(
					getResourceRef().getFile().getFileName() + "." + getMainSourceSet().getStableId());
			jarFile = tmpJarDir.getParent().resolve(tmpJarDir.getFileName() + ".jar");
		}
		return jarFile;
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
	public Project asProject() {
		return this;
	}

	/**
	 * Returns a <code>Builder</code> that can be used to turn this
	 * <code>Project</code> into executable code.
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
	 * <code>Project</code>'s code.
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
