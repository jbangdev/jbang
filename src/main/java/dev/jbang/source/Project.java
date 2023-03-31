package dev.jbang.source;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.dependencies.DependencyResolver;
import dev.jbang.dependencies.MavenRepo;
import dev.jbang.dependencies.ModularClassPath;
import dev.jbang.source.sources.JavaSource;
import dev.jbang.util.ModuleUtil;
import dev.jbang.util.Util;

import eu.maveniverse.maven.mima.context.Context;

/**
 * This class gives access to all information necessary to turn source files
 * into something that can be executed. Typically, this means that it holds
 * references to source files, resources and dependencies which can be used by
 * the <code>AppBuilder</code> to create a JAR file, for example.
 */
public class Project {
	@Nonnull
	private final ResourceRef resourceRef;

	@Nonnull
	private final Context rootContext;

	private Source mainSource;

	// Public (user) input values (can be changed from the outside at any time)
	private final SourceSet mainSourceSet = new SourceSet();
	private final List<MavenRepo> repositories = new ArrayList<>();
	// TODO move runtimeOptions to CmdGenerator!
	private final List<String> runtimeOptions = new ArrayList<>();
	private Map<String, String> properties = new HashMap<>();
	private final Map<String, String> manifestAttributes = new LinkedHashMap<>();
	private String javaVersion;
	private String description;
	private String gav;
	private String mainClass;
	private String moduleName;
	private boolean nativeImage;
	private boolean enablePreviewRequested;

	// Cached values
	private String stableId;
	private ModularClassPath mcp;

	public static final String ATTR_PREMAIN_CLASS = "Premain-Class";
	public static final String ATTR_AGENT_CLASS = "Agent-Class";

	public boolean enablePreview() {
		return enablePreviewRequested || (mainSource != null && mainSource.enablePreview());
	}

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

	public Context getRootContext() {
		return rootContext;
	}

	public static ProjectBuilder builder() {
		return new ProjectBuilder();
	}

	public Project(@Nonnull ResourceRef resourceRef, @Nonnull Context rootContext) {
		this.resourceRef = resourceRef;
		this.rootContext = rootContext;
	}

	// TODO This should be refactored and removed
	public Project(@Nonnull Source mainSource, @Nonnull Context rootContext) {
		this(mainSource.getResourceRef(), rootContext);
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

	public void setEnablePreviewRequested(boolean enablePreview) {
		this.enablePreviewRequested = enablePreview;
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

	protected String getStableId() {
		if (stableId == null) {
			Stream<String> sss = mainSourceSet.getStableIdInfo();
			if (moduleName != null) {
				Stream<String> s = Stream.of(ModuleUtil.getModuleName(this));
				sss = Stream.concat(sss, s);
			}
			stableId = Util.getStableID(sss);
		}
		return stableId;
	}

	@Nonnull
	public ModularClassPath resolveClassPath() {
		if (mcp == null) {
			DependencyResolver resolver = new DependencyResolver(rootContext);
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
	public Builder<CmdGeneratorBuilder> codeBuilder() {
		return codeBuilder(BuildContext.forProject(this));
	}

	/**
	 * Returns a <code>Builder</code> that can be used to turn this
	 * <code>Project</code> into executable code.
	 *
	 * @param ctx will use the given <code>BuildContext</code> to store target files
	 *            and intermediate results
	 * @return A <code>Builder</code>
	 */
	@Nonnull
	public Builder<CmdGeneratorBuilder> codeBuilder(BuildContext ctx) {
		if (mainSource != null) {
			return mainSource.getBuilder(this, ctx);
		} else {
			if (isJar() && nativeImage) {
				return new JavaSource.JavaAppBuilder(this, ctx);
			} else {
				return () -> CmdGenerator.builder(this, ctx);
			}
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
