package dev.jbang.source.sources;

import static dev.jbang.util.JavaUtil.resolveInJavaHome;

import java.util.List;
import java.util.function.Function;

import dev.jbang.source.*;
import dev.jbang.source.AppBuilder;
import dev.jbang.source.buildsteps.CompileBuildStep;
import dev.jbang.source.buildsteps.IntegrationBuildStep;
import dev.jbang.spi.IntegrationResult;

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
	public Builder<Project> getBuilder(Project prj) {
		return new JavaAppBuilder(prj);
	}

	public static class JavaAppBuilder extends AppBuilder {
		public JavaAppBuilder(Project project) {
			super(project);
		}

		@Override
		protected Builder<Project> getCompileBuildStep() {
			return new JavaCompileBuildStep();
		}

		@Override
		protected Builder<IntegrationResult> getIntegrationBuildStep() {
			return new JavaIntegrationBuildStep();
		}

		public class JavaCompileBuildStep extends CompileBuildStep {

			public JavaCompileBuildStep() {
				super(JavaAppBuilder.this.project);
			}

			@Override
			protected String getCompilerBinary(String requestedJavaVersion) {
				return resolveInJavaHome("javac", requestedJavaVersion);
			}
		}

		public class JavaIntegrationBuildStep extends IntegrationBuildStep {
			public JavaIntegrationBuildStep() {
				super(JavaAppBuilder.this.project);
			}

			@Override
			protected String getMainExtension() {
				return ".java";
			}
		}
	}
}
