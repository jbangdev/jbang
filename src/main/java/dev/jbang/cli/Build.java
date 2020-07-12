package dev.jbang.cli;

import java.io.IOException;

import picocli.CommandLine.Command;

@Command(name = "build", description = "Build .java/.jsh script without running. Use for caching dependencies.")
public class Build extends Run {

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		script = prepareScript(scriptOrFile, userParams, properties);

		if (script.needsJar()) {
			build(script);
		}
		return 0;
	}
}
