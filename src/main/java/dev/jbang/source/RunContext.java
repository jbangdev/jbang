package dev.jbang.source;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.*;
import dev.jbang.source.resolvers.*;
import dev.jbang.util.PropertiesValueResolver;
import dev.jbang.util.Util;

/**
 * This class contains all the extra information needed to actually run a
 * Source. These are either options given by the user on the command line or
 * things that are part of the user's environment. It's all the dynamic parts of
 * the execution where the Source is immutable. The RunContext and the Source
 * together determine what finally gets executed and how.
 */
public class RunContext {
	private List<String> additionalSources = Collections.emptyList();
	private List<String> additionalResources = Collections.emptyList();
	private List<String> additionalDeps = Collections.emptyList();
	private List<String> additionalRepos = Collections.emptyList();
	private List<String> additionalClasspaths = Collections.emptyList();
	private Map<String, String> properties = Collections.emptyMap();
	private Source.Type forceType = null;
	private String mainClass;
	private String javaAgentOption;
	private List<AgentSourceContext> javaAgents;
	private File catalogFile;

	private boolean alias;

	private ModularClassPath mcp;
	private boolean nativeImage;
	private String javaVersion;
	private Properties contextProperties;

	private List<String> arguments = Collections.emptyList();
	private List<String> javaOptions = Collections.emptyList();
	private boolean mainRequired;
	private boolean interactive;
	private boolean enableAssertions;
	private boolean enableSystemAssertions;
	private String flightRecorderString;
	private String debugString;
	private Boolean classDataSharing;

	public static RunContext empty() {
		return new RunContext();
	}

	public List<String> getArguments() {
		return Collections.unmodifiableList(arguments);
	}

	public void setArguments(List<String> arguments) {
		if (arguments != null) {
			this.arguments = new ArrayList<>(arguments);
		} else {
			this.arguments = Collections.emptyList();
		}
	}

	public Map<String, String> getProperties() {
		return Collections.unmodifiableMap(properties);
	}

	public void setProperties(Map<String, String> properties) {
		if (properties != null) {
			this.properties = properties;
		} else {
			this.properties = Collections.emptyMap();
		}
	}

	public boolean isMainRequired() {
		return mainRequired;
	}

	public void setMainRequired(boolean mainRequired) {
		this.mainRequired = mainRequired;
	}

	public boolean isInteractive() {
		return interactive;
	}

	public void setInteractive(boolean interactive) {
		this.interactive = interactive;
	}

	public boolean isEnableAssertions() {
		return enableAssertions;
	}

	public void setEnableAssertions(boolean enableAssertions) {
		this.enableAssertions = enableAssertions;
	}

	public boolean isEnableSystemAssertions() {
		return enableSystemAssertions;
	}

	public void setEnableSystemAssertions(boolean enableSystemAssertions) {
		this.enableSystemAssertions = enableSystemAssertions;
	}

	public String getFlightRecorderString() {
		return flightRecorderString;
	}

	public void setFlightRecorderString(String flightRecorderString) {
		this.flightRecorderString = flightRecorderString;
	}

	public boolean isFlightRecordingEnabled() {
		return flightRecorderString != null && !flightRecorderString.isEmpty();
	}

	public String getDebugString() {
		return debugString;
	}

	public void setDebugString(String debugString) {
		this.debugString = debugString;
	}

	public boolean isDebugEnabled() {
		return debugString != null && !debugString.isEmpty();
	}

	public Boolean getClassDataSharing() {
		return classDataSharing;
	}

	public void setClassDataSharing(Boolean classDataSharing) {
		this.classDataSharing = classDataSharing;
	}

	public void setAdditionalSources(List<String> sources) {
		if (sources != null) {
			this.additionalSources = new ArrayList<>(sources);
		} else {
			this.additionalSources = Collections.emptyList();
		}
	}

	public void setAdditionalResources(List<String> resources) {
		if (resources != null) {
			this.additionalResources = new ArrayList<>(resources);
		} else {
			this.additionalResources = Collections.emptyList();
		}
	}

	public void setAdditionalDependencies(List<String> deps) {
		if (deps != null) {
			this.additionalDeps = new ArrayList<>(deps);
		} else {
			this.additionalDeps = Collections.emptyList();
		}
		mcp = null;
	}

	public void setAdditionalRepositories(List<String> repos) {
		if (repos != null) {
			this.additionalRepos = repos;
		} else {
			this.additionalRepos = Collections.emptyList();
		}
		mcp = null;
	}

	public void setAdditionalClasspaths(List<String> cps) {
		if (cps != null) {
			this.additionalClasspaths = new ArrayList<>(cps);
		} else {
			this.additionalClasspaths = Collections.emptyList();
		}
		mcp = null;
	}

