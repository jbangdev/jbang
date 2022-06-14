package dev.jbang.source.sources;

import java.util.List;
import java.util.function.Function;

import dev.jbang.source.*;
import dev.jbang.source.builders.JavaBuilder;

public class JavaSource extends Source {

	public JavaSource(String script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	public JavaSource(ResourceRef script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	protected JavaSource(ResourceRef ref, String script, Function<String, String> replaceProperties) {
		super(ref, script, replaceProperties);
	}

	@Override
	public List<String> getCompileOptions() {
		return collectOptions("JAVAC_OPTIONS");
	}

	@Override
	public List<String> getRuntimeOptions() {
		return collectOptions("JAVA_OPTIONS");
	}

	@Override
	public Builder getBuilder(Project prj) {
		return new JavaBuilder(prj);
	}
}
