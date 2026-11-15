package dev.jbang.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.aesh.command.CommandDefinition;

import dev.jbang.ExitException;
import dev.jbang.catalog.Alias;
import dev.jbang.resources.resolvers.AliasResourceResolver.AliasedResourceRef;
import dev.jbang.source.BuildContext;
import dev.jbang.source.Project;
import dev.jbang.util.CremaRuntime;
import dev.jbang.util.Util;

@CommandDefinition(name = "crema", description = "Experimental: run Java bytecode inside a Crema-enabled native JBang.", generateHelp = true, stopAtFirstPositional = true)
public class Crema extends Run {

	@Override
	public Integer doCall() throws IOException {
		requireScriptArgument();
		if (!CremaRuntime.isSupported()) {
			throw ExitException.invalidInput(
					"jbang crema requires the experimental nativeImageCrema build; ordinary JVM and native builds are unsupported.");
		}
		if (!runMixin.opts().isEmpty() || Boolean.TRUE.equals(nativeMixin.nativeImage)
				|| buildMixin.module != null || Boolean.TRUE.equals(enablePreviewRequested)) {
			throw ExitException
				.invalidInput("jbang crema does not support JVM run options, --native, --module or preview execution.");
		}
		if (buildMixin.javaVersion == null) {
			// Match the compile target to the Crema interpreter's java.base version so
			// produced bytecode is never newer than what the runtime can load.
			String spec = System.getProperty("java.specification.version", "");
			if (spec.startsWith("1.")) {
				spec = spec.substring(2);
			}
			if (!spec.isEmpty()) {
				buildMixin.javaVersion = spec;
			}
		}
		return super.doCall();
	}

	@Override
	protected Integer runProject(Project project) throws IOException {
		Alias alias = project.getResourceRef() instanceof AliasedResourceRef
				? ((AliasedResourceRef) project.getResourceRef()).getAlias()
				: null;
		if (project.isJShell() || project.isNativeImage() || project.enablePreview()
				|| project.enableCDS() || !project.getRuntimeOptions().isEmpty()) {
			throw ExitException.invalidInput(
					"jbang crema requires classpath bytecode without JShell, native, preview, CDS or //JAVA_OPTIONS.");
		}
		if (alias != null) {
			if ((alias.javaAgents != null && !alias.javaAgents.isEmpty())
					|| (alias.runtimeOptions != null && !alias.runtimeOptions.isEmpty())
					|| alias.jfr != null || alias.debug != null || alias.moduleName != null
					|| Boolean.TRUE.equals(alias.cds) || Boolean.TRUE.equals(alias.interactive)
					|| Boolean.TRUE.equals(alias.enableAssertions)
					|| Boolean.TRUE.equals(alias.enableSystemAssertions)) {
				throw ExitException.invalidInput("jbang crema does not support alias JVM run options.");
			}
		}
		for (String attribute : new String[] { Project.ATTR_ADD_OPENS, Project.ATTR_ADD_EXPORTS,
				Project.ATTR_ENABLE_NATIVE_ACCESS }) {
			String value = project.getManifestAttributes().get(attribute);
			if (value != null && !value.trim().isEmpty()) {
				throw ExitException.invalidInput("jbang crema cannot apply manifest runtime attribute " + attribute);
			}
		}
		BuildContext ctx = BuildContext.forProject(project, getBuildDir());
		Project.codeBuilder(ctx).build();
		String main = buildMixin.main != null ? buildMixin.main : project.getMainClass();
		if (buildMixin.main == null && alias != null && alias.mainClass != null) {
			main = alias.mainClass;
		}
		if (main == null || main.trim().isEmpty() || main.contains("*") || main.contains("?")) {
			throw ExitException.invalidInput("jbang crema requires a concrete main class; use --main.");
		}
		List<Path> classpath = new ArrayList<>();
		classpath.add(ctx.getJarFile());
		String dependencies = ctx.resolveClassPath().getClassPath();
		if (dependencies != null && !dependencies.trim().isEmpty()) {
			for (String entry : dependencies.split(Pattern.quote(File.pathSeparator))) {
				if (!entry.isEmpty()) {
					classpath.add(Paths.get(entry));
				}
			}
		}
		List<String> arguments = new ArrayList<>();
		if (alias != null && alias.arguments != null) {
			alias.arguments.stream().map(Util::substituteRemote).forEach(arguments::add);
		}
		arguments.addAll(userParams);
		CremaRuntime.launch(classpath, main, arguments.toArray(new String[0]), project.getProperties());
		return ExitException.EXIT_OK;
	}
}
