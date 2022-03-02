package dev.jbang.source.builders;

import static dev.jbang.net.GroovyManager.resolveInGroovyHome;

import java.io.IOException;
import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;

import dev.jbang.net.JdkManager;
import dev.jbang.source.RunContext;
import dev.jbang.source.SourceSet;
import dev.jbang.source.scripts.GroovyScript;

public class GroovyBuilder extends BaseBuilder {

	public GroovyBuilder(SourceSet ss, RunContext ctx) {
		super(ss, ctx);
	}

	@Override
	protected String getCompilerBinary(String requestedJavaVersion) {
		return resolveInGroovyHome("groovyc", ((GroovyScript) ss.getMainSource()).getGroovyVersion());
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
		if (ss.getMainSource() instanceof GroovyScript) {
			processBuilder	.environment()
							.put("JAVA_HOME", JdkManager.getCurrentJdk(getRequestedJavaVersion()).toString());
			processBuilder.environment().remove("GROOVY_HOME");
		}
		super.runCompiler(processBuilder);
	}
}
