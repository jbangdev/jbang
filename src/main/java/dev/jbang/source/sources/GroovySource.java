package dev.jbang.source.sources;

import static dev.jbang.net.GroovyManager.resolveInGroovyHome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.NonNull;

import dev.jbang.net.GroovyManager;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.*;
import dev.jbang.source.AppBuilder;
import dev.jbang.source.buildsteps.CompileBuildStep;
import dev.jbang.source.parser.Directives;
import dev.jbang.util.Util;

public class GroovySource extends Source {

	public GroovySource(ResourceRef script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	@Override
	public @NonNull Type getType() {
		return Type.groovy;
	}

	@Override
	protected List<String> getCompileOptions() {
		return getDirectives().compileOptions();
	}

	@Override
	protected List<String> getNativeOptions() {
		return getDirectives().nativeOptions();
	}

	protected List<String> getRuntimeOptions() {
		List<String> gopts = Collections.singletonList("-Dgroovy.grape.enable=false");
		List<String> opts = getDirectives().runtimeOptions();
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
		return getDirectives().collectDirectives(Directives.Names.GROOVY)
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
			protected String getCompilerBinary() {
				return resolveInGroovyHome("groovyc",
						((GroovySource) ctx.getProject().getMainSource()).getGroovyVersion());
			}

			@Override
			protected List<String> getCompileCommandOptions() throws IOException {
				List<String> optionList = new ArrayList<>();
				optionList.addAll(ctx.getProject().getMainSourceSet().getCompileOptions());
				optionList.addAll(Arrays.asList("-d", ctx.getCompileDir().toAbsolutePath().toString()));
				return optionList;
			}

			@Override
			protected void runCompiler(ProcessBuilder processBuilder) throws IOException {
				Project project = ctx.getProject();
				if (project.getMainSource() instanceof GroovySource) {
					processBuilder.environment()
						.put("JAVA_HOME",
								project.projectJdk()
									.home()
									.toString());
					processBuilder.environment().remove("GROOVY_HOME");
					String path = ctx.resolveClassPath().getClassPath();
					if (!Util.isBlankString(path)) {
						processBuilder.environment().put("CLASSPATH", path);
					}
				}
				super.runCompiler(processBuilder);
			}

			@Override
			protected String getMainExtension() {
				return Type.groovy.extension;
			}
		}
	}
}
