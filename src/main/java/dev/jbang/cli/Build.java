package dev.jbang.cli;

import java.io.IOException;

import dev.jbang.source.RunContext;
import dev.jbang.source.Source;

import picocli.CommandLine.Command;

@Command(name = "build", description = "Compiles and stores script in the cache.")
public class Build extends BaseBuildCommand {

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		RunContext ctx = getRunContext();
		Source src = Source.forResource(scriptOrFile, ctx);

		buildIfNeeded(src, ctx);

		return EXIT_OK;
	}

	RunContext getRunContext() {
		RunContext ctx = RunContext.create(null, null,
				dependencyInfoMixin.getProperties(),
				dependencyInfoMixin.getDependencies(),
				dependencyInfoMixin.getClasspaths(),
				forcejsh);
		ctx.setJavaVersion(javaVersion);
		ctx.setMainClass(main);
		ctx.setNativeImage(nativeImage);
		ctx.setCatalog(catalog);
		return ctx;
	}
}
