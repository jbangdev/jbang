package dev.jbang.source;

import java.io.IOException;

public interface CmdGenerator {
	String generate() throws IOException;

	static CmdGeneratorBuilder builder(Project project) {
		return new CmdGeneratorBuilder(BuildContext.forProject(project));
	}

	static CmdGeneratorBuilder builder(BuildContext ctx) {
		return new CmdGeneratorBuilder(ctx);
	}

}
