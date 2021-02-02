package dev.jbang;

import java.io.File;
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
	final private RunContext context;

	private ModularClassPath classpath;

	protected DecoratedSource(Source source, RunContext context) {
		this.source = source;
		this.context = context;
	}

	public Source getSource() {
		return source;
	}

	public RunContext getContext() {
		return context;
	}

	public ScriptSource script() {
		return (ScriptSource) source;
	}

	public JarSource jar() {
		return (JarSource) source;
	}

	public boolean forJar() {
		return Source.forJar(getResourceRef().getFile());
	}

	public boolean forJShell() {
		return context.isForceJsh() || Source.forJShell(getResourceRef().getFile());
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

	@Override
	public String getMainClass() {
		return (context.getMainClass() != null) ? context.getMainClass() : source.getMainClass();
	}

	public void setMainClass(String mainClass) {
		context.setMainClass(mainClass);
	}

	@Override
	public List<String> getRuntimeOptions() {
		return (context.getRuntimeOptions() != null) ? context.getRuntimeOptions() : source.getRuntimeOptions();
	}

	public void setRuntimeOptions(List<String> javaRuntimeOptions) {
		context.setRuntimeOptions(javaRuntimeOptions);
	}

	public List<String> collectAllDependencies() {
		Properties p = new Properties(System.getProperties());
		if (context.getProperties() != null) {
			p.putAll(context.getProperties());
		}
		return getAllDependencies(p);
	}

	@Override
	public List<String> getAllDependencies(Properties props) {
		return Stream	.concat(context.getAdditionalDependencies().stream(), source.getAllDependencies(props).stream())
						.collect(Collectors.toList());
	}

	@Override
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
		}
		StringBuilder cp = new StringBuilder(classpath.getClassPath());
		for (String addcp : context.getAdditionalClasspaths()) {
			if (cp.length() > 0) {
				cp.append(Settings.CP_SEPARATOR);
			}
			cp.append(addcp);
		}
		return cp.toString();
	}

	public List<String> getAutoDetectedModuleArguments(String requestedVersion, boolean offline) {
		if (classpath == null) {
			resolveClassPath(offline);
		}
		return classpath.getAutoDectectedModuleArguments(requestedVersion);
	}

	public void importJarMetadata() {
		File outjar = getJar();
		if (outjar.exists()) {
			JarSource jar = JarSource.prepareJar(
					ResourceRef.forNamedFile(getResourceRef().getOriginalResource(), outjar));
			setMainClass(jar.getMainClass());
			context.setPersistentJvmArgs(jar.getRuntimeOptions());
			context.setBuildJdk(jar.getBuildJdk());
		}
	}

	public static DecoratedSource forResource(String resource) {
		return forResource(resource, null, null, null, null, false, false);
	}

	public static DecoratedSource forResource(String resource, List<String> arguments) {
		return forResource(resource, arguments, null, null, null, false, false);
	}

	public static DecoratedSource forResource(String resource, List<String> arguments,
			Map<String, String> properties) {
		return forResource(resource, arguments, properties, null, null, false, false);
	}

	public static DecoratedSource forResource(String resource, List<String> arguments,
			Map<String, String> properties,
			List<String> dependencies, List<String> classpaths, boolean fresh, boolean forcejsh) {
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
					throw new IllegalArgumentException(
							"Alias " + resource + " from " + alias.catalog.catalogFile + " failed to resolve "
									+ alias.scriptRef);
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

		RunContext ctx = new RunContext(arguments, properties);
		ctx.setForceJsh(forcejsh);
		ctx.setOriginalRef(resource);
		ctx.setAlias(alias);
		ctx.setAdditionalDependencies(dependencies);
		ctx.setAdditionalClasspaths(classpaths);
		return new DecoratedSource(ru, ctx);
	}

	public static DecoratedSource forScriptResource(ResourceRef resourceRef, List<String> arguments,
			Map<String, String> properties) {
		return forScriptResource(resourceRef, arguments, properties, null, null, false, false);
	}

	public static DecoratedSource forScriptResource(ResourceRef resourceRef, List<String> arguments,
			Map<String, String> properties,
			List<String> dependencies, List<String> classpaths, boolean fresh, boolean forcejsh) {
		// note script file must be not null at this point
		Source ru;
		if (resourceRef.getFile().getName().endsWith(".jar")) {
			ru = JarSource.prepareJar(resourceRef);
		} else {
			ru = ScriptSource.prepareScript(resourceRef);
		}

		RunContext ctx = new RunContext(arguments, properties);
		ctx.setForceJsh(forcejsh);
		ctx.setAdditionalDependencies(dependencies);
		ctx.setAdditionalClasspaths(classpaths);
		return new DecoratedSource(ru, ctx);
	}

	public static DecoratedSource forScript(String script, List<String> arguments,
			Map<String, String> properties) {
		return forScript(script, arguments, properties, null, null, false, false);
	}

	public static DecoratedSource forScript(String script, List<String> arguments,
			Map<String, String> properties,
			List<String> dependencies, List<String> classpaths,
			boolean fresh, boolean forcejsh) {
		Source ru = new ScriptSource(script);
		RunContext ctx = new RunContext(arguments, properties);
		ctx.setForceJsh(forcejsh);
		ctx.setAdditionalDependencies(dependencies);
		ctx.setAdditionalClasspaths(classpaths);
		return new DecoratedSource(ru, ctx);
	}
}
