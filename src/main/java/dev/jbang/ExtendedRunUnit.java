package dev.jbang;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExtendedRunUnit implements RunUnit {
	final public RunUnit runUnit;
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
	private List<ExtendedRunUnit> javaAgents;
	private String preMainClass;
	private String agentMainClass;

	private AliasUtil.Alias alias;

	private ModularClassPath classpath;

	protected ExtendedRunUnit(RunUnit runUnit, List<String> arguments, Map<String, String> properties) {
		this.runUnit = runUnit;
		this.arguments = arguments;
		this.properties = properties;
	}

	public Script script() {
		return (Script) runUnit;
	}

	public Jar jar() {
		return (Jar) runUnit;
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
		return forJar(getBackingFile());
	}

	protected static boolean forJar(File backingFile) {
		return backingFile != null && backingFile.toString().endsWith(".jar");
	}

	public boolean forJShell() {
		return forcejsh || getBackingFile().getName().endsWith(".jsh");
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

	@Override
	public ScriptResource getScriptResource() {
		return runUnit.getScriptResource();
	}

	@Override
	public File getBackingFile() {
		return runUnit.getBackingFile();
	}

	@Override
	public File getJar() {
		return runUnit.getJar();
	}

	@Override
	public String javaVersion() {
		return runUnit.javaVersion();
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
		return collectAllDependencies(p);
	}

	public List<ExtendedRunUnit> getJavaAgents() {
		return javaAgents != null ? javaAgents : Collections.emptyList();
	}

	public void addJavaAgent(ExtendedRunUnit agent) {
		if (javaAgents == null) {
			javaAgents = new ArrayList<>();
		}
		javaAgents.add(agent);
	}

	@Override
	public List<String> collectAllDependencies(Properties props) {
		return Stream	.concat(additionalDeps.stream(), runUnit.collectAllDependencies(props).stream())
						.collect(Collectors.toList());
	}

	/**
	 * Return resolved classpath lazily. resolution will only happen once, any
	 * consecutive calls return the same classpath.
	 **/
	public String resolveClassPath(boolean offline) {
		if (classpath == null) {
			if (runUnit instanceof Jar) {
				Jar jar = (Jar) runUnit;
				classpath = jar.resolveClassPath(collectAllDependencies(), offline);
				// fetch main class as we can't use -jar to run as it ignores classpath.
				if (getMainClass() == null) {
					try (JarFile jf = new JarFile(getBackingFile())) {
						setMainClass(
								jf.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
					} catch (IOException e) {
						Util.warnMsg("Problem reading manifest from " + getBackingFile());
					}
				}
			} else {
				Script script = (Script) runUnit;
				classpath = script.resolveClassPath(collectAllDependencies(), offline);
			}
		}
		StringBuilder cp = new StringBuilder(classpath.getClassPath());
		for (String addcp : additionalClasspaths) {
			cp.append(Settings.CP_SEPARATOR + addcp);
		}
		if (runUnit instanceof Jar) {
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
}
