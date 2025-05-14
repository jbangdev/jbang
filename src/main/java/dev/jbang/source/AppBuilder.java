package dev.jbang.source;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.jbang.source.buildsteps.IntegrationBuildStep;
import dev.jbang.source.buildsteps.JarBuildStep;
import dev.jbang.source.buildsteps.NativeBuildStep;
import dev.jbang.spi.IntegrationResult;
import dev.jbang.util.*;

/**
 * This class takes a <code>Project</code> and "builds" it. Which more precisely
 * means that it performs all the necessary steps to turn the project into
 * runnable code. (Sometimes the project is already runnable, in those cases it
 * will silently skip any unneeded steps).
 *
 * The steps it performs are called "build steps" and the most common ones are
 * (in order): "compile", "integration", "jar" and "native".
 */
public abstract class AppBuilder implements Builder<CmdGeneratorBuilder> {
	protected final BuildContext ctx;

	protected boolean fresh = Util.isFresh();
	protected Util.Shell shell = Util.getShell();

	public AppBuilder(BuildContext ctx) {
		this.ctx = ctx;
	}

	public AppBuilder setFresh(boolean fresh) {
		this.fresh = fresh;
		return this;
	}

	public AppBuilder setShell(Util.Shell shell) {
		this.shell = shell;
		return this;
	}

	@Override
	public CmdGeneratorBuilder build() throws IOException {
		Project project = ctx.getProject();
		Path outjar = ctx.getJarFile();
		boolean nativeBuildRequired = project.isNativeImage() && (!Files.exists(ctx.getNativeImageFile()) || fresh);
		IntegrationResult integrationResult = new IntegrationResult(null, null, null);
		String requestedJavaVersion = project.getJavaVersion();
		// always build the jar for native mode
		// it allows integrations the options to produce the native image
		boolean buildRequired = true;
		if (project.isJar()) {
			Util.verboseMsg("The resource is a jar, no compilation to be done.");
			buildRequired = false;
		} else if (fresh) {
			Util.verboseMsg("Building as fresh build explicitly requested.");
		} else if (nativeBuildRequired) {
			Util.verboseMsg("Building as native build required.");
		} else if (Files.isReadable(outjar)) {
			Project jarProject = Project.builder().build(outjar);
			// We already have a Jar, check if we can still use it
			if (!ctx.isUpToDate()) {
				Util.verboseMsg("Building as previously built jar found but it or its dependencies not up-to-date.");
			} else if (jarProject.getJavaVersion() == null) {
				Util.verboseMsg("Building as previously built jar found but it has incomplete meta data.");
			} else if (project.projectJdk().majorVersion() < JavaUtil.minRequestedVersion(
					jarProject.getJavaVersion())) {
				Util.verboseMsg(
						String.format(
								"Building as requested Java version %s < than the java version used during last build %s",
								requestedJavaVersion, project.getJavaVersion()));
			} else {
				if (project.getMainClass() == null) {
					project.setMainClass(jarProject.getMainClass());
				}
				if (!project.getModuleName().isPresent()) {
					project.setModuleName(jarProject.getModuleName().orElse(null));
				}
				Util.verboseMsg("No build required. Reusing jar from " + ctx.getJarFile());
				buildRequired = false;
			}
		} else {
			Util.verboseMsg("Build required as " + outjar + " not readable or not found.");
		}

		if (buildRequired) {
			// set up temporary folders for compilation
			Path compileDir = ctx.getCompileDir();
			Util.deletePath(compileDir, true);
			compileDir.toFile().mkdirs();
			Path generatedDir = ctx.getGeneratedSourcesDir();
			Util.deletePath(generatedDir, true);
			generatedDir.toFile().mkdirs();

			// do the actual building
			try {
				getCompileBuildStep().build();
				if (!project.disableIntegrations()) {
					integrationResult = getIntegrationBuildStep().build();
				}
				getJarBuildStep().build();
			} finally {
				if (!keepClasses()) {
					// clean up temporary folders
					Util.deletePath(compileDir, true);
					Util.deletePath(generatedDir, true);
				}
			}
		}

		if (nativeBuildRequired) {
			if (integrationResult.nativeImagePath != null) {
				Files.move(integrationResult.nativeImagePath, ctx.getNativeImageFile());
			} else {
				getNativeBuildStep().build();
			}
		}

		return CmdGenerator.builder(ctx)
			.mainClass(project.getMainClass())
			.moduleName(project.getModuleName().orElse(null));
	}

	public static boolean keepClasses() {
		return "true".equals(System.getProperty("jbang.build.keepclasses"));
	}

	protected abstract Builder<Project> getCompileBuildStep();

	protected Builder<IntegrationResult> getIntegrationBuildStep() {
		return new IntegrationBuildStep(ctx);
	}

	protected Builder<Project> getJarBuildStep() {
		return new JarBuildStep(ctx);
	}

	protected Builder<Project> getNativeBuildStep() {
		return new NativeBuildStep(ctx);
	}
}
