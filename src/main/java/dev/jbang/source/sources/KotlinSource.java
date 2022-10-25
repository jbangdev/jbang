package dev.jbang.source.sources;

import static dev.jbang.net.KotlinManager.resolveInKotlinHome;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;

import dev.jbang.net.KotlinManager;
import dev.jbang.source.*;
import dev.jbang.source.AppBuilder;
import dev.jbang.source.buildsteps.CompileBuildStep;
import dev.jbang.source.buildsteps.IntegrationBuildStep;
import dev.jbang.spi.IntegrationResult;

public class KotlinSource extends Source {

	public KotlinSource(ResourceRef script, Function<String, String> replaceProperties) {
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

	@Override
	protected List<String> getRuntimeOptions() {
		return tagReader.collectOptions("JAVA_OPTIONS", "RUNTIME_OPTIONS");
	}

	@Override
	public Builder<Project> getBuilder(Project prj) {
		return new KotlinAppBuilder(prj);
	}

	public String getKotlinVersion() {
		return tagReader.collectOptions("KOTLIN")
						.stream()
						.findFirst()
						.orElse(KotlinManager.DEFAULT_KOTLIN_VERSION);
	}

	private static class KotlinAppBuilder extends AppBuilder {
		public KotlinAppBuilder(Project project) {
			super(project);
		}

		@Override
		protected Builder<Project> getCompileBuildStep() {
			return new KotlinCompileBuildStep();
		}

		@Override
		protected Builder<IntegrationResult> getIntegrationBuildStep() {
			return new KotlinIntegrationBuildStep();
		}

		private class KotlinCompileBuildStep extends CompileBuildStep {

			public KotlinCompileBuildStep() {
				super(KotlinAppBuilder.this.project);
			}

			@Override
			protected String getCompilerBinary(String requestedJavaVersion) {
				return resolveInKotlinHome("kotlinc", ((KotlinSource) project.getMainSource()).getKotlinVersion());
			}
		}

		private class KotlinIntegrationBuildStep extends IntegrationBuildStep {
			public KotlinIntegrationBuildStep() {
				super(KotlinAppBuilder.this.project);
			}

			@Override
			protected String getMainExtension() {
				return ".kt";
			}

			@Override
			protected Predicate<ClassInfo> getMainFinder() {
				return pubClass -> pubClass.method("main", IntegrationBuildStep.STRINGARRAYTYPE) != null
						|| pubClass.method("main") != null;
			}
		}
	}
}
