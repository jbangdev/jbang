package dev.jbang.cli;

import picocli.CommandLine;

public abstract class BaseScriptDepsCommand extends BaseScriptCommand {
	@CommandLine.Mixin
	DependencyInfoMixin dependencyInfoMixin;

}
