package dev.jbang.source.sources;

import static dev.jbang.net.GroovyManager.resolveInGroovyHome;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;

import dev.jbang.net.GroovyManager;
import dev.jbang.net.JdkManager;
import dev.jbang.source.*;
import dev.jbang.source.AppBuilder;
import dev.jbang.source.buildsteps.CompileBuildStep;
import dev.jbang.util.Util;

public class GroovySource extends Source {

	public GroovySource(ResourceRef script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	public GroovySource(String script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	@Override
	protected List<String> getCompileOptions() {
		return tagReader.collectOptions("COMPILE_OPTIONS");
	}

	@Override
	protected List<String> getNativeOptions() {
		return tagReader.collectOptions("NATIVE_OPTIONS");
	}

	protected List<String> getRuntimeOptions() {
		List<String> gopts = Collections.singletonList("-Dgroovy.grape.enable=false");
		List<String> opts = tagReader.collectOptions("JAVA_OPTIONS", "RUNTIME_OPTIONS");
		opts.remove("-g");
		return Util.join(gopts, opts);
	}

	@Override
	protected List<String> collectBinaryDependencies() {
		final List<String> allDependencies = super.collectBinaryDependencies();
		final String groovyVersion = getGroovyVersion();
		if (groovyVersion.startsWith("4.") || groovyVersion.startsWith("5.")) {
			allDependencies.add("org.apache.groovy:groovy:" + groovyVersion);
		} else {
			allDependencies.add("org.codehaus.groovy:groovy:" + groovyVersion);
		}
		return allDependencies;
	}

	public String getGroovyVersion() {
		return tagReader.collectOptions("GROOVY")
						.stream()
						.findFirst()
						.orElse(GroovyManager.DEFAULT_GROOVY_VERSION);
	}

	@Override
	public Builder<CmdGeneratorBuilder> getBuilder(BuildContext ctx) {
		return new GroovyAppBuilder(ctx);
	}

	private static class GroovyAppBuilder extends AppBuilder {
		public GroovyAppBuilder(BuildContext ctx) {
			super(ctx);
		}

		@Override
		protected Builder<Project> getCompileBuildStep() {
			return new GroovyCompileBuildStep();
		}

		private class GroovyCompileBuildStep extends CompileBuildStep {

			public GroovyCompileBuildStep() {
				super(GroovyAppBuilder.this.ctx);
			}

			@Override
			protected String getCompilerBinary(String requestedJavaVersion) {
				return resolveInGroovyHome("groovyc",
						((GroovySource) ctx.getProject().getMainSource()).getGroovyVersion());
			}

			@Override
			protected void runCompiler(ProcessBuilder processBuilder) throws IOException {
				Project project = ctx.getProject();
				if (project.getMainSource() instanceof GroovySource) {
					processBuilder	.environment()
									.put("JAVA_HOME",
											JdkManager.getOrInstallJdk(project.getJavaVersion()).getHome().toString());
					processBuilder.environment().remove("GROOVY_HOME");
				}
				super.runCompiler(processBuilder);
			}

			@Override
			protected String getMainExtension() {
				return ".groovy";
			}

			@Override
			protected Predicate<ClassInfo> getMainFinder() {
				return pubClass -> pubClass.method("main", CompileBuildStep.STRINGARRAYTYPE) != null
						|| pubClass.method("main") != null;
			}
		}
	}
}
