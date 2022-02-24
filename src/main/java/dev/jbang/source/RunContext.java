package dev.jbang.source;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.*;
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
	private List<MavenRepo> repositories;
	private List<String> dependencies;
	private List<RefTarget> filerefs;
	private List<ScriptSource> sources;
	private List<KeyValue> agentOptions;

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

	public List<ScriptSource> getAllSources(ScriptSource src) {
		List<ScriptSource> ssrcs = collectAllSources(src);
		List<ScriptSource> asrcs = getAdditionalSources()	.stream()
															.map(src::getSibling)
															.collect(Collectors.toList());
		if (asrcs.isEmpty()) {
			return ssrcs;
		} else if (ssrcs.isEmpty()) {
			return asrcs;
		} else {
			ArrayList<ScriptSource> result = new ArrayList<>();
			result.addAll(ssrcs);
			result.addAll(asrcs);
			return result;
		}
	}

	private List<ScriptSource> collectAllSources(ScriptSource src) {
		if (sources == null) {
			List<ScriptSource> scripts = new ArrayList<>();
			HashSet<ResourceRef> refs = new HashSet<>();
			// We should only return sources but we must avoid circular references via this
			// script, so we add this script's ref but not the script itself
			refs.add(src.getResourceRef());
			collectAllSources(src, refs, scripts);
			sources = scripts;
		}
		return sources;
	}

	private void collectAllSources(ScriptSource src, Set<ResourceRef> refs, List<ScriptSource> scripts) {
		List<ScriptSource> srcs = src.collectSources();
		for (ScriptSource s : srcs) {
			if (!refs.contains(s.getResourceRef())) {
				refs.add(s.getResourceRef());
				scripts.add(s);
				collectAllSources(s, refs, scripts);
			}
		}
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

	public List<RefTarget> getAllFiles(ScriptSource src) {
		if (filerefs == null) {
			filerefs = collectAll(src, ScriptSource::collectFiles);
		}
		return filerefs;
	}

	public void copyFilesTo(ScriptSource src, Path dest) {
		List<RefTarget> files = getAllFiles(src);
		for (RefTarget file : files) {
			file.copy(dest);
		}
	}

	public List<String> getAllDependencies(ScriptSource src) {
		if (dependencies == null) {
			dependencies = collectAll(src, ScriptSource::collectDependencies);
		}
		return dependencies;
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

	public List<MavenRepo> getAllRepositories(ScriptSource src) {
		if (repositories == null) {
			repositories = collectAll(src, ScriptSource::collectRepositories);
		}
		return repositories;
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

	public String getMainClassOr(Source src) {
		return (mainClass != null) ? mainClass : src.getMainClass();
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

	public List<String> getRuntimeOptionsMerged(Source src) {
		List<String> opts = new ArrayList<>(src.getRuntimeOptions());
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

	public List<KeyValue> getAllAgentOptions(ScriptSource src) {
		if (agentOptions == null) {
			agentOptions = collectAll(src, ScriptSource::collectAgentOptions);
		}
		return agentOptions;
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
		final public Source source;
		final public RunContext context;

		private AgentSourceContext(Source source, RunContext context) {
			this.source = source;
			this.context = context;
		}
	}

	public List<AgentSourceContext> getJavaAgents() {
		return javaAgents != null ? javaAgents : Collections.emptyList();
	}

	public void addJavaAgent(Source src, RunContext ctx) {
		if (javaAgents == null) {
			javaAgents = new ArrayList<>();
		}
		javaAgents.add(new AgentSourceContext(src, ctx));
	}

	/**
	 * Return resolved classpath lazily. resolution will only happen once, any
	 * consecutive calls return the same classpath.
	 *
	 * Properties available will be used for property replacement.
	 **/
	public String resolveClassPath(Source src) {
		if (mcp == null) {
			DependencyResolver resolver = new DependencyResolver();
			updateDependencyResolver(resolver);
			src.updateDependencyResolver(resolver);
			if (src instanceof ScriptSource) {
				getAllSources((ScriptSource) src).stream().forEach(s -> s.updateDependencyResolver(resolver));
			}
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

	public List<String> getAutoDetectedModuleArguments(Source src, String requestedVersion) {
		if (mcp == null) {
			resolveClassPath(src);
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
	public Source importJarMetadataFor(Source src) {
		JarSource jar = src.asJarSource();
		if (jar != null && jar.isUpToDate()) {
			setBuildJdk(JavaUtil.javaVersion(jar.getJavaVersion()));
			return jar;
		} else {
			return src;
		}
	}

	public Source forResource(String resource) {
		ResourceResolver resolver = ResourceResolver.forScripts(this::resolveDependency);
		ResourceRef resourceRef = resolver.resolve(resource);

		Alias alias;
		if (resourceRef == null) {
			// Not found as such, so let's check the aliases
			if (getCatalog() == null) {
				alias = Alias.get(resource);
			} else {
				Catalog cat = Catalog.get(getCatalog().toPath());
				alias = Alias.get(cat, resource);
			}
			if (alias != null) {
				resourceRef = resolver.resolve(alias.resolve());
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
				if (resourceRef == null) {
					throw new IllegalArgumentException(
							"Alias " + resource + " from " + alias.catalog.catalogRef + " failed to resolve "
									+ alias.scriptRef);
				}
			}
		}

		// Support URLs as script files
		// just proceed if the script file is a regular file at this point
		if (resourceRef == null || !resourceRef.getFile().canRead()) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Script or alias could not be found or read: '" + resource + "'");
		}

		// note script file must be not null at this point
		setOriginalRef(resource);
		return forResourceRef(resourceRef);
	}

	public Source forResourceRef(ResourceRef resourceRef) {
		Source src;
		if (resourceRef.getFile().getName().endsWith(".jar")) {
			src = JarSource.prepareJar(resourceRef);
		} else {
			src = ScriptSource.prepareScript(resourceRef,
					it -> PropertiesValueResolver.replaceProperties(it, getContextProperties()));
		}
		return src;
	}

	public Source forFile(File resourceFile) {
		ResourceRef resourceRef = ResourceRef.forFile(resourceFile);
		return forResourceRef(resourceRef);
	}

	private ModularClassPath resolveDependency(String dep) {
		DependencyResolver resolver = new DependencyResolver().addDependency(dep);
		updateDependencyResolver(resolver);
		return resolver.resolve();
	}

	protected <R> List<R> collectAll(ScriptSource src, Function<ScriptSource, List<R>> func) {
		Stream<R> subs = getAllSources(src).stream().flatMap(s -> func.apply(s).stream());
		return Stream.concat(func.apply(src).stream(), subs).collect(Collectors.toList());
	}

}
