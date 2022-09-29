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
import dev.jbang.source.ProjectBuilder;
import dev.jbang.source.buildsteps.CompileBuildStep;
import dev.jbang.source.buildsteps.IntegrationBuildStep;
import dev.jbang.spi.IntegrationResult;

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

	@Override
	public Builder<Project> getBuilder(Project prj) {
		return new GroovyProjectBuilder(prj);
	}

	private static class GroovyProjectBuilder extends ProjectBuilder {
		public GroovyProjectBuilder(Project project) {
			super(project);
		}

		@Override
		protected Builder<Project> getCompileBuildStep() {
			return new GroovyCompileBuildStep();
		}

		@Override
		protected Builder<IntegrationResult> getIntegrationBuildStep() {
			return new GroovyIntegrationBuildStep();
		}

		private class GroovyCompileBuildStep extends CompileBuildStep {

			public GroovyCompileBuildStep() {
				super(GroovyProjectBuilder.this.project);
			}

			@Override
			protected String getCompilerBinary(String requestedJavaVersion) {
				return resolveInGroovyHome("groovyc", ((GroovySource) project.getMainSource()).getGroovyVersion());
			}

			@Override
			protected void runCompiler(ProcessBuilder processBuilder) throws IOException {
				if (project.getMainSource() instanceof GroovySource) {
					processBuilder	.environment()
									.put("JAVA_HOME",
											JdkManager.getCurrentJdk(project.getJavaVersion()).toString());
					processBuilder.environment().remove("GROOVY_HOME");
				}
				super.runCompiler(processBuilder);
			}
		}

		private class GroovyIntegrationBuildStep extends IntegrationBuildStep {
			public GroovyIntegrationBuildStep() {
				super(GroovyProjectBuilder.this.project);
			}

			@Override
			protected String getMainExtension() {
				return ".groovy";
			}

			@Override
			protected Predicate<ClassInfo> getMainFinder() {
				return pubClass -> pubClass.method("main", IntegrationBuildStep.STRINGARRAYTYPE) != null
						|| pubClass.method("main") != null;
			}
		}
	}
}
