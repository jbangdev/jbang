package dev.jbang.source.buildsteps;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import dev.jbang.source.AppBuilder;
import dev.jbang.source.BuildContext;
import dev.jbang.source.Builder;
import dev.jbang.source.Project;
import dev.jbang.util.JarUtil;

/**
 * This class takes a <code>Project</code> and the result from a previous
 * "compile" step and packages the whole into a JAR file.
 */
public class JarBuildStep implements Builder<Project> {
	private final BuildContext ctx;

	public static final String ATTR_BUILD_JDK = "Build-Jdk";
	public static final String ATTR_JBANG_JAVA_OPTIONS = "JBang-Java-Options";
	public static final String ATTR_BOOT_CLASS_PATH = "Boot-Class-Path";

	public JarBuildStep(BuildContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public Project build() throws IOException {
		Project project = ctx.getProject();
		createJar(project, ctx.getCompileDir(), ctx.getJarFile());
		return project;
	}

	public static void createJar(Project prj, Path compileDir, Path jarFile) throws IOException {
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

		prj.getManifestAttributes().forEach((k, v) -> manifest.getMainAttributes().putValue(k, v));

		int buildJdk = prj.projectJdk().majorVersion();
		if (buildJdk > 0) {
			String val = buildJdk >= 9 ? Integer.toString(buildJdk) : "1." + buildJdk;
			manifest.getMainAttributes().putValue(ATTR_BUILD_JDK, val);
		}

		JarUtil.createJar(jarFile, compileDir, manifest, prj.getMainClass(), prj.projectJdk());

		if (AppBuilder.keepClasses()) {
			// In the case the "keep classes" option is specified we write
			// an extra copy if the manifest to its proper location.
			// This file is never used but is there so the user can take
			// a look at it and know what its contents are
			String mainclass = prj.getMainClass();
			if (mainclass != null) {
				manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainclass);
			}
			Path mf = compileDir.resolve("META-INF/MANIFEST.MF");
			try (OutputStream os = Files.newOutputStream(mf)) {
				manifest.write(os);
			}
		}
	}
}
