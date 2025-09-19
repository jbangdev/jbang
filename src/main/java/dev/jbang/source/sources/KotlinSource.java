package dev.jbang.source.sources;

import static dev.jbang.net.KotlinManager.resolveInKotlinHome;
import static dev.jbang.source.parser.TagReader.COMPILE_OPTIONS_COMMENT_PREFIX;
import static dev.jbang.source.parser.TagReader.JAVA_OPTIONS_COMMENT_PREFIX;
import static dev.jbang.source.parser.TagReader.NATIVE_OPTIONS_COMMENT_PREFIX;
import static dev.jbang.source.parser.TagReader.RUNTIME_OPTIONS_COMMENT_PREFIX;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.NonNull;

import dev.jbang.net.KotlinManager;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.*;
import dev.jbang.source.AppBuilder;
import dev.jbang.source.buildsteps.CompileBuildStep;
import dev.jbang.util.Util;

public class KotlinSource extends Source {
	public static final String KOTLIN_COMMENT_PREFIX = "KOTLIN";

	public KotlinSource(ResourceRef script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	@Override
	public @NonNull Type getType() {
		return Type.kotlin;
	}

	@Override
	protected List<String> collectBinaryDependencies() {
		final List<String> allDependencies = super.collectBinaryDependencies();
		allDependencies.add("org.jetbrains.kotlin:kotlin-stdlib:" + getKotlinVersion());
		return allDependencies;
	}

	@Override
	protected List<String> getCompileOptions() {
		return getTagReader().collectOptions(COMPILE_OPTIONS_COMMENT_PREFIX);
	}

	@Override
	protected List<String> getNativeOptions() {
		return getTagReader().collectOptions(NATIVE_OPTIONS_COMMENT_PREFIX);
	}

	@Override
	protected List<String> getRuntimeOptions() {
		return getTagReader().collectOptions(JAVA_OPTIONS_COMMENT_PREFIX, RUNTIME_OPTIONS_COMMENT_PREFIX);
	}

	@Override
	public Builder<CmdGeneratorBuilder> getBuilder(BuildContext ctx) {
		return new KotlinAppBuilder(ctx);
	}

	public String getKotlinVersion() {
		return getTagReader().collectTags(KOTLIN_COMMENT_PREFIX)
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
			protected String getCompilerBinary() {
				return resolveInKotlinHome("kotlinc",
						((KotlinSource) ctx.getProject().getMainSource()).getKotlinVersion());
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
			protected String getMainExtension() {
				return Type.kotlin.extension;
			}
		}
	}
}
