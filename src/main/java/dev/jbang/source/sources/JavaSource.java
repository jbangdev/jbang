package dev.jbang.source.sources;

import static dev.jbang.util.JavaUtil.resolveInJavaHome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.Jdk;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.*;
import dev.jbang.source.AppBuilder;
import dev.jbang.source.buildsteps.CompileBuildStep;
import dev.jbang.util.Util;

public class JavaSource extends Source {

	public JavaSource(ResourceRef script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	protected JavaSource(ResourceRef ref, String script, Function<String, String> replaceProperties) {
		super(ref, script, replaceProperties);
	}

	@Override
	public @NonNull Type getType() {
		return Type.java;
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
		return new JavaAppBuilder(ctx);
	}

	public static class JavaAppBuilder extends AppBuilder {
		public JavaAppBuilder(BuildContext ctx) {
			super(ctx);
		}

		@Override
		protected Builder<Project> getCompileBuildStep() {
			return new JavaCompileBuildStep();
		}

		public class JavaCompileBuildStep extends CompileBuildStep {

			public JavaCompileBuildStep() {
				super(JavaAppBuilder.this.ctx);
			}

			@Override
			protected String getCompilerBinary() {
				Project project = ctx.getProject();
				Jdk jdk = project.projectJdk();
				String requestedJavaVersion = project.getJavaVersion();
				if (requestedJavaVersion == null
						&& project.getModuleName().isPresent()
						&& jdk.majorVersion() < 9) {
					// Make sure we use at least Java 9 when dealing with modules
					requestedJavaVersion = "9+";
					jdk = project.projectJdkManager().getOrInstallJdk(requestedJavaVersion);
				}
				return resolveInJavaHome("javac", jdk);
			}

			@Override
			protected List<String> getCompileCommandOptions() throws IOException {
				List<String> optionList = new ArrayList<>();

				Project project = ctx.getProject();
				if (project.enablePreview()) {
					optionList.add("--enable-preview");
					optionList.add("-source");
					optionList.add("" + project.projectJdk().majorVersion());
				}
				optionList.addAll(project.getMainSourceSet().getCompileOptions());
				String path = ctx.resolveClassPath().getClassPath();
				if (!Util.isBlankString(path)) {
					if (project.getModuleName().isPresent()) {
						optionList.addAll(Arrays.asList("-p", path));
					} else {
						optionList.addAll(Arrays.asList("-classpath", path));
					}
				}
				optionList.addAll(Arrays.asList("-d", ctx.getCompileDir().toAbsolutePath().toString()));

				return optionList;
			}

			@Override
			protected String getMainExtension() {
				return Type.java.extension;
			}
		}
	}
}
