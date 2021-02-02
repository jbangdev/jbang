package dev.jbang.cli;

import static dev.jbang.Settings.CP_SEPARATOR;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.DecoratedSource;

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

		public ScriptInfo(DecoratedSource xrunit) {
			List<String> collectDependencies = xrunit.collectAllDependencies();
			String cp = xrunit.resolveClassPath(offline);

			originalResource = xrunit.getResourceRef().getOriginalResource();
			backingResource = xrunit.getResourceRef().getFile().toString();

			applicationJar = xrunit.getJar().getAbsolutePath();
			mainClass = xrunit.getMainClass();

			if (cp.isEmpty()) {
				resolvedDependencies = Collections.emptyList();
			} else {
				resolvedDependencies = Arrays.asList(cp.split(CP_SEPARATOR));
			}

			javaVersion = xrunit.javaVersion();

		}
	}

	protected ScriptInfo getInfo() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		xrunit = DecoratedSource.forResource(scriptOrFile, null, null, dependencies, classpaths, false, forcejsh);

		ScriptInfo info = new ScriptInfo(xrunit);

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
