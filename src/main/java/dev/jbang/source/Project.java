package dev.jbang.source;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.DependencyResolver;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.dependencies.ModularClassPath;
import dev.jbang.source.sources.JavaSource;
import dev.jbang.util.Util;

/**
 * This class gives access to all information necessary to turn source files
 * into something that can be executed. Typically, this means that it holds
 * references to source files, resources and dependencies which can be used by
 * the <code>AppBuilder</code> to create a JAR file, for example.
 */
public class Project {
	@Nonnull
	private final ResourceRef resourceRef;
	private Source mainSource;
	private Function<BuildContext, CmdGenerator> cmdGeneratorFactory;

	// Public (user) input values (can be changed from the outside at any time)
	private final SourceSet mainSourceSet = new SourceSet();
	private final List<MavenRepo> repositories = new ArrayList<>();
	private final List<String> runtimeOptions = new ArrayList<>();
	private Map<String, String> properties = new HashMap<>();
	private final Map<String, String> manifestAttributes = new LinkedHashMap<>();
	private List<Project> javaAgents = new ArrayList<>();
	private String javaVersion;
	private String description;
	private String gav;
	private String mainClass;
	private String moduleName;
	private boolean nativeImage;

	// Cached values
	private String stableId;
	private ModularClassPath mcp;

	public static final String ATTR_PREMAIN_CLASS = "Premain-Class";
	public static final String ATTR_AGENT_CLASS = "Agent-Class";

	public enum BuildFile {
		jbang("build.jbang");

		public final String fileName;

		BuildFile(String fileName) {
			this.fileName = fileName;
		}

		public static List<String> fileNames() {
			return Arrays.stream(values()).map(v -> v.fileName).collect(Collectors.toList());
		}
	}

	public Project(@Nonnull ResourceRef resourceRef) {
		this.resourceRef = resourceRef;
	}

	// TODO This should be refactored and removed
	public Project(@Nonnull Source mainSource) {
		this.resourceRef = mainSource.getResourceRef();
		this.mainSource = mainSource;
	}

