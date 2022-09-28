package dev.jbang.source.generators;

import static dev.jbang.source.builders.BaseBuilder.getImageName;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dev.jbang.source.Project;
import dev.jbang.source.RunContext;
import dev.jbang.util.Util;

public class NativeCmdGenerator extends BaseCmdGenerator<NativeCmdGenerator> {
	protected final Project project;
	protected final RunContext ctx;

	public NativeCmdGenerator(Project prj, RunContext ctx) {
		this.project = prj;
		this.ctx = ctx;
	}

	@Override
	protected Project getProject() {
		return project;
	}

	@Override
	public String generate() throws IOException {
		List<String> fullArgs = new ArrayList<>();

		String imagename = getImageName(project.getJarFile()).toString();
		if (new File(imagename).exists()) {
			fullArgs.add(imagename);
		} else {
			Util.warnMsg("native built image not found - running in java mode.");
			return getProject().cmdGenerator(ctx).generate();
		}

		fullArgs.addAll(arguments);

		return generateCommandLineString(fullArgs);
	}

	@Override
	protected List<String> generateCommandLineList() throws IOException {
		return null;
	}
}
