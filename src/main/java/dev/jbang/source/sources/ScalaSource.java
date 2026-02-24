package dev.jbang.source.sources;

import static dev.jbang.net.ScalaManager.resolveInScalaHome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.NonNull;

import dev.jbang.net.ScalaManager;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.*;
import dev.jbang.source.AppBuilder;
import dev.jbang.source.buildsteps.CompileBuildStep;
import dev.jbang.source.parser.Directives;
import dev.jbang.util.Util;

public class ScalaSource extends Source {

	public ScalaSource(ResourceRef script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	@Override
	public @NonNull Type getType() {
		return Type.scala;
	}

	@Override
	protected List<String> collectBinaryDependencies() {
		final List<String> allDependencies = super.collectBinaryDependencies();
		final String scalaVersion = getScalaVersion();
		if (scalaVersion.startsWith("2.")) {
			allDependencies.add("org.scala-lang:scala-library:" + scalaVersion);
		} else {
			allDependencies.add("org.scala-lang:scala3-library_3:" + scalaVersion);
		}
		return allDependencies;
	}

	@Override
	protected List<String> getCompileOptions() {
		return getDirectives().compileOptions();
	}

	@Override
	protected List<String> getNativeOptions() {
		return getDirectives().nativeOptions();
	}

	@Override
	protected List<String> getRuntimeOptions() {
		return getDirectives().runtimeOptions();
	}

	@Override
	public Builder<CmdGeneratorBuilder> getBuilder(BuildContext ctx) {
		return new ScalaAppBuilder(ctx);
	}

	public String getScalaVersion() {
		return getDirectives().collectDirectives(Directives.Names.SCALA)
			.findFirst()
			.map(ScalaManager::normalizeVersion)
			.orElse(ScalaManager.DEFAULT_SCALA_VERSION);
	}

	public static class ScalaAppBuilder extends AppBuilder {
		public ScalaAppBuilder(BuildContext ctx) {
			super(ctx);
		}

		@Override
		protected Builder<Project> getCompileBuildStep() {
			return new ScalaCompileBuildStep();
		}

		protected class ScalaCompileBuildStep extends CompileBuildStep {

			public ScalaCompileBuildStep() {
				super(ScalaAppBuilder.this.ctx);
			}

			@Override
			protected String getCompilerBinary() {
				return resolveInScalaHome("scalac",
						((ScalaSource) ctx.getProject().getMainSource()).getScalaVersion());
			}

			@Override
			protected List<String> getCompileCommandOptions() throws IOException {
				List<String> optionList = new ArrayList<>();
				optionList.addAll(ctx.getProject().getMainSourceSet().getCompileOptions());
				String path = ctx.resolveClassPath().getClassPath();
				if (!Util.isBlankString(path)) {
					optionList.addAll(Arrays.asList("-classpath", path));
				}
				optionList.addAll(Arrays.asList("-d", ctx.getCompileDir().toAbsolutePath().toString()));
				return optionList;
			}

			@Override
			protected void runCompiler(ProcessBuilder processBuilder) throws IOException {
				processBuilder.environment()
					.put("JAVA_HOME",
							ctx.getProject()
								.projectJdk()
								.home()
								.toString());
				processBuilder.environment().remove("SCALA_HOME");
				super.runCompiler(processBuilder);
			}

			@Override
			protected String getMainExtension() {
				return Type.scala.extension;
			}
		}
	}
}
