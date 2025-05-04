package dev.jbang.source.generators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.jbang.source.BuildContext;
import dev.jbang.source.CmdGenerator;
import dev.jbang.util.Util;

public class NativeCmdGenerator extends BaseCmdGenerator<NativeCmdGenerator> {
	private final CmdGenerator fallback;

	public NativeCmdGenerator(BuildContext ctx, CmdGenerator fallback) {
		super(ctx);
		this.fallback = fallback;
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

		fullArgs.addAll(arguments);

		return generateCommandLineString(fullArgs);
	}

	@Override
	protected List<String> generateCommandLineList() throws IOException {
		return null;
	}
}
