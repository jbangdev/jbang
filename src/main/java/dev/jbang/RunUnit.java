package dev.jbang;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import dev.jbang.cli.BaseCommand;

public interface RunUnit {

	ResourceRef getResourceRef();

	/**
	 * The actual local file that `getScriptResource()` refers to. This might be the
	 * sane as `getScriptResource()` if that pointed to a file on the local file
	 * system, in all other cases it will refer to a downloaded copy in Jbang's
	 * cache.
	 */
	File getBackingFile();

	/**
	 * Returns the path to the main application JAR file
	 */
	File getJar();

	/**
	 * Returns the requested Java version
	 */
	String javaVersion();

	List<String> collectAllDependencies(Properties props);

	static ExtendedRunUnit forResource(String resource) {
		return forResource(resource, null, null, null, null, false, false);
	}

	static ExtendedRunUnit forResource(String resource, List<String> arguments) {
		return forResource(resource, arguments, null, null, null, false, false);
	}

	static ExtendedRunUnit forResource(String resource, List<String> arguments,
			Map<String, String> properties) {
		return forResource(resource, arguments, properties, null, null, false, false);
	}

	static ExtendedRunUnit forResource(String resource, List<String> arguments,
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

		RunUnit ru;
		if (resourceRef.getFile().getName().endsWith(".jar")) {
			ru = Jar.prepareJar(resourceRef);
		} else {
			ru = Script.prepareScript(resourceRef);
		}

		ExtendedRunUnit s = new ExtendedRunUnit(ru, arguments, properties);
		s.setForcejsh(forcejsh);
		s.setOriginalRef(resource);
		s.setAlias(alias);
		s.setAdditionalDependencies(dependencies);
		s.setAdditionalClasspaths(classpaths);
		return s;
	}

	static ExtendedRunUnit forScriptResource(ResourceRef resourceRef, List<String> arguments,
			Map<String, String> properties) {
		return forScriptResource(resourceRef, arguments, properties, null, null, false, false);
	}

	static ExtendedRunUnit forScriptResource(ResourceRef resourceRef, List<String> arguments,
			Map<String, String> properties,
			List<String> dependencies, List<String> classpaths, boolean fresh, boolean forcejsh) {
		// note script file must be not null at this point
		RunUnit ru;
		if (resourceRef.getFile().getName().endsWith(".jar")) {
			ru = Jar.prepareJar(resourceRef);
		} else {
			ru = Script.prepareScript(resourceRef);
		}

		ExtendedRunUnit s = new ExtendedRunUnit(ru, arguments, properties);
		s.setForcejsh(forcejsh);
		s.setAdditionalDependencies(dependencies);
		s.setAdditionalClasspaths(classpaths);
		return s;
	}

	static ExtendedRunUnit forScript(String script, List<String> arguments,
			Map<String, String> properties) {
		return forScript(script, arguments, properties, null, null, false, false);
	}

	static ExtendedRunUnit forScript(String script, List<String> arguments,
			Map<String, String> properties,
			List<String> dependencies, List<String> classpaths,
			boolean fresh, boolean forcejsh) {
		RunUnit ru = new Script(script);
		ExtendedRunUnit s = new ExtendedRunUnit(ru, arguments, properties);
		s.setForcejsh(forcejsh);
		s.setAdditionalDependencies(dependencies);
		s.setAdditionalClasspaths(classpaths);
		return s;
	}
}
