package dev.jbang.source.sources;

import java.util.function.Function;

import dev.jbang.source.*;

public class JshSource extends JavaSource {
	public JshSource(ResourceRef script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	protected JshSource(ResourceRef ref, String script, Function<String, String> replaceProperties) {
		super(ref, script, replaceProperties);
	}

	@Override
	public Builder<Project> getBuilder(Project prj, BuildContext ctx) {
		return () -> prj;
	}
}