	/**
	 * Returns true if originalRef is an alias, otherwise false
	 */
	public boolean isAlias() {
		return alias;
	}

	/**
	 * Sets if originalRef is an alias or not
	 */
	public void setAlias(boolean alias) {
		this.alias = alias;
	}

	public Source.Type getForceType() {
		return forceType;
	}

	public void setForceType(Source.Type forceType) {
		this.forceType = forceType;
	}

	public String getMainClassOr(Code code) {
		return (mainClass != null) ? mainClass : code.getMainClass();
	}

	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	public void setJavaOptions(List<String> javaOptions) {
		if (javaOptions != null) {
			this.javaOptions = javaOptions;
		} else {
			this.javaOptions = Collections.emptyList();
		}
	}

	public List<String> getRuntimeOptionsMerged(Code code) {
		List<String> opts = new ArrayList<>(code.getRuntimeOptions());
		opts.addAll(javaOptions);
		return opts;
	}

	public String getJavaAgentOption() {
		return javaAgentOption;
	}

	public void setJavaAgentOption(String option) {
		this.javaAgentOption = option;
	}

	public boolean isNativeImage() {
		return nativeImage;
	}

	public void setNativeImage(boolean nativeImage) {
		this.nativeImage = nativeImage;
	}

	public String getJavaVersionOr(Code code) {
		return javaVersion != null ? javaVersion : code.getJavaVersion();
	}

	public void setJavaVersion(String javaVersion) {
		this.javaVersion = javaVersion;
	}

	public void setCatalog(File catalogFile) {
		this.catalogFile = catalogFile;
	}

	private Properties getContextProperties() {
		if (contextProperties == null) {
			contextProperties = new Properties(System.getProperties());
			// early/eager init to property resolution will work.
			new Detector().detect(contextProperties, Collections.emptyList());

			contextProperties.putAll(getProperties());
		}
		return contextProperties;
	}

	public static class AgentSourceContext {
		final public Code source;
		final public RunContext context;

		private AgentSourceContext(Code code, RunContext context) {
			this.source = code;
			this.context = context;
		}
	}

	public List<AgentSourceContext> getJavaAgents() {
		return javaAgents != null ? javaAgents : Collections.emptyList();
	}

	public void addJavaAgent(Code code, RunContext ctx) {
		if (javaAgents == null) {
			javaAgents = new ArrayList<>();
		}
		javaAgents.add(new AgentSourceContext(code, ctx));
	}

	/**
	 * Return resolved classpath lazily. resolution will only happen once, any
	 * consecutive calls return the same classpath. Properties available will be
	 * used for property replacement.
	 */
	public ModularClassPath resolveClassPath(Code code) {
		if (mcp == null) {
			DependencyResolver resolver = new DependencyResolver();
			code.asProject().updateDependencyResolver(resolver);
			mcp = resolver.resolve();
		}
		return mcp;
	}

	private DependencyResolver updateDependencyResolver(DependencyResolver resolver) {
		return resolver
						.addRepositories(allToMavenRepo(replaceAllProps(
								additionalRepos)))
						.addDependencies(replaceAllProps(
								additionalDeps))
						.addClassPaths(
								replaceAllProps(additionalClasspaths));
	}

	private List<String> replaceAllProps(List<String> items) {
		return items.stream()
					.map(item -> PropertiesValueResolver.replaceProperties(item, getContextProperties()))
					.collect(Collectors.toList());
	}

	private List<MavenRepo> allToMavenRepo(List<String> repos) {
		return repos.stream().map(DependencyUtil::toMavenRepo).collect(Collectors.toList());

	}

	public List<String> getAutoDetectedModuleArguments(Code code, String requestedVersion) {
		if (mcp == null) {
			resolveClassPath(code);
		}
		return mcp.getAutoDectectedModuleArguments(requestedVersion);
	}

	public Project forResource(String resource) {
		ResourceRef resourceRef = resolveChecked(getResourceResolver(), resource);
		return forResourceRef(resourceRef);
	}

