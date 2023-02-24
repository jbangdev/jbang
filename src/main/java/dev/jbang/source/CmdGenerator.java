package dev.jbang.source;

import java.io.IOException;

public interface CmdGenerator {
	String generate() throws IOException;

	static CmdGeneratorBuilder builder(Project project) {
		return new CmdGeneratorBuilder(project, BuildContext.forProject(project));
	}

	static CmdGeneratorBuilder builder(Project project, BuildContext ctx) {
		return new CmdGeneratorBuilder(project, ctx);
	}

}
