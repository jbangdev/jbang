package dev.jbang.source.generators;

import static dev.jbang.source.builders.BaseBuilder.getImageName;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dev.jbang.source.CmdGenerator;
import dev.jbang.source.Project;
import dev.jbang.util.Util;

public class NativeCmdGenerator extends BaseCmdGenerator<NativeCmdGenerator> {
	private final Project project;
	private final CmdGenerator fallback;

	public NativeCmdGenerator(Project prj, CmdGenerator fallback) {
		this.project = prj;
		this.fallback = fallback;
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