	private static ResourceRef resolveChecked(ResourceResolver resolver, String resource) {
		ResourceRef ref = resolver.resolve(resource);
		// Support URLs as script files
		// just proceed if the script file is a regular file at this point
		if (ref == null || !Files.isReadable(ref.getFile())) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Script or alias could not be found or read: '" + resource + "'");
		}
		return ref;
	}

	public Project forFile(Path resourceFile) {
		ResourceRef resourceRef = ResourceRef.forFile(resourceFile);
		return forResourceRef(resourceRef);
	}

	public Project forResourceRef(ResourceRef resourceRef) {
		Project prj;
		if (resourceRef.getFile().getFileName().toString().endsWith(".jar")) {
			prj = updateProject(Jar.prepareJar(resourceRef).asProject());
		} else if (resourceRef.getFile().getFileName().toString().equals(Project.BuildFile.jbang.fileName)) {
			// This is a bit of a hack, but what we do here is treat "build.jbang"
			// as if it were a source file, which we can do because it's syntax is
			// the same, just that it can only contain //-lines but no code.
			prj = updateProject(createSource(resourceRef).createProject(getResourceResolver()));
			// But once we get the resulting <code>Project</code> we remove the
			// first file from the sources to prevent "build.jbang" from being
			// passed to the compiler.
			prj.getMainSourceSet().getSources().remove(0);
			prj.setMainSource(createSource(prj.getMainSourceSet().getSources().get(0)));
		} else {
			prj = updateProject(createSource(resourceRef).createProject(getResourceResolver()));
		}
		return prj;
	}

	public Source createSource(ResourceRef resourceRef) {
		return Source.forResourceRef(resourceRef, forceType,
				it -> PropertiesValueResolver.replaceProperties(it, getContextProperties()));

	}

	private Project updateProject(Project prj) {
		SourceSet ss = prj.getMainSourceSet();
		prj.addRepositories(allToMavenRepo(replaceAllProps(additionalRepos)));
		ss.addDependencies(replaceAllProps(additionalDeps));
		ss.addClassPaths(replaceAllProps(additionalClasspaths));
		updateAllSources(prj, replaceAllProps(additionalSources));
		ss.addResources(allToFileRef(replaceAllProps(additionalResources)));
		prj.setProperties(properties);
		if (mainClass != null) {
			prj.setMainClass(mainClass);
		}
		if (javaVersion != null) {
			prj.setJavaVersion(javaVersion);
		}
		prj.setNativeImage(nativeImage);
		return prj;
	}

	private void updateAllSources(Project prj, List<String> sources) {
		Catalog catalog = catalogFile != null ? Catalog.get(catalogFile.toPath()) : null;
		ResourceResolver resolver = getResourceResolver();
		Function<String, String> propsResolver = it -> PropertiesValueResolver.replaceProperties(it,
				getContextProperties());
		sources	.stream()
				.flatMap(f -> Util.explode(null, Util.getCwd(), f).stream())
				.map(s -> resolveChecked(resolver, s))
				.map(ref -> Source.forResourceRef(ref, forceType, propsResolver))
				.forEach(src -> src.updateProject(prj, resolver));
	}

	private List<RefTarget> allToFileRef(List<String> resources) {
		ResourceResolver resolver = ResourceResolver.forResources();
		Function<String, String> propsResolver = it -> PropertiesValueResolver.replaceProperties(it,
				getContextProperties());
		return resources.stream()
						.flatMap(f -> Source.explodeFileRef(null, Util.getCwd(), f).stream())
						.map(f -> Source.toFileRef(f, resolver))
						.collect(Collectors.toList());
	}

	private ResourceResolver getResourceResolver() {
		Catalog cat = catalogFile != null ? Catalog.get(catalogFile.toPath()) : null;
		return new AliasResourceResolver(cat, this::getAliasResourceResolver);
	}

	private ResourceResolver getAliasResourceResolver(Alias alias) {
		if (alias != null) {
			updateFromAlias(alias);
		}
		return new CombinedResourceResolver(
				new RenamingScriptResourceResolver(),
				new LiteralScriptResourceResolver(),
				new RemoteResourceResolver(false),
				new ClasspathResourceResolver(),
				new GavResourceResolver(this::resolveDependency),
				new FileResourceResolver());
	}

	private ModularClassPath resolveDependency(String dep) {
		DependencyResolver resolver = new DependencyResolver().addDependency(dep);
		updateDependencyResolver(resolver);
		return resolver.resolve();
	}

	private void updateFromAlias(Alias alias) {
		if (arguments.isEmpty()) {
			setArguments(alias.arguments);
		}
		if (javaOptions.isEmpty()) {
			setJavaOptions(alias.javaOptions);
		}
		if (additionalSources.isEmpty()) {
			setAdditionalSources(alias.sources);
		}
		if (additionalResources.isEmpty()) {
			setAdditionalResources(alias.resources);
		}
		if (additionalDeps.isEmpty()) {
			setAdditionalDependencies(alias.dependencies);
		}
		if (additionalRepos.isEmpty()) {
			setAdditionalRepositories(alias.repositories);
		}
		if (additionalClasspaths.isEmpty()) {
			setAdditionalClasspaths(alias.classpaths);
		}
		if (properties.isEmpty()) {
			setProperties(alias.properties);
		}
		if (javaVersion == null) {
			setJavaVersion(alias.javaVersion);
		}
		if (mainClass == null) {
			setMainClass(alias.mainClass);
		}
		setAlias(true);
	}
}
