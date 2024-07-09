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

public class KotlinSource extends Source {

	public KotlinSource(ResourceRef script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	public KotlinSource(String script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	@Override
	protected List<String> collectBinaryDependencies() {
		final List<String> allDependencies = super.collectBinaryDependencies();
		allDependencies.add("org.jetbrains.kotlin:kotlin-stdlib:" + getKotlinVersion());
		return allDependencies;
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
	public Builder<CmdGeneratorBuilder> getBuilder(BuildContext ctx) {
		return new KotlinAppBuilder(ctx);
	}

	public String getKotlinVersion() {
		return tagReader.collectOptions("KOTLIN")
						.stream()
						.findFirst()
						.orElse(KotlinManager.DEFAULT_KOTLIN_VERSION);
	}

	public static class KotlinAppBuilder extends AppBuilder {
		public KotlinAppBuilder(BuildContext ctx) {
			super(ctx);
		}

		@Override
		protected Builder<Project> getCompileBuildStep() {
			return new KotlinCompileBuildStep();
		}

		protected class KotlinCompileBuildStep extends CompileBuildStep {

			public KotlinCompileBuildStep() {
				super(KotlinAppBuilder.this.ctx);
			}

			@Override
			protected String getCompilerBinary(String requestedJavaVersion) {
				return resolveInKotlinHome("kotlinc",
						((KotlinSource) ctx.getProject().getMainSource()).getKotlinVersion());
			}

			@Override
			protected String getMainExtension() {
				return ".kt";
			}

			@Override
			protected Predicate<ClassInfo> getMainFinder() {
				return pubClass -> pubClass.method("main", CompileBuildStep.STRINGARRAYTYPE) != null
						|| pubClass.method("main") != null;
			}
		}
	}
}
