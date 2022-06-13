package dev.jbang.source.builders;

import static dev.jbang.net.GroovyManager.resolveInGroovyHome;

import java.io.IOException;
import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;

import dev.jbang.net.JdkManager;
import dev.jbang.source.Project;
import dev.jbang.source.RunContext;
import dev.jbang.source.sources.GroovySource;

public class GroovyBuilder extends BaseBuilder {

	public GroovyBuilder(Project prj, RunContext ctx) {
		super(prj, ctx);
	}

	@Override
	protected String getCompilerBinary(String requestedJavaVersion) {
		return resolveInGroovyHome("groovyc", ((GroovySource) prj.getMainSource()).getGroovyVersion());
	}

	@Override
	protected Predicate<ClassInfo> getMainFinder() {
		return pubClass -> pubClass.method("main", BaseBuilder.STRINGARRAYTYPE) != null
				|| pubClass.method("main") != null;
	}

	@Override
	protected String getMainExtension() {
		return ".groovy";
	}

	@Override
	protected void runCompiler(ProcessBuilder processBuilder) throws IOException {
		if (prj.getMainSource() instanceof GroovySource) {
			processBuilder	.environment()
							.put("JAVA_HOME", JdkManager.getCurrentJdk(ctx.getJavaVersionOr(prj)).toString());
			processBuilder.environment().remove("GROOVY_HOME");
		}
		super.runCompiler(processBuilder);
	}
}