	@Nonnull
	public ResourceRef getResourceRef() {
		return resourceRef;
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
	public Project addRepository(@Nonnull MavenRepo repository) {
		repositories.add(repository);
		return this;
	}

	@Nonnull
	public Project addRepositories(@Nonnull Collection<MavenRepo> repositories) {
		this.repositories.addAll(repositories);
		return this;
	}

	@Nonnull
	public List<String> getRuntimeOptions() {
		return Collections.unmodifiableList(runtimeOptions);
	}

	@Nonnull
	public Project addRuntimeOption(@Nonnull String option) {
		runtimeOptions.add(option);
		return this;
	}

	@Nonnull
	public Project addRuntimeOptions(@Nonnull Collection<String> options) {
		runtimeOptions.addAll(options);
		return this;
	}

	@Nonnull
	public List<Project> getJavaAgents() {
		return Collections.unmodifiableList(javaAgents);
	}

	@Nonnull
	public Project addJavaAgents(List<Project> javaAgents) {
		this.javaAgents.addAll(javaAgents);
		return this;
	}

	@Nonnull
	public Map<String, String> getProperties() {
		return Collections.unmodifiableMap(properties);
	}

	public void putProperties(@Nonnull Map<String, String> properties) {
		this.properties = properties;
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

	@Nonnull
	public Optional<String> getDescription() {
		return Optional.ofNullable(description);
	}

	@Nonnull
	public Project setDescription(String description) {
		this.description = description;
		return this;
	}

	@Nonnull
	public Optional<String> getGav() {
		return Optional.ofNullable(gav);
	}

	@Nonnull
	public Project setGav(String gav) {
		this.gav = gav;
		return this;
	}

	public String getMainClass() {
		return mainClass;
	}

	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	@Nonnull
	public Optional<String> getModuleName() {
		return Optional.ofNullable(moduleName);
	}

	@Nonnull
	public Project setModuleName(String moduleName) {
		this.moduleName = moduleName;
		return this;
	}

	public boolean isNativeImage() {
		return nativeImage;
	}

	public void setNativeImage(boolean isNative) {
		this.nativeImage = isNative;
	}

	public boolean enableCDS() {
		return mainSource != null && mainSource.enableCDS();
	}

	@Nullable
	public Source getMainSource() {
		return mainSource;
	}

	public void setMainSource(Source mainSource) {
		this.mainSource = mainSource;
	}

	public void setCmdGeneratorFactory(Function<BuildContext, CmdGenerator> cmdGeneratorFactory) {
		this.cmdGeneratorFactory = cmdGeneratorFactory;
	}

	protected String getStableId() {
		if (stableId == null) {
			Stream<String> sss = mainSourceSet.getStableIdInfo();
			if (moduleName != null) {
				Stream<String> ps = Stream.of(moduleName);
				Stream<String> info = Stream.concat(sss, ps);
				stableId = Util.getStableID(info);
			} else {
				stableId = Util.getStableID(sss);
			}
		}
		return stableId;
	}

	@Nonnull
	public ModularClassPath resolveClassPath() {
		if (mcp == null) {
			DependencyResolver resolver = new DependencyResolver();
			updateDependencyResolver(resolver);
			mcp = resolver.resolve();
		}
		return mcp;
	}

	@Nonnull
	public DependencyResolver updateDependencyResolver(DependencyResolver resolver) {
		resolver.addRepositories(repositories);
		return getMainSourceSet().updateDependencyResolver(resolver);
	}

	/**
	 * Returns a <code>Builder</code> that can be used to turn this
	 * <code>Project</code> into executable code.
	 * 
	 * @return A <code>Builder</code>
	 */
	@Nonnull
	public Builder<Project> builder(BuildContext ctx) {
		if (mainSource != null) {
			return mainSource.getBuilder(this, ctx);
		} else {
			if (isJar() && nativeImage) {
				return new JavaSource.JavaAppBuilder(this, ctx);
			} else {
				return () -> this;
			}
		}
	}

	/**
	 * Returns a <code>CmdGenerator</code> that can be used to generate the command
	 * line which, when used in a shell or any other CLI, would run this
	 * <code>Project</code>'s code.
	 *
	 * @return A <code>CmdGenerator</code>
	 */
	@Nonnull
	public CmdGenerator cmdGenerator(BuildContext ctx) {
		if (cmdGeneratorFactory != null) {
			return cmdGeneratorFactory.apply(ctx);
		} else {
			throw new ExitException(BaseCommand.EXIT_INTERNAL_ERROR, "Missing CmdGenerator factory for Project");
		}
	}

	public boolean isJar() {
		return Project.isJar(getResourceRef().getFile());
	}

	static boolean isJar(Path backingFile) {
		return backingFile != null && backingFile.toString().endsWith(".jar");
	}

	public boolean isJShell() {
		return Project.isJShell(getResourceRef().getFile());
	}

	static boolean isJShell(Path backingFile) {
		return backingFile != null && backingFile.toString().endsWith(".jsh");
	}

	// https://stackoverflow.com/questions/366202/regex-for-splitting-a-string-using-space-when-not-surrounded-by-single-or-double
	static List<String> quotedStringToList(String subjectString) {
		List<String> matchList = new ArrayList<>();
		Pattern regex = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
		Matcher regexMatcher = regex.matcher(subjectString);
		while (regexMatcher.find()) {
			if (regexMatcher.group(1) != null) {
				// Add double-quoted string without the quotes
				matchList.add(regexMatcher.group(1));
			} else if (regexMatcher.group(2) != null) {
				// Add single-quoted string without the quotes
				matchList.add(regexMatcher.group(2));
			} else {
				// Add unquoted word
				matchList.add(regexMatcher.group());
			}
		}
		return matchList;
	}
}
