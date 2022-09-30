package dev.jbang.source.generators;

import static dev.jbang.source.builders.BaseBuilder.getImageName;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dev.jbang.source.Code;
import dev.jbang.source.RunContext;
import dev.jbang.util.Util;

public class NativeCmdGenerator extends BaseCmdGenerator<NativeCmdGenerator> {
	protected final Code code;
	protected final RunContext ctx;

	public NativeCmdGenerator(Code code, RunContext ctx) {
		this.code = code;
		this.ctx = ctx;
	}

	@Override
	protected Code getCode() {
		return code;
	}

	@Override
	public String generate() throws IOException {
		List<String> fullArgs = new ArrayList<>();

		String imagename = getImageName(code.getJarFile()).toString();
		if (new File(imagename).exists()) {
			fullArgs.add(imagename);
		} else {
			Util.warnMsg("native built image not found - running in java mode.");
			return getCode().cmdGenerator(ctx).generate();
		}

		fullArgs.addAll(arguments);

		return generateCommandLineString(fullArgs);
	}

	@Override
	protected List<String> generateCommandLineList() throws IOException {
		return null;
	}
}
