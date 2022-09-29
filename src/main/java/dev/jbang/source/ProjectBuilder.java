package dev.jbang.source;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.jbang.source.buildsteps.JarBuildStep;
import dev.jbang.source.buildsteps.NativeBuildStep;
import dev.jbang.spi.IntegrationResult;
import dev.jbang.util.*;

public abstract class ProjectBuilder implements Builder<Project> {
	protected final Project project;

	protected boolean fresh = Util.isFresh();
	protected Util.Shell shell = Util.getShell();

	public ProjectBuilder(Project project) {
		this.project = project;
	}

	public ProjectBuilder setFresh(boolean fresh) {
		this.fresh = fresh;
		return this;
	}

	public ProjectBuilder setShell(Util.Shell shell) {
		this.shell = shell;
		return this;
	}

	@Override
	public Project build() throws IOException {
		Path outjar = project.getJarFile();
		boolean nativeBuildRequired = project.isNativeImage() && !Files.exists(project.getNativeImageFile());
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
			// We already have a Jar, check if we can still use it
			if (!project.isUpToDate()) {
				Util.verboseMsg("Building as previous build jar found but it or its dependencies not up-to-date.");
			} else if (JavaUtil.javaVersion(requestedJavaVersion) < JavaUtil.minRequestedVersion(
					project.getJavaVersion())) {
				Util.verboseMsg(
						String.format(
								"Building as requested Java version %s < than the java version used during last build %s",
								requestedJavaVersion, project.getJavaVersion()));
			} else {
				Util.verboseMsg("No build required. Reusing jar from " + project.getJarFile());
				buildRequired = false;
			}
		} else {
			Util.verboseMsg("Build required as " + outjar + " not readable or not found.");
		}

		if (buildRequired) {
			// set up temporary folder for compilation
			Path compileDir = project.getBuildDir();
			Util.deletePath(compileDir, true);
			compileDir.toFile().mkdirs();
			// do the actual building
			try {
				getCompileBuildStep().build();
				integrationResult = getIntegrationBuildStep().build();
				getJarBuildStep().build();
			} finally {
				// clean up temporary folder
				Util.deletePath(compileDir, true);
			}
		}

		if (nativeBuildRequired) {
			if (integrationResult.nativeImagePath != null) {
				Files.move(integrationResult.nativeImagePath, project.getNativeImageFile());
			} else {
				getNativeBuildStep().build();
			}
		}

		return project;
	}

	protected abstract Builder<Project> getCompileBuildStep();

	protected abstract Builder<IntegrationResult> getIntegrationBuildStep();

	protected Builder<Project> getJarBuildStep() {
		return new JarBuildStep(project);
	}

	protected Builder<Project> getNativeBuildStep() {
		return new NativeBuildStep(project);
	}
}
