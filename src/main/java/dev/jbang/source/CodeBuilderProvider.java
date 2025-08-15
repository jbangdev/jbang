package dev.jbang.source;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;

import dev.jbang.source.sources.JavaSource;

public class CodeBuilderProvider implements Supplier<Builder<CmdGeneratorBuilder>> {
	private final BuildContext buildContext;

	/**
	 * Returns an initialized <code>CodeBuilderProvider</code> using the given
	 * <code>Project</code> which will use a default <code>BuildContext</code> to
	 * store target files and intermediate results
	 * 
	 * @param prj the <code>Project</code> to use
	 * @return A <code>CodeBuilder</code>
	 */
	public static CodeBuilderProvider create(Project prj) {
		return create(BuildContext.forProject(prj));
	}

	/**
	 * Returns an initialized <code>CodeBuilderProvider</code> using the given
	 * <code>BuildContext</code> to store target files and intermediate results
	 * 
	 * @param ctx the <code>BuildContext</code> to use
	 * @return A <code>CodeBuilder</code>
	 */
	public static CodeBuilderProvider create(BuildContext ctx) {
		return new CodeBuilderProvider(ctx);
	}

	protected CodeBuilderProvider(BuildContext buildContext) {
		this.buildContext = buildContext;
	}

	/**
	 * Returns a <code>Builder</code> that can be used to turn this
	 * <code>Project</code> into executable code.
	 *
	 * @return A <code>Builder</code>
	 */
	@NonNull
	@Override
	public Builder<CmdGeneratorBuilder> get() {
		return get(buildContext);
	}

	@NonNull
	protected Builder<CmdGeneratorBuilder> get(BuildContext ctx) {
		Builder<CmdGeneratorBuilder> builder = getBuilder(ctx);
		Project prj = ctx.getProject();
		if (!prj.getSubProjects().isEmpty()) {
			List<Builder<CmdGeneratorBuilder>> subBuilders = prj.getSubProjects()
				.stream()
				.map(p -> get(ctx.forSubProject(p)))
				.collect(Collectors.toList());
			return () -> {
				for (Builder<CmdGeneratorBuilder> b : subBuilders) {
					b.build();
				}
				return builder.build();
			};
		} else {
			return builder;
		}
	}

	@NonNull
	protected Builder<CmdGeneratorBuilder> getBuilder(BuildContext ctx) {
		Project prj = ctx.getProject();
		if (prj.getMainSource() != null) {
			return prj.getMainSource().getBuilder(ctx);
		} else {
			if (prj.isJar() && prj.isNativeImage()) {
				// JARs normally don't need building unless a native image
				// was requested
				return new JavaSource.JavaAppBuilder(ctx);
			} else {
				// Returns a no-op builder that when its build() gets called
				// immediately returns a builder for a CmdGenerator
				return () -> CmdGenerator.builder(ctx);
			}
		}
	}
}
