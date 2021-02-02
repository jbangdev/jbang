package dev.jbang;

import java.io.File;
import java.util.List;
import java.util.Map;

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
public class DecoratedSource {
	final private Source source;
	final private RunContext context;

	public DecoratedSource(Source source, RunContext context) {
		this.source = source;
		this.context = context;
	}

	public Source getSource() {
		return source;
	}

	public RunContext getContext() {
		return context;
	}

	public static boolean needsJar(Source source, RunContext context) {
		// anything but .jar and .jsh files needs jar
		return !(source.forJar() || context.isForceJsh() || source.forJShell());
	}

	public void importJarMetadata() {
		File outjar = source.getJar();
		if (outjar.exists()) {
			JarSource jar = JarSource.prepareJar(
					ResourceRef.forNamedFile(source.getResourceRef().getOriginalResource(), outjar));
			context.setMainClass(jar.getMainClass());
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
