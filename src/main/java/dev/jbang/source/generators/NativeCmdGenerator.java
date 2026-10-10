package dev.jbang.source.generators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import dev.jbang.source.BuildContext;
import dev.jbang.source.CmdGenerator;
import dev.jbang.source.Project;
import dev.jbang.util.Util;

public class NativeCmdGenerator extends BaseCmdGenerator<NativeCmdGenerator> {
	private final CmdGenerator fallback;

	private List<String> runtimeOptions = Collections.emptyList();

	public NativeCmdGenerator(BuildContext ctx, CmdGenerator fallback) {
		super(ctx);
		this.fallback = fallback;
	}

	public NativeCmdGenerator runtimeOptions(List<String> runtimeOptions) {
		this.runtimeOptions = runtimeOptions != null ? runtimeOptions : Collections.emptyList();
		return this;
	}

	@Override
	public String generate() throws IOException {
		List<String> fullArgs = new ArrayList<>();

		if (ctx.getProject().enablePreview()) {
			fullArgs.add("--enable-preview");
		}

		Path image = ctx.getNativeImageFile();
		if (Files.exists(image)) {
			fullArgs.add(image.toString());
		} else {
			Util.warnMsg("native built image not found - running in java mode.");
			return fallback.generate();
		}

		addPropertyFlags(fullArgs);

		fullArgs.addAll(arguments);

		return generateCommandLineString(fullArgs);
	}

	@Override
	protected List<String> generateCommandLineList() throws IOException {
		return null;
	}

	/**
	 * Passes the system properties on to the native executable.
	 *
	 * <p>
	 * A native image accepts <code>-Dkey=value</code> at run time just like a JVM
	 * does, so properties set with <code>-D</code> or with a
	 * <code>//RUNTIME_OPTIONS</code> directive can simply be handed to the binary.
	 * They are consumed by the image runtime and never reach
	 * <code>main(String[])</code>.
	 *
	 * <p>
	 * Any other runtime option is deliberately <em>not</em> passed on, because for
	 * a native image it either fails the run or silently corrupts the program's
	 * arguments: an unknown <code>-XX:</code> flag makes the binary exit with
	 * "Could not find option", while options the image runtime does not recognise
	 * at all (<code>-agentlib:</code>, <code>-verbose:gc</code>, ...) are left in
	 * place and arrive as program arguments. Since dropping them silently is what
	 * made this hard to diagnose in the first place, we say so instead.
	 *
	 * @param result the argument list to add the flags to
	 */
	private void addPropertyFlags(List<String> result) {
		Project project = ctx.getProject();
		for (Map.Entry<String, String> entry : project.getProperties().entrySet()) {
			result.add("-D" + entry.getKey() + "=" + entry.getValue());
		}

		List<String> ignored = new ArrayList<>();
		for (String option : allRuntimeOptions(project)) {
			if (option.startsWith("-D")) {
				result.add(option);
			} else {
				ignored.add(option);
			}
		}
		if (!ignored.isEmpty()) {
			Util.warnMsg("Runtime options are not supported by a native image and are ignored: "
					+ String.join(" ", ignored));
		}
	}

	private List<String> allRuntimeOptions(Project project) {
		List<String> options = new ArrayList<>(project.getRuntimeOptions());
		options.addAll(runtimeOptions);
		return options;
	}
}
