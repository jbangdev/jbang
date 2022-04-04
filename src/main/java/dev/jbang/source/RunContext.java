package dev.jbang.source;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.*;
import dev.jbang.source.resolvers.AliasResourceResolver;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.PropertiesValueResolver;

/**
 * This class contains all the extra information needed to actually run a
 * Source. These are either options given by the user on the command line or
 * things that are part of the user's environment. It's all the dynamic parts of
 * the execution where the Source is immutable. The RunContext and the Source
 * together determine what finally gets executed and how.
 */
public class RunContext {
	private List<String> additionalSources = Collections.emptyList();
	private List<String> additionalDeps = Collections.emptyList();
	private List<String> additionalRepos = Collections.emptyList();
	private List<String> additionalClasspaths = Collections.emptyList();
	private Map<String, String> properties;
	private boolean forceJsh = false; // if true, interpret any input as for jshell
	private String originalRef;
	private String mainClass;
	private int buildJdk;
	/**
	 * if this script is used as an agent, agentOption is the option needed to pass
	 * in
	 **/
	private String javaAgentOption;
	private List<AgentSourceContext> javaAgents;
	private String preMainClass;
	private List<String> integrationOptions;
	private String agentMainClass;
	private File catalogFile;

	private Alias alias;

	private ModularClassPath mcp;
	private boolean nativeImage;
	private String javaVersion;
	private Properties contextProperties;

