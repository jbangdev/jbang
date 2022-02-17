package dev.jbang.cli;

import java.io.IOException;

import dev.jbang.source.RunContext;
import dev.jbang.source.Source;

import picocli.CommandLine.Command;

@Command(name = "build", description = "Compiles and stores script in the cache.")
public class Build extends BaseBuildCommand {

	@Override
	public Integer doCall() throws IOException {
		requireScriptArgument();
		if (insecure) {
			enableInsecure();
		}

		RunContext ctx = getRunContext();
		Source src = ctx.forResource(scriptOrFile);

		buildIfNeeded(src, ctx);

		return EXIT_OK;
	}

	RunContext getRunContext() {
		RunContext ctx = RunContext.create(null, null,
				dependencyInfoMixin.getProperties(),
				dependencyInfoMixin.getDependencies(),
				dependencyInfoMixin.getRepositories(),
				dependencyInfoMixin.getClasspaths(),
				forcejsh);
		ctx.setJavaVersion(javaVersion);
		ctx.setMainClass(main);
		ctx.setNativeImage(nativeImage);
		ctx.setCatalog(catalog);
		return ctx;
	}
}
