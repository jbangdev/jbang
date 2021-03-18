package dev.jbang.cli;

import static dev.jbang.Settings.CP_SEPARATOR;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.source.RunContext;
import dev.jbang.source.Source;

import picocli.CommandLine;

@CommandLine.Command(name = "info", description = "Provides info about the script for tools (and humans who are tools).", subcommands = {
		Tools.class, ClassPath.class })
public class Info {
}

abstract class BaseInfoCommand extends BaseScriptDepsCommand {

	class ScriptInfo {

		String originalResource;

		String backingResource;

		String applicationJar;

		String mainClass;

		List<String> resolvedDependencies;

		String javaVersion;

		List<String> runtimeOptions;

		public ScriptInfo(Source src, RunContext ctx) {
			String cp = ctx.resolveClassPath(src);

			originalResource = src.getResourceRef().getOriginalResource();
			backingResource = src.getResourceRef().getFile().toString();

			applicationJar = src.getJarFile().getAbsolutePath();
			mainClass = ctx.getMainClassOr(src);

			if (cp.isEmpty()) {
				resolvedDependencies = Collections.emptyList();
			} else {
				resolvedDependencies = Arrays.asList(cp.split(CP_SEPARATOR));
			}

			if (ctx.getBuildJdk() > 0) {
				javaVersion = Integer.toString(ctx.getBuildJdk());
			}

			if (ctx.getRuntimeOptions() != null && !ctx.getRuntimeOptions().isEmpty()) {
				runtimeOptions = ctx.getRuntimeOptions();
			}
		}
	}

	ScriptInfo getInfo() {
		if (insecure) {
			enableInsecure();
		}

		RunContext ctx = RunContext.create(null, null, dependencies, classpaths, forcejsh);
		Source src = ctx.importJarMetadataFor(Source.forResource(scriptOrFile, ctx));

		ScriptInfo info = new ScriptInfo(src, ctx);

		return info;
	}

}

@CommandLine.Command(name = "tools", description = "Prints a json description usable for tools/IDE's to get classpath and more info for a jbang script/application. Exact format is still quite experimental.")
class Tools extends BaseInfoCommand {

	@Override
	public Integer doCall() throws IOException {

		Gson parser = new GsonBuilder().setPrettyPrinting().create();
		parser.toJson(getInfo(), System.out);

		return EXIT_OK;
	}
}

@CommandLine.Command(name = "classpath", description = "Prints classpath used for this application using operating system specific path separation.")
class ClassPath extends BaseInfoCommand {

	@Override
	public Integer doCall() throws IOException {

		ScriptInfo info = getInfo();
		List<String> cp = new ArrayList<>(info.resolvedDependencies.size() + 1);
		if (info.applicationJar != null) {
			cp.add(info.applicationJar);
		}
		cp.addAll(info.resolvedDependencies);
		System.out.println(String.join(CP_SEPARATOR, cp));

		return EXIT_OK;
	}
}