	private List<String> arguments;
	private List<String> javaOptions;
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
		return (arguments != null) ? arguments : Collections.emptyList();
	}

	public void setArguments(List<String> arguments) {
		this.arguments = arguments;
	}

	public Map<String, String> getProperties() {
		return (properties != null) ? properties : Collections.emptyMap();
	}

	public void setProperties(Map<String, String> properties) {
		this.properties = properties;
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

	public List<String> getAdditionalSources() {
		return additionalSources;
	}

	public void setAdditionalSources(List<String> sources) {
		if (sources != null) {
			this.additionalSources = new ArrayList<>(sources);
		} else {
			this.additionalSources = Collections.emptyList();
		}
	}

	public List<String> getAdditionalDependencies() {
		return additionalDeps;
	}

	public void setAdditionalDependencies(List<String> deps) {
		if (deps != null) {
			this.additionalDeps = new ArrayList<>(deps);
		} else {
			this.additionalDeps = Collections.emptyList();
		}
	}

	public List<String> getAdditionalRepositories() {
		return additionalRepos;
	}

	public void setAdditionalRepositories(List<String> repos) {
		if (repos != null) {
			this.additionalRepos = repos;
		} else {
			this.additionalRepos = Collections.emptyList();
		}
	}

	public List<String> getAdditionalClasspaths() {
		return additionalClasspaths;
	}

	public void setAdditionalClasspaths(List<String> cps) {
		if (cps != null) {
			this.additionalClasspaths = new ArrayList<>(cps);
		} else {
			this.additionalClasspaths = Collections.emptyList();
		}
	}

	/**
	 * Returns the Alias object if originalRef is an alias, otherwise null
	 */
	public Alias getAlias() {
		return alias;
	}

	/**
	 * Sets the Alias object if originalRef is an alias
	 */
	public void setAlias(Alias alias) {
		this.alias = alias;
	}

	public boolean isForceJsh() {
		return forceJsh;
	}

	public void setForceJsh(boolean forcejsh) {
		this.forceJsh = forcejsh;
	}

	/**
	 * The original script reference. Might ba a URL or an alias.
	 */
	public String getOriginalRef() {
		return originalRef;
	}

	public void setOriginalRef(String ref) {
		this.originalRef = ref;
	}

	public String getMainClass() {
		return mainClass;
	}

	public String getMainClassOr(Code code) {
		return (mainClass != null) ? mainClass : code.getMainClass();
	}

	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	public List<String> getJavaOptions() {
		return (javaOptions != null) ? javaOptions : Collections.emptyList();
	}

	public void setJavaOptions(List<String> javaOptions) {
		this.javaOptions = javaOptions;
	}

	public List<String> getIntegrationOptions() {
		return (integrationOptions != null) ? integrationOptions : Collections.emptyList();
	}

	public void setIntegrationOptions(List<String> integrationOptions) {
		this.integrationOptions = integrationOptions;
	}

	public List<String> getRuntimeOptionsMerged(Code code) {
		List<String> opts = new ArrayList<>(code.getRuntimeOptions());
		opts.addAll(getJavaOptions());
		opts.addAll(getIntegrationOptions());
		return opts;
	}

	public int getBuildJdk() {
		return buildJdk;
	}

	public void setBuildJdk(int javaVersion) {
		this.buildJdk = javaVersion;
	}

	public String getJavaAgentOption() {
		return javaAgentOption;
	}

	public void setJavaAgentOption(String option) {
		this.javaAgentOption = option;
	}

	public String getAgentMainClass() {
		return agentMainClass;
	}

	public void setAgentMainClass(String b) {
		agentMainClass = b;
	}

	public String getPreMainClass() {
		return preMainClass;
	}

	public void setPreMainClass(String name) {
		preMainClass = name;
	}

	public boolean isNativeImage() {
		return nativeImage;
	}

	public void setNativeImage(boolean nativeImage) {
		this.nativeImage = nativeImage;
	}

	public void setJavaVersion(String javaVersion) {
		this.javaVersion = javaVersion;
	}

	public String getJavaVersion() {
		return javaVersion;
	}

	public String getJavaVersionOr(Code code) {
		return javaVersion != null ? javaVersion : code.getJavaVersion().orElse(null);
	}

	public File getCatalog() {
		return catalogFile;
	}

	public void setCatalog(File catalogFile) {
		this.catalogFile = catalogFile;
	}

	public Properties getContextProperties() {
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
	 * consecutive calls return the same classpath.
	 *
	 * Properties available will be used for property replacement.
	 **/
	public String resolveClassPath(Code code) {
		if (mcp == null) {
			DependencyResolver resolver = new DependencyResolver();
			if (code instanceof Jar) {
				updateDependencyResolver(resolver);
			}
			code.asSourceSet().updateDependencyResolver(resolver);
			mcp = resolver.resolve();
		}
		return mcp.getClassPath();
	}

	private DependencyResolver updateDependencyResolver(DependencyResolver resolver) {
		return resolver
						.addRepositories(allToMavenRepo(replaceAllProps(
								getAdditionalRepositories())))
						.addDependencies(replaceAllProps(
								getAdditionalDependencies()))
						.addClassPaths(
								replaceAllProps(getAdditionalClasspaths()));
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

	public ModularClassPath getClassPath() {
		if (mcp == null) {
			throw new ExitException(BaseCommand.EXIT_INTERNAL_ERROR, "Classpath must be resolved first");
		}
		return mcp;
	}

	/**
	 * If the given source is a JarSource its metadata will be copied to this
	 * RunContext and the JarSource will be returned. In any other case the given
	 * source will be returned;
	 */
	public Code importJarMetadataFor(Code code) {
		Jar jar = code.asJar();
		if (jar != null && jar.isUpToDate()) {
			setBuildJdk(JavaUtil.javaVersion(jar.getJavaVersion().orElse(null)));
			return jar;
		} else {
			return code;
		}
	}

	private List<Source> allToScriptSource(List<String> sources) {
		Catalog catalog = getCatalog() != null ? Catalog.get(getCatalog().toPath()) : null;
		ResourceResolver resolver = ResourceResolver.forScripts(this::resolveDependency, catalog);
		Function<String, String> propsResolver = it -> PropertiesValueResolver.replaceProperties(it,
				getContextProperties());
		return sources	.stream()
						.map(s -> resolveChecked(resolver, s))
						.map(ref -> Source.forResourceRef(ref, propsResolver))
						.collect(Collectors.toList());
	}

	private ModularClassPath resolveDependency(String dep) {
		DependencyResolver resolver = new DependencyResolver().addDependency(dep);
		updateDependencyResolver(resolver);
		return resolver.resolve();
	}

	public Code forResource(String resource) {
		ResourceRef resourceRef = resolveChecked(getResourceResolver(), resource);

		if (resourceRef instanceof AliasResourceResolver.AliasedResourceRef) {
			// The resource we found was obtained from an alias which might
			// contain extra options that need to be taken into account
			// when running the code
			Alias alias = ((AliasResourceResolver.AliasedResourceRef) resourceRef).getAlias();
			if (getArguments() == null || getArguments().isEmpty()) {
				setArguments(alias.arguments);
			}
			if (getJavaOptions() == null || getJavaOptions().isEmpty()) {
				setJavaOptions(alias.javaOptions);
			}
			if (getAdditionalSources() == null || getAdditionalSources().isEmpty()) {
				setAdditionalSources(alias.sources);
			}
			if (getAdditionalDependencies() == null || getAdditionalDependencies().isEmpty()) {
				setAdditionalDependencies(alias.dependencies);
			}
			if (getAdditionalRepositories() == null || getAdditionalRepositories().isEmpty()) {
				setAdditionalRepositories(alias.repositories);
			}
			if (getAdditionalClasspaths() == null || getAdditionalClasspaths().isEmpty()) {
				setAdditionalClasspaths(alias.classpaths);
			}
			if (getProperties() == null || getProperties().isEmpty()) {
				setProperties(alias.properties);
			}
			if (getJavaVersion() == null) {
				setJavaVersion(alias.javaVersion);
			}
			if (getMainClass() == null) {
				setMainClass(alias.mainClass);
			}
			setAlias(alias);
		}

		// note script file must be not null at this point
		setOriginalRef(resource);
		return forResourceRef(resourceRef);
	}

	private static ResourceRef resolveChecked(ResourceResolver resolver, String resource) {
		ResourceRef ref = resolver.resolve(resource);
		// Support URLs as script files
		// just proceed if the script file is a regular file at this point
		if (ref == null || !ref.getFile().canRead()) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Script or alias could not be found or read: '" + resource + "'");
		}
		return ref;
	}

	public Code forFile(File resourceFile) {
		ResourceRef resourceRef = ResourceRef.forFile(resourceFile);
		return forResourceRef(resourceRef);
	}

	public Code forResourceRef(ResourceRef resourceRef) {
		Code code;
		if (resourceRef.getFile().getName().endsWith(".jar")) {
			code = Jar.prepareJar(resourceRef);
		} else {
			code = createSourceSet(createSource(resourceRef));
		}
		return code;
	}

	private Source createSource(ResourceRef resourceRef) {
		return Source.forResourceRef(resourceRef,
				it -> PropertiesValueResolver.replaceProperties(it, getContextProperties()));

	}

	private SourceSet createSourceSet(Source src) {
		SourceSet ss = SourceSet.forSource(src, getResourceResolver());
		ss.addRepositories(allToMavenRepo(replaceAllProps(getAdditionalRepositories())));
		ss.addDependencies(replaceAllProps(getAdditionalDependencies()));
		ss.addClassPaths(replaceAllProps(getAdditionalClasspaths()));
		ss.addRuntimeOptions(getJavaOptions());
		ss.addRuntimeOptions(getIntegrationOptions());
		ss.addSources(allToScriptSource(replaceAllProps(getAdditionalSources())));
		if (javaVersion != null) {
			ss.setJavaVersion(javaVersion);
		}
		return ss;
	}

	private ResourceResolver getResourceResolver() {
		Catalog catalog = getCatalog() != null ? Catalog.get(getCatalog().toPath()) : null;
		return ResourceResolver.forScripts(this::resolveDependency, catalog);
	}

}
