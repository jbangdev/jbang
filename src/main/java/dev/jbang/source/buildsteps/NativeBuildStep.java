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

/**
 * This class takes a <code>Project</code> and the result from a previous
 * packaging (ie "jar") step and tries to run the native image compiler to turn
 * it into a native application.
 */
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
		optionList.addAll(project.getMainSourceSet().getNativeOptions());

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
		Util.verboseMsg("native-image: " + String.join(" ", optionList));

		ProcessBuilder pb = CommandBuffer	.of(optionList)
											.asProcessBuilder()
											.inheritIO();

		// Redirect the output of the native builder to a file
		Path nilog = Files.createTempFile("jbang", "native-image");
		Util.infoMsg("log: " + nilog.toString());
		pb.redirectOutput(nilog.toFile());

		Process process = pb.start();
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
