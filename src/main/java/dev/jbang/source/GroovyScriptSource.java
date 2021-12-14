package dev.jbang.source;

import static dev.jbang.net.GroovyManager.resolveInGroovyHome;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;

import dev.jbang.cli.BaseBuildCommand;
import dev.jbang.net.GroovyManager;

public class GroovyScriptSource extends ScriptSource {

	protected GroovyScriptSource(ResourceRef script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	@Override
	public List<String> getCompileOptions() {
		return Collections.emptyList();
	}

	public List<String> getRuntimeOptions() {
		return Arrays.asList("-Dgroovy.grape.enable=false");
	}

	@Override
	public String getCompilerBinary(String requestedJavaVersion) {
		return resolveInGroovyHome("groovyc", getGroovyVersion());
	}

	@Override
	public Predicate<ClassInfo> getMainFinder() {
		return pubClass -> pubClass.method("main", BaseBuildCommand.STRINGARRAYTYPE) != null
				|| pubClass.method("main") != null;
	}

	@Override
	public List<String> getAllDependencies() {
		final List<String> allDependencies = super.getAllDependencies();
		final String groovyVersion = getGroovyVersion();
		if (groovyVersion.startsWith("4.")) {
			allDependencies.add("org.apache.groovy:groovy:" + groovyVersion);
		} else {
			allDependencies.add("org.codehaus.groovy:groovy:" + groovyVersion);
		}
		return allDependencies;
	}

	@Override
	protected String getMainExtension() {
		return ".groovy";
	}

	public String getGroovyVersion() {
		return collectAll((s) -> collectOptions("GROOVY"))
															.stream()
															.findFirst()
															.orElse(GroovyManager.DEFAULT_GROOVY_VERSION);
	}
}
