package dev.jbang;

import java.io.IOException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.jbang.cli.BaseCommand;

public class ExtendedScript extends Script {
	private List<String> arguments;
	private Map<String, String> properties;
	private List<String> additionalDeps = Collections.emptyList();
	private List<String> additionalClasspaths = Collections.emptyList();
	private boolean forcejsh = false; // if true, interpret any input as for jshell

	private ModularClassPath classpath;

	public ExtendedScript(ScriptResource resource, List<String> arguments, Map<String, String> properties) {
		super(resource);
		this.arguments = arguments;
		this.properties = properties;
	}

	public ExtendedScript(String script, List<String> arguments, Map<String, String> properties) {
		this(ScriptResource.forFile(null), script, arguments, properties);
	}

	public ExtendedScript(ScriptResource resource, String content, List<String> arguments,
			Map<String, String> properties) {
		super(resource, content);
		this.arguments = arguments;
		this.properties = properties;
	}

	public List<String> getArguments() {
		return (arguments != null) ? arguments : Collections.emptyList();
	}

	public Map<String, String> getProperties() {
		return (properties != null) ? properties : Collections.emptyMap();
	}

	public Script setAdditionalDependencies(List<String> deps) {
		if (deps != null) {
			this.additionalDeps = new ArrayList<>(deps);
		} else {
			this.additionalDeps = Collections.emptyList();
		}
		return this;
	}

	public Script setAdditionalClasspaths(List<String> cps) {
		if (cps != null) {
			this.additionalClasspaths = new ArrayList<>(cps);
		} else {
			this.additionalClasspaths = Collections.emptyList();
		}
		return this;
	}

	public boolean forJShell() {
		return forcejsh || super.forJShell();
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

	public List<String> collectAllDependencies() {
		if (forJar()) { // if a .jar then we don't try parse it for dependencies.
			return additionalDeps;
		}

		Properties p = new Properties(System.getProperties());
		if (properties != null) {
			p.putAll(properties);
		}

		return Stream	.concat(additionalDeps.stream(), super.collectAllDependencies(p).stream())
						.collect(Collectors.toList());
	}

	/**
	 * Return resolved classpath lazily. resolution will only happen once, any
	 * consecutive calls return the same classpath.
	 **/
	public String resolveClassPath(boolean offline) {
		if (classpath == null) {
			if (forJar()) {
				if (DependencyUtil.looksLikeAGav(scriptResource.getOriginalResource())) {
					List<String> dependencies = new ArrayList<>(additionalDeps);
					dependencies.add(scriptResource.getOriginalResource());
					classpath = new DependencyUtil().resolveDependencies(dependencies,
							Collections.emptyList(), offline, !Util.isQuiet());
				} else if (!additionalDeps.isEmpty()) {
					classpath = new DependencyUtil().resolveDependencies(additionalDeps,
							Collections.emptyList(), offline, !Util.isQuiet());
				} else {
					if (getBackingFile() == null) {
						classpath = new ModularClassPath(Arrays.asList(new ArtifactInfo(null, getOriginalFile())));
					} else {
						classpath = new ModularClassPath(Arrays.asList(new ArtifactInfo(null, getBackingFile())));
					}
				}
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
				List<String> dependencies = collectAllDependencies();
				List<MavenRepo> repositories = collectAllRepositories();
				classpath = new DependencyUtil().resolveDependencies(dependencies, repositories, offline,
						!Util.isQuiet());
			}
		}
		StringBuilder cp = new StringBuilder(classpath.getClassPath());
		for (String addcp : additionalClasspaths) {
			cp.append(Settings.CP_SEPARATOR + addcp);
		}
		if (getJar() != null) {
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

	public static ExtendedScript prepareScript(String scriptResource) {
		return prepareScript(scriptResource, null, null, null, null, false, false);
	}

	public static ExtendedScript prepareScript(String scriptResource, List<String> arguments) {
		return prepareScript(scriptResource, arguments, null, null, null, false, false);
	}

	public static ExtendedScript prepareScript(String scriptResource, List<String> arguments,
			Map<String, String> properties,
			List<String> dependencies, List<String> classpaths, boolean fresh, boolean forcejsh) {
		ScriptResource scriptFile = ScriptResource.forResource(scriptResource);

		AliasUtil.Alias alias = null;
		if (scriptFile == null) {
			// Not found as such, so let's check the aliases
			alias = AliasUtil.getAlias(null, scriptResource, arguments, properties);
			if (alias != null) {
				scriptFile = ScriptResource.forResource(alias.resolve(null));
				arguments = alias.arguments;
				properties = alias.properties;
				if (scriptFile == null) {
					throw new IllegalArgumentException(
							"Alias " + scriptResource + " from " + alias.catalog.catalogFile + " failed to resolve "
									+ alias.scriptRef);
				}
			}
		}

		// Support URLs as script files
		// just proceed if the script file is a regular file at this point
		if (scriptFile == null || !scriptFile.getFile().canRead()) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT, "Could not read script argument " + scriptResource);
		}

		// note script file must be not null at this point

		ExtendedScript s = new ExtendedScript(scriptFile, arguments, properties);
		s.setForcejsh(forcejsh);
		s.setOriginal(scriptResource);
		s.setAlias(alias);
		s.setAdditionalDependencies(dependencies);
		s.setAdditionalClasspaths(classpaths);
		return s;
	}

}
