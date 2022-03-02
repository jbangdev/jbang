package dev.jbang.source.builders;

import static dev.jbang.util.JavaUtil.resolveInJavaHome;

import dev.jbang.source.RunContext;
import dev.jbang.source.SourceSet;

public class JavaBuilder extends BaseBuilder {

	public JavaBuilder(SourceSet ss, RunContext ctx) {
		super(ss, ctx);
	}

	@Override
	protected String getCompilerBinary(String requestedJavaVersion) {
		return resolveInJavaHome("javac", requestedJavaVersion);
	}

	@Override
	protected String getMainExtension() {
		return ".java";
	}
}
