package dev.jbang.source.buildsteps;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import dev.jbang.source.AppBuilder;
import dev.jbang.source.Builder;
import dev.jbang.source.Project;
import dev.jbang.util.CommandBuffer;
import dev.jbang.util.JarUtil;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

/**
 * This class takes a <code>Project</code> and the result from a previous
 * "compile" step and packages the whole into a JAR file.
 */
public class JarBuildStep implements Builder<Project> {
	private final Project project;

	public static final String ATTR_BUILD_JDK = "Build-Jdk";
	public static final String ATTR_JBANG_JAVA_OPTIONS = "JBang-Java-Options";
	public static final String ATTR_BOOT_CLASS_PATH = "Boot-Class-Path";

	public JarBuildStep(Project project) {
		this.project = project;
	}

	@Override
	public Project build() throws IOException {
		createJar(project, project.getBuildDir(), project.getJarFile());
		return project;
	}

	public static void createJar(Project prj, Path compileDir, Path jarFile) throws IOException {
		String mainclass = prj.getMainClass();
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		if (mainclass != null) {
			manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainclass);
		}

		prj.getManifestAttributes().forEach((k, v) -> manifest.getMainAttributes().putValue(k, v));

		// When persistent JVM args are set they are appended to any runtime
		// options set on the Source (that way persistent args can override
		// options set on the Source)
		List<String> rtArgs = prj.getRuntimeOptions();
		String runtimeOpts = CommandBuffer.of(rtArgs).asCommandLine(Util.Shell.bash);
		// TODO should be removed
		// if (!runtimeOpts.isEmpty()) {
		// manifest.getMainAttributes()
		// .putValue(ATTR_JBANG_JAVA_OPTIONS, runtimeOpts);
		// }
		int buildJdk = JavaUtil.javaVersion(prj.getJavaVersion());
		if (buildJdk > 0) {
			String val = buildJdk >= 9 ? Integer.toString(buildJdk) : "1." + buildJdk;
			manifest.getMainAttributes().putValue(ATTR_BUILD_JDK, val);
		}

		FileOutputStream target = new FileOutputStream(jarFile.toFile());
		JarUtil.jar(target, compileDir.toFile().listFiles(), null, null, manifest);
		target.close();

		if (AppBuilder.keepClasses()) {
			// In the case the "keep classes" option is specified we write
			// an extra copy if the manifest to its proper location.
			// This file is never used but is there so the user can take
			// a look at it and know what its contents are
			Path mf = compileDir.resolve("META-INF/MANIFEST.MF");
			try (OutputStream os = Files.newOutputStream(mf)) {
				manifest.write(os);
			}
		}
	}
}
