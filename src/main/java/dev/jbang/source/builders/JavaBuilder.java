package dev.jbang.source.builders;

import static dev.jbang.util.JavaUtil.resolveInJavaHome;

import dev.jbang.source.Project;
import dev.jbang.source.RunContext;

public class JavaBuilder extends BaseBuilder {

	public JavaBuilder(Project prj, RunContext ctx) {
		super(prj, ctx);
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
