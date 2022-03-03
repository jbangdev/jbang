package dev.jbang.source.sources;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import dev.jbang.net.GroovyManager;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.RunContext;
import dev.jbang.source.Source;
import dev.jbang.source.SourceSet;
import dev.jbang.source.builders.BaseBuilder;
import dev.jbang.source.builders.GroovyBuilder;

public class GroovySource extends Source {

	public GroovySource(ResourceRef script, Function<String, String> replaceProperties) {
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
	public BaseBuilder getBuilder(SourceSet ss, RunContext ctx) {
		return new GroovyBuilder(ss, ctx);
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

	public String getGroovyVersion() {
		return collectOptions("GROOVY")
										.stream()
										.findFirst()
										.orElse(GroovyManager.DEFAULT_GROOVY_VERSION);
	}
}
