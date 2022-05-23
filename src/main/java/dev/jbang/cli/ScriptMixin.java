package dev.jbang.cli;

import java.util.List;

import dev.jbang.source.Code;
import dev.jbang.source.RunContext;

import picocli.CommandLine;

public class ScriptMixin {
	@CommandLine.Option(names = { "-s",
			"--sources" }, converter = CommaSeparatedConverter.class, description = "Add additional sources.")
	List<String> sources;

	@CommandLine.Option(names = {
			"--files" }, converter = CommaSeparatedConverter.class, description = "Add additional files.")
	List<String> resources;

	@CommandLine.Option(names = { "--jsh" }, description = "Force input to be interpreted with jsh/jshell")
	boolean forcejsh = false;

	@CommandLine.Parameters(index = "0", arity = "0..1", description = "A reference to a source file")
	String scriptOrFile;

	public void validate() {
		if (scriptOrFile == null) {
			throw new IllegalArgumentException("Missing required parameter: '<scriptOrFile>'");
		}
	}

	static protected boolean needsJar(Code code, RunContext context) {
		// anything but .jar and .jsh files needs jar
		return !(code.isJar() || context.isForceJsh() || code.isJShell());
	}

}
