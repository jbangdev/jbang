package dev.jbang.cli;

import java.util.List;

import picocli.CommandLine;

public abstract class BaseScriptDepsCommand extends BaseScriptCommand {

	@CommandLine.Option(names = { "--deps" }, description = "Add additional dependencies.")
	List<String> dependencies;

	@CommandLine.Option(names = { "--cp", "--class-path" }, description = "Add class path entries.")
	List<String> classpaths;

}
