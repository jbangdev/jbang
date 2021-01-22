package dev.jbang;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class wraps a reference to an runnable/executable resource in the form
 * of a Source. It also holds all additional information necessary to be able to
 * actually run/execute that resource. This is information that can not be
 * induced or extracted from the resource itself but is information that is
 * provided by the user or by the environment at runtime.
 *
 * This class also implements Source, passing all calls directly to the wrapped
 * Source object. This makes it easier to use this class in places where it's
 * not really important to know with what type or Source we're dealing.
 */
public class DecoratedSource implements Source {
	final private Source source;
	final private List<String> arguments;
	final private Map<String, String> properties;

	private List<String> additionalDeps = Collections.emptyList();
	private List<String> additionalClasspaths = Collections.emptyList();
	private boolean forcejsh = false; // if true, interpret any input as for jshell
	private String originalRef;
	private String mainClass;
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

	protected DecoratedSource(Source source, List<String> arguments, Map<String, String> properties) {
		this.source = source;
		this.arguments = arguments;
		this.properties = properties;
	}

	public ScriptSource script() {
		return (ScriptSource) source;
	}

	public JarSource jar() {
		return (JarSource) source;
	}

	public List<String> getArguments() {
		return (arguments != null) ? arguments : Collections.emptyList();
	}

	public Map<String, String> getProperties() {
		return (properties != null) ? properties : Collections.emptyMap();
	}

	public void setAdditionalDependencies(List<String> deps) {
		if (deps != null) {
			this.additionalDeps = new ArrayList<>(deps);
		} else {
			this.additionalDeps = Collections.emptyList();
		}
	}

	public void setAdditionalClasspaths(List<String> cps) {
		if (cps != null) {
			this.additionalClasspaths = new ArrayList<>(cps);
		} else {
			this.additionalClasspaths = Collections.emptyList();
		}
	}

	/**
	 * Sets the Alias object if originalRef is an alias
	 */
	public void setAlias(AliasUtil.Alias alias) {
		this.alias = alias;
	}

	/**
	 * Returns the Alias object if originalRef is an alias, otherwise null
	 */
	public AliasUtil.Alias getAlias() {
		return alias;
	}

	public boolean forJar() {
		return Source.forJar(getResourceRef().getFile());
	}

	public boolean forJShell() {
		return forcejsh || Source.forJShell(getResourceRef().getFile());
	}

	public void setForcejsh(boolean forcejsh) {
		this.forcejsh = forcejsh;
	}

	public ModularClassPath getClassPath() {
		return classpath;
	}

	public boolean needsJar() {
		// anything but .jar and .jsh files needs jar
		return !(forJar() || forJShell());
	}

	public ResourceRef getResourceRef() {
		return source.getResourceRef();
	}

	public Optional<String> getDescription() {
		return source.getDescription();
	}

	public File getJar() {
		return source.getJar();
	}

	public String javaVersion() {
		return source.javaVersion();
	}

	public void setOriginalRef(String ref) {
		this.originalRef = ref;
	}

	/**
	 * The original script reference. Might ba a URL or an alias.
	 */
	public String getOriginalRef() {
		return originalRef;
	}

	public String getMainClass() {
		return mainClass;
	}

	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
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

	public List<String> collectAllDependencies() {
		Properties p = new Properties(System.getProperties());
		if (properties != null) {
			p.putAll(properties);
		}
		return getAllDependencies(p);
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

	public List<String> getAllDependencies(Properties props) {
		return Stream	.concat(additionalDeps.stream(), source.getAllDependencies(props).stream())
						.collect(Collectors.toList());
	}

	public ModularClassPath resolveClassPath(List<String> dependencies, boolean offline) {
		return source.resolveClassPath(dependencies, offline);
	}

	/**
	 * Return resolved classpath lazily. resolution will only happen once, any
	 * consecutive calls return the same classpath.
	 **/
	public String resolveClassPath(boolean offline) {
		if (classpath == null) {
			classpath = resolveClassPath(collectAllDependencies(), offline);
			if (source instanceof JarSource) {
				// fetch main class as we can't use -jar to run as it ignores classpath.
				if (getMainClass() == null) {
					try (JarFile jf = new JarFile(getResourceRef().getFile())) {
						setMainClass(
								jf.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
					} catch (IOException e) {
						Util.warnMsg("Problem reading manifest from " + getResourceRef().getFile());
					}
				}
			}
		}
		StringBuilder cp = new StringBuilder(classpath.getClassPath());
		for (String addcp : additionalClasspaths) {
			cp.append(Settings.CP_SEPARATOR + addcp);
		}
		if (source.getJar() != null && !forcejsh) {
			return getJar().getAbsolutePath() + Settings.CP_SEPARATOR + cp.toString();
		}
		return cp.toString();
	}

	public List<String> getAutoDetectedModuleArguments(String requestedVersion, boolean offline) {
		if (classpath == null) {
			resolveClassPath(offline);
		}
		return classpath.getAutoDectectedModuleArguments(requestedVersion);
	}

	public Source getSource() {
		return source;
	}
}
