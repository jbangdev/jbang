package dev.jbang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RunContext {
	final private List<String> arguments;
	final private Map<String, String> properties;

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
	private List<DecoratedSource> javaAgents;
	private String preMainClass;
	private String agentMainClass;

	private AliasUtil.Alias alias;

	private ModularClassPath classpath;

	public RunContext(List<String> arguments, Map<String, String> properties) {
		this.arguments = arguments;
		this.properties = properties;
	}

	public List<String> getArguments() {
		return (arguments != null) ? arguments : Collections.emptyList();
	}

	public Map<String, String> getProperties() {
		return (properties != null) ? properties : Collections.emptyMap();
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

	public List<DecoratedSource> getJavaAgents() {
		return javaAgents != null ? javaAgents : Collections.emptyList();
	}

	public void addJavaAgent(DecoratedSource agent) {
		if (javaAgents == null) {
			javaAgents = new ArrayList<>();
		}
		javaAgents.add(agent);
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
			cp.append(Settings.CP_SEPARATOR).append(addcp);
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

}
