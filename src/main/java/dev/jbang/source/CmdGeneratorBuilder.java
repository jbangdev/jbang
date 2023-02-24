package dev.jbang.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import dev.jbang.catalog.Alias;
import dev.jbang.source.generators.JarCmdGenerator;
import dev.jbang.source.generators.JshCmdGenerator;
import dev.jbang.source.generators.NativeCmdGenerator;
import dev.jbang.source.resolvers.AliasResourceResolver;

public class CmdGeneratorBuilder {
	private final Project project;
	private final BuildContext ctx;

	private List<String> arguments = Collections.emptyList();
	private List<String> runtimeOptions = Collections.emptyList();

	private String mainClass;
	private boolean interactive;
	private boolean enableAssertions;
	private boolean enableSystemAssertions;
	private String flightRecorderString;
	private String debugString;
	private Boolean classDataSharing;

	CmdGeneratorBuilder(Project project, BuildContext ctx) {
		this.project = project;
		this.ctx = ctx;
	}

	// TODO Try to get rid of this getter
	public List<String> getArguments() {
		return Collections.unmodifiableList(arguments);
	}

	public CmdGeneratorBuilder setArguments(List<String> arguments) {
		if (arguments != null) {
			this.arguments = new ArrayList<>(arguments);
		} else {
			this.arguments = Collections.emptyList();
		}
		return this;
	}

	public CmdGeneratorBuilder runtimeOptions(List<String> runtimeOptions) {
		if (runtimeOptions != null) {
			this.runtimeOptions = runtimeOptions;
		} else {
			this.runtimeOptions = Collections.emptyList();
		}
		return this;
	}

	public CmdGeneratorBuilder mainClass(String mainClass) {
		this.mainClass = mainClass;
		return this;
	}

	public CmdGeneratorBuilder interactive(boolean interactive) {
		this.interactive = interactive;
		return this;
	}

	public CmdGeneratorBuilder enableAssertions(boolean enableAssertions) {
		this.enableAssertions = enableAssertions;
		return this;
	}

	public CmdGeneratorBuilder enableSystemAssertions(boolean enableSystemAssertions) {
		this.enableSystemAssertions = enableSystemAssertions;
		return this;
	}

	public CmdGeneratorBuilder flightRecorderString(String flightRecorderString) {
		this.flightRecorderString = flightRecorderString;
		return this;
	}

	public CmdGeneratorBuilder debugString(String debugString) {
		this.debugString = debugString;
		return this;
	}

	public CmdGeneratorBuilder classDataSharing(Boolean classDataSharing) {
		this.classDataSharing = classDataSharing;
		return this;
	}

	public CmdGenerator build() {
		// If the project was created from an Alias, it might
		// have some values we need to update
		if (project.getResourceRef() instanceof AliasResourceResolver.AliasedResourceRef) {
			Alias alias = ((AliasResourceResolver.AliasedResourceRef) project.getResourceRef()).getAlias();
			updateFromAlias(alias);
		}
		CmdGenerator gen;
		if (project.isJShell() || interactive) {
			gen = createJshCmdGenerator();
		} else {
			if (Boolean.TRUE.equals(project.isNativeImage())) {
				gen = createNativeCmdGenerator();
			} else {
				gen = createJarCmdGenerator();
			}
		}
		return gen;
	}

	private JarCmdGenerator createJarCmdGenerator() {
		return new JarCmdGenerator(project, ctx)
												.arguments(arguments)
												.runtimeOptions(runtimeOptions)
												.mainClass(mainClass)
												.mainRequired(!interactive)
												.assertions(enableAssertions)
												.systemAssertions(enableSystemAssertions)
												.classDataSharing(
														Optional.ofNullable(classDataSharing).orElse(false))
												.debugString(debugString)
												.flightRecorderString(flightRecorderString);
	}

	private JshCmdGenerator createJshCmdGenerator() {
		return new JshCmdGenerator(project, ctx)
												.arguments(arguments)
												.runtimeOptions(runtimeOptions)
												.mainClass(mainClass)
												.interactive(interactive)
												.debugString(debugString)
												.flightRecorderString(flightRecorderString);
	}

	private NativeCmdGenerator createNativeCmdGenerator() {
		return new NativeCmdGenerator(project, ctx, createJarCmdGenerator())
																			.arguments(arguments);
	}

	private void updateFromAlias(Alias alias) {
		if (arguments.isEmpty()) {
			setArguments(alias.arguments);
		}
		if (runtimeOptions.isEmpty()) {
			runtimeOptions(alias.runtimeOptions);
		}
		if (mainClass == null) {
			mainClass(alias.mainClass);
		}
	}
}
