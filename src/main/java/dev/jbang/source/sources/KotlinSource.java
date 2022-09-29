package dev.jbang.source.sources;

import static dev.jbang.net.KotlinManager.resolveInKotlinHome;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;

import dev.jbang.net.KotlinManager;
import dev.jbang.source.*;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.source.buildsteps.CompileBuildStep;
import dev.jbang.source.buildsteps.IntegrationBuildStep;
import dev.jbang.spi.IntegrationResult;

public class KotlinSource extends Source {

	public KotlinSource(ResourceRef script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	@Override
	public List<String> getCompileOptions() {
		return Collections.emptyList();
	}

	@Override
	public List<String> getRuntimeOptions() {
		return collectOptions("JAVA_OPTIONS");
	}

	@Override
	public Builder<Project> getBuilder(Project prj) {
		return new KotlinProjectBuilder(prj);
	}

	public String getKotlinVersion() {
		return collectOptions("KOTLIN")
										.stream()
										.findFirst()
										.orElse(KotlinManager.DEFAULT_KOTLIN_VERSION);
	}

	private static class KotlinProjectBuilder extends ProjectBuilder {
		public KotlinProjectBuilder(Project project) {
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
				super(KotlinProjectBuilder.this.project);
			}

			@Override
			protected String getCompilerBinary(String requestedJavaVersion) {
				return resolveInKotlinHome("kotlinc", ((KotlinSource) project.getMainSource()).getKotlinVersion());
			}
		}

		private class KotlinIntegrationBuildStep extends IntegrationBuildStep {
			public KotlinIntegrationBuildStep() {
				super(KotlinProjectBuilder.this.project);
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
