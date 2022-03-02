package dev.jbang.source.scripts;

import static dev.jbang.net.GroovyManager.resolveInGroovyHome;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;

import dev.jbang.net.GroovyManager;
import dev.jbang.source.JarBuilder;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.Script;

public class GroovyScript extends Script {

	public GroovyScript(ResourceRef script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	@Override
	public List<String> getCompileOptions() {
		return Collections.emptyList();
	}

	public List<String> getRuntimeOptions() {
		return Collections.singletonList("-Dgroovy.grape.enable=false");
	}

	@Override
	public String getCompilerBinary(String requestedJavaVersion) {
		return resolveInGroovyHome("groovyc", getGroovyVersion());
	}

	@Override
	public Predicate<ClassInfo> getMainFinder() {
		return pubClass -> pubClass.method("main", JarBuilder.STRINGARRAYTYPE) != null
				|| pubClass.method("main") != null;
	}

	@Override
	public List<String> collectDependencies() {
		final List<String> allDependencies = super.collectDependencies();
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
		return collectOptions("GROOVY")
										.stream()
										.findFirst()
										.orElse(GroovyManager.DEFAULT_GROOVY_VERSION);
	}
}
