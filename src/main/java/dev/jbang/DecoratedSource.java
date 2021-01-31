package dev.jbang;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.cli.BaseCommand;

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

	private boolean forcejsh = false; // if true, interpret any input as for jshell
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
	private DependencyContext additionalDependencyContext = new DependencyContext();

	protected DecoratedSource(Source source, List<String> arguments, Map<String, String> properties) {
		this.source = source;
		this.arguments = arguments;
		this.properties = properties;
	}

	public Source getSource() {
		return source;
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

	@Override
	public ResourceRef getResourceRef() {
		return source.getResourceRef();
	}

	@Override
	public Optional<String> getDescription() {
		return source.getDescription();
	}

	@Override
	public File getJar() {
		return source.getJar();
	}

	@Override
	public boolean enableCDS() {
		return source.enableCDS();
	}

	@Override
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

	@Override
	public String getMainClass() {
		return (mainClass != null) ? mainClass : source.getMainClass();
	}

	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	@Override
	public List<String> getRuntimeOptions() {
		return (javaRuntimeOptions != null) ? javaRuntimeOptions : source.getRuntimeOptions();
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

	@Override
	public List<String> getAllDependencies(Properties props) {
		return Stream.concat(additionalDependencyContext.getDependencies().stream(),
				source.getAllDependencies(props).stream()).collect(Collectors.toList());
	}

	@Override
	public ModularClassPath resolveClassPath(DependencyContext additinal, boolean offline) {
		return source.resolveClassPath(additinal, offline);
	}

	/**
	 * Return resolved classpath lazily. resolution will only happen once, any
	 * consecutive calls return the same classpath.
	 **/
	public String resolveClassPath(boolean offline) {
		if (classpath == null) {
			classpath = resolveClassPath(
					new DependencyContext()	.setRepositories(additionalDependencyContext.getRepositories())
											.setDependencies(collectAllDependencies()),
					offline);
		}
		StringBuilder cp = new StringBuilder(classpath.getClassPath());
		for (String addcp : additionalDependencyContext.getClasspaths()) {
			cp.append(Settings.CP_SEPARATOR).append(addcp);
		}
		return cp.toString();
	}

	public List<String> getAutoDetectedModuleArguments(String requestedVersion, boolean offline) {
		if (classpath == null) {
			resolveClassPath(offline);
		}
		return classpath.getAutoDectectedModuleArguments(requestedVersion);
	}

	public static DecoratedSource forResource(String resource) {
		return forResource(resource, null, null, new DependencyContext(), false, false);
	}

	public static DecoratedSource forResource(String resource, List<String> arguments) {
		return forResource(resource, arguments, null, new DependencyContext(), false, false);
	}

	public static DecoratedSource forResource(String resource, List<String> arguments, Map<String, String> properties) {
		return forResource(resource, arguments, properties, new DependencyContext(), false, false);
	}

	public static DecoratedSource forResource(String resource, List<String> arguments, Map<String, String> properties,
			DependencyContext dependencyContext, boolean fresh, boolean forcejsh) {
		ResourceRef resourceRef = ResourceRef.forResource(resource);

		AliasUtil.Alias alias = null;
		if (resourceRef == null) {
			// Not found as such, so let's check the aliases
			alias = AliasUtil.getAlias(null, resource, arguments, properties);
			if (alias != null) {
				resourceRef = ResourceRef.forResource(alias.resolve(null));
				arguments = alias.arguments;
				properties = alias.properties;
				if (resourceRef == null) {
					throw new IllegalArgumentException("Alias " + resource + " from " + alias.catalog.catalogFile
							+ " failed to resolve " + alias.scriptRef);
				}
			}
		}

		// Support URLs as script files
		// just proceed if the script file is a regular file at this point
		if (resourceRef == null || !resourceRef.getFile().canRead()) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Could not read script argument " + resource);
		}

		// note script file must be not null at this point

		Source ru;
		if (resourceRef.getFile().getName().endsWith(".jar")) {
			ru = JarSource.prepareJar(resourceRef);
		} else {
			ru = ScriptSource.prepareScript(resourceRef);
		}

		DecoratedSource xrunit = new DecoratedSource(ru, arguments, properties);
		xrunit.setForcejsh(forcejsh);
		xrunit.setOriginalRef(resource);
		xrunit.setAlias(alias);
		xrunit.setDependencyContext(dependencyContext);
		return xrunit;
	}

	private void setDependencyContext(DependencyContext dependencyContext) {
		if (dependencyContext == null) {
			dependencyContext = new DependencyContext();
		} else {
			this.additionalDependencyContext = dependencyContext;
		}
	}

	public static DecoratedSource forScriptResource(ResourceRef resourceRef, List<String> arguments,
			Map<String, String> properties) {
		return forScriptResource(resourceRef, arguments, properties, null, false, false);
	}

	public static DecoratedSource forScriptResource(ResourceRef resourceRef, List<String> arguments,
			Map<String, String> properties, DependencyContext dependencyContext, boolean fresh, boolean forcejsh) {
		// note script file must be not null at this point
		Source ru;
		if (resourceRef.getFile().getName().endsWith(".jar")) {
			ru = JarSource.prepareJar(resourceRef);
		} else {
			ru = ScriptSource.prepareScript(resourceRef);
		}

		DecoratedSource xrunit = new DecoratedSource(ru, arguments, properties);
		xrunit.setForcejsh(forcejsh);
		xrunit.setDependencyContext(dependencyContext);
		return xrunit;
	}

	public static DecoratedSource forScript(String script, List<String> arguments, Map<String, String> properties) {
		return forScript(script, arguments, properties, null, false, false);
	}

	public static DecoratedSource forScript(String script, List<String> arguments, Map<String, String> properties,
			DependencyContext dependencyContext, boolean fresh, boolean forcejsh) {
		Source ru = new ScriptSource(script);
		DecoratedSource xrunit = new DecoratedSource(ru, arguments, properties);
		xrunit.setForcejsh(forcejsh);
		xrunit.setDependencyContext(dependencyContext);
		return xrunit;
	}
}
