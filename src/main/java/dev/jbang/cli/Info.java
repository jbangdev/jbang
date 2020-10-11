package dev.jbang.cli;

import static dev.jbang.Settings.CP_SEPARATOR;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.Script;

import picocli.CommandLine;

@CommandLine.Command(name = "info", description = "Provides info about the script for tools (and humans who are tools).", subcommands = {
		Tools.class, ClassPath.class })
public class Info {
}

abstract class BaseInfoCommand extends BaseScriptCommand {

	class ScriptInfo {

		String originalResource;

		String backingResource;

		List<String> resolvedClasspath;

		public ScriptInfo(Script script) {
			List<String> collectDependencies = script.collectDependencies();
			String cp = script.resolveClassPath(offline);

			originalResource = script.getOriginalFile();
			backingResource = script.getBackingFile().toString();

			resolvedClasspath = Arrays.asList(cp.split(CP_SEPARATOR));
		}
	}

	protected ScriptInfo getInfo() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		script = prepareScript(scriptOrFile, userParams);

		ScriptInfo info = new ScriptInfo(script);

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

		System.out.println(String.join(CP_SEPARATOR, getInfo().resolvedClasspath));

		return EXIT_OK;
	}
}
