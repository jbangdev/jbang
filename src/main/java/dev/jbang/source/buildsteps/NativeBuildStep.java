package dev.jbang.source.buildsteps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import dev.jbang.cli.ExitException;
import dev.jbang.source.Builder;
import dev.jbang.source.Project;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

public class NativeBuildStep implements Builder<Project> {
	private final Project project;

	public NativeBuildStep(Project project) {
		this.project = project;
	}

	public Project build() throws IOException {
		List<String> optionList = new ArrayList<>();
		optionList.add(resolveInGraalVMHome("native-image", project.getJavaVersion()));

		optionList.add("-H:+ReportExceptionStackTraces");

		optionList.add("--enable-https");

		String classpath = project.resolveClassPath().getClassPath();
		if (!Util.isBlankString(classpath)) {
			optionList.add("--class-path=" + classpath);
		}

		optionList.add("-jar");
		optionList.add(project.getJarFile().toString());

		optionList.add(getNativeImageOutputName().toString());

		runNativeBuilder(optionList);

		return project;
	}

	/**
	 * Based on the jar path this will return the path for the output file to be
	 * passed to the native-image compiler. NB: On Windows the compiler always adds
	 * `.exe` to the name!
	 */
	private Path getNativeImageOutputName() {
		Path image = project.getNativeImageFile();
		if (Util.isWindows()) {
			// NB: On Windows the compiler always adds `.exe` to the name!
			// So we remove the extension from the name before returning it
			image = image.getParent().resolve(Util.base(image.getFileName().toString()));
		}
		return image;
	}

	protected void runNativeBuilder(List<String> optionList) throws IOException {
		Path nilog = Files.createTempFile("jbang", "native-image");
		Util.verboseMsg("native-image: " + String.join(" ", optionList));
		Util.infoMsg("log: " + nilog.toString());

		Process process = CommandBuffer	.of(optionList)
										.asProcessBuilder()
										.inheritIO()
										.redirectOutput(nilog.toFile())
										.start();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			throw new ExitException(1, e);
		}

		if (process.exitValue() != 0) {
			throw new ExitException(1, "Error during native-image");
		}
	}

	private static String resolveInGraalVMHome(String cmd, String requestedVersion) {
		String newcmd = resolveInEnv("GRAALVM_HOME", cmd);

		if (newcmd.equals(cmd) &&
				!new File(newcmd).exists()) {
			return JavaUtil.resolveInJavaHome(cmd, requestedVersion);
		} else {
			return newcmd;
		}
	}

	private static String resolveInEnv(String env, String cmd) {
		if (System.getenv(env) != null) {
			Path dir = Paths.get(System.getenv(env)).toAbsolutePath().resolve("bin");
			Path cmdPath = Util.searchPath(cmd, dir.toString());
			return cmdPath != null ? cmdPath.toString() : cmd;
		} else {
			return cmd;
		}
	}
}
