package dev.jbang;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.dependencies.ModularClassPath;

/**
 * This class contains all the extra information needed to actually run a
 * Source. These are either options given by the user on the command line or
 * things that are part of the user's environment. It's all the dynamic parts of
 * the execution where the Source is immutable. The RunContext and the Source
 * together determine what finally gets executed and how.
 */
public class RunContext {
	private List<String> arguments;
	private Map<String, String> properties;

	private List<String> additionalDeps = Collections.emptyList();
	private List<String> additionalClasspaths = Collections.emptyList();
	private boolean forceJsh = false; // if true, interpret any input as for jshell
	private String originalRef;
	private String mainClass;
	private List<String> javaRuntimeOptions;
	private List<String> persistentJvmArgs;
	private int buildJdk;
	/**
	 * if this script is used as an agent, agentOption is the option needed to pass
	 * in
	 **/
	private String javaAgentOption;
	private List<AgentSourceContext> javaAgents;
	private String preMainClass;
	private String agentMainClass;

	private AliasUtil.Alias alias;

	private ModularClassPath classpath;

	public static RunContext empty() {
		return new RunContext();
	}

	public static RunContext create(List<String> arguments, Map<String, String> properties) {
		return new RunContext(arguments, properties);
	}

	public static RunContext create(List<String> arguments, Map<String, String> properties, List<String> dependencies,
			List<String> classpaths, boolean forceJsh) {
		RunContext ctx = new RunContext(arguments, properties);
		ctx.setAdditionalDependencies(dependencies);
		ctx.setAdditionalClasspaths(classpaths);
		ctx.setForceJsh(forceJsh);
		return ctx;
	}

	private RunContext() {
		this(null, null);
	}

	private RunContext(List<String> arguments, Map<String, String> properties) {
		this.arguments = arguments;
		this.properties = properties;
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
	public AliasUtil.Alias getAlias() {
		return alias;
	}

	/**
	 * Sets the Alias object if originalRef is an alias
	 */
	public void setAlias(AliasUtil.Alias alias) {
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

	public List<String> getRuntimeOptions() {
		return javaRuntimeOptions;
	}

	public List<String> getRuntimeOptionsOr(Source src) {
		return (javaRuntimeOptions != null) ? javaRuntimeOptions : src.getRuntimeOptions();
	}

	public void setRuntimeOptions(List<String> javaRuntimeOptions) {
		this.javaRuntimeOptions = javaRuntimeOptions;
	}

	public List<String> getPersistentJvmArgs() {
		return persistentJvmArgs;
	}

	public void setPersistentJvmArgs(List<String> persistentJvmArgs) {
		this.persistentJvmArgs = persistentJvmArgs;
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

	public List<String> collectAllDependenciesFor(Source src) {
		Properties p = new Properties(System.getProperties());
		if (getProperties() != null) {
			p.putAll(getProperties());
		}
		return getAllDependencies(src, p);
	}

	private List<String> getAllDependencies(Source src, Properties props) {
		return Stream	.concat(getAdditionalDependencies().stream(), src.getAllDependencies(props).stream())
						.collect(Collectors.toList());
	}

	/**
	 * Return resolved classpath lazily. resolution will only happen once, any
	 * consecutive calls return the same classpath.
	 **/
	public String resolveClassPath(Source src, boolean offline) {
		if (classpath == null) {
			classpath = src.resolveClassPath(collectAllDependenciesFor(src), offline);
		}
		StringBuilder cp = new StringBuilder(classpath.getClassPath());
		for (String addcp : getAdditionalClasspaths()) {
			if (cp.length() > 0) {
				cp.append(Settings.CP_SEPARATOR);
			}
			cp.append(addcp);
		}
		return cp.toString();
	}

	public List<String> getAutoDetectedModuleArguments(Source src, String requestedVersion, boolean offline) {
		if (classpath == null) {
			resolveClassPath(src, offline);
		}
		return classpath.getAutoDectectedModuleArguments(requestedVersion);
	}

	public ModularClassPath getClassPath() {
		return classpath;
	}

	/**
	 * If the given source is a JarSource its metadata will be copied to this
	 * RunContext and the JarSource will be returned. In any other case the given
	 * source will be returned;
	 * 
	 * @return
	 */
	public Source importJarMetadataFor(Source src) {
		File jarFile = src.getJarFile();
		if (jarFile.exists()) {
			JarSource jar = JarSource.prepareJar(
					ResourceRef.forNamedFile(src.getResourceRef().getOriginalResource(), jarFile));
			setMainClass(jar.getMainClass());
			setPersistentJvmArgs(jar.getRuntimeOptions());
			setBuildJdk(jar.getBuildJdk());
			return jar;
		} else {
			return src;
		}
	}

}
