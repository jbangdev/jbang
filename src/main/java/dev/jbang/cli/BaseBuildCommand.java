package dev.jbang.cli;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.Type;

import dev.jbang.TemplateEngine;
import dev.jbang.build.JarSource;
import dev.jbang.build.RunContext;
import dev.jbang.build.ScriptSource;
import dev.jbang.build.Source;
import dev.jbang.net.JdkManager;
import dev.jbang.spi.IntegrationManager;
import dev.jbang.spi.IntegrationResult;
import dev.jbang.util.JarUtil;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;
import picocli.CommandLine;

public abstract class BaseBuildCommand extends BaseScriptDepsCommand {
	public static final Type STRINGARRAYTYPE = Type.create(DotName.createSimple("[Ljava.lang.String;"),
			Type.Kind.ARRAY);
	public static final Type STRINGTYPE = Type.create(DotName.createSimple("java.lang.String"), Type.Kind.CLASS);
	public static final Type INSTRUMENTATIONTYPE = Type.create(
			DotName.createSimple("java.lang.instrument.Instrumentation"), Type.Kind.CLASS);
	protected String javaVersion;

	@CommandLine.Option(names = { "-m",
			"--main" }, description = "Main class to use when running. Used primarily for running jar's.")
	String main;

	@CommandLine.Option(names = { "-j",
			"--java" }, description = "JDK version to use for running the script.")
	void setJavaVersion(String javaVersion) {
		if (!javaVersion.matches("\\d+[+]?")) {
			throw new IllegalArgumentException(
					"Invalid version, should be a number optionally followed by a plus sign");
		}
		this.javaVersion = javaVersion;
	}

	@CommandLine.Option(names = {
			"--cds" }, description = "If specified Class Data Sharing (CDS) will be used for building and running (requires Java 13+)", negatable = true)
	Boolean cds;

	Optional<Boolean> cds() {
		return Optional.ofNullable(cds);
	}

	@CommandLine.Option(names = { "-D" }, description = "set a system property", mapFallbackValue = "true")
	Map<String, String> properties = new HashMap<>();

	@CommandLine.Option(names = {
			"-n", "--native" }, description = "Build using native-image", defaultValue = "false")
	boolean nativeImage;

	@CommandLine.Option(names = {
			"-f",
			"--fresh" }, description = "Make it a fresh run - i.e. a new build with fresh (i.e. non-cached) resources.", defaultValue = "false")
	boolean fresh;

	PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out));

	protected boolean createdJar;

	Source buildIfNeeded(Source src, RunContext ctx) throws IOException {
		if (needsJar(src, ctx)) {
			src = build((ScriptSource) src, ctx);
		}
		return src;
	}

	Source build(ScriptSource src, RunContext ctx) throws IOException {
		Source result = src;

		for (Map.Entry<String, String> entry : properties.entrySet()) {
			System.setProperty(entry.getKey(), entry.getValue());
		}

		File outjar = src.getJarFile();
		boolean nativeBuildRequired = nativeImage && !getImageName(outjar).exists();
		IntegrationResult integrationResult = new IntegrationResult(null, null, null);
		String requestedJavaVersion = javaVersion != null ? javaVersion : src.javaVersion();
		// always build the jar for native mode
		// it allows integrations the options to produce the native image
		boolean buildRequired = fresh || nativeBuildRequired;
		if (!buildRequired && outjar.canRead()) {
			// We already have a Jar, check if we can still use it
			JarSource jarSrc = src.asJarSource();
			if (jarSrc == null || JavaUtil.javaVersion(requestedJavaVersion) < jarSrc.getBuildJdk()) {
				buildRequired = true;
			} else {
				result = jarSrc;
			}
		} else {
			buildRequired = true;
		}
		if (buildRequired) {
			// set up temporary folder for compilation
			File tmpJarDir = new File(outjar.getParentFile(), outjar.getName() + ".tmp");
			Util.deletePath(tmpJarDir.toPath(), true);
			tmpJarDir.mkdirs();
			// do the actual building
			try {
				integrationResult = buildJar(src, ctx, tmpJarDir, outjar, requestedJavaVersion);
			} finally {
				// clean up temporary folder
				Util.deletePath(tmpJarDir.toPath(), true);
			}
		}

		if (nativeBuildRequired) {
			if (integrationResult.nativeImagePath != null) {
				Files.move(integrationResult.nativeImagePath, getImageName(outjar).toPath());
			} else {
				buildNative(src, ctx, outjar, requestedJavaVersion);
			}
		}

		return result;
	}

	// build with javac and then jar... todo: split up in more testable chunks
	private IntegrationResult buildJar(ScriptSource src, RunContext ctx, File tmpJarDir, File outjar,
			String requestedJavaVersion)
			throws IOException {
		IntegrationResult integrationResult;
		List<String> optionList = new ArrayList<>();
		optionList.add(resolveInJavaHome("javac", requestedJavaVersion));
		optionList.addAll(src.getCompileOptions());
		String path = ctx.resolveClassPath(src, offline);
		if (!path.trim().isEmpty()) {
			optionList.addAll(Arrays.asList("-classpath", path));
		}
		optionList.addAll(Arrays.asList("-d", tmpJarDir.getAbsolutePath()));

		// add source files to compile
		optionList.add(src.getResourceRef().getFile().getPath());
		optionList.addAll(
				src
					.getAllSources()
					.stream()
					.map(x -> x.getResourceRef().getFile().getPath())
					.collect(Collectors.toList()));

		// add additional files
		src.copyFilesTo(tmpJarDir.toPath(), fresh);

		Template pomTemplate = TemplateEngine.instance().getTemplate("pom.qute.xml");

		Path pomPath = null;
		if (pomTemplate == null) {
			// ignore
			Util.warnMsg("Could not locate pom.xml template");
		} else {
			String pomfile = pomTemplate
										.data("baseName", Util.getBaseName(src.getResourceRef().getFile().getName()))
										.data("dependencies", ctx.getClassPath().getArtifacts())
										.render();
			pomPath = new File(tmpJarDir, "META-INF/maven/g/a/v/pom.xml").toPath();
			Files.createDirectories(pomPath.getParent());
			Util.writeString(pomPath, pomfile);
		}

		info("Building jar...");
		debug("compile: " + String.join(" ", optionList));

		Process process = new ProcessBuilder(optionList).inheritIO().start();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			throw new ExitException(1, e);
		}

		if (process.exitValue() != 0) {
			throw new ExitException(1, "Error during compile");
		}

		ctx.setBuildJdk(JavaUtil.javaVersion(requestedJavaVersion));
		integrationResult = IntegrationManager.runIntegration(src.getAllRepositories(),
				ctx.getClassPath().getArtifacts(),
				tmpJarDir.toPath(), pomPath,
				src, nativeImage);
		if (integrationResult.mainClass != null) {
			ctx.setMainClass(integrationResult.mainClass);
		} else {
			try {
				// using Files.walk method with try-with-resources
				try (Stream<Path> paths = Files.walk(tmpJarDir.toPath())) {
					List<Path> items = paths.filter(Files::isRegularFile)
											.filter(f -> !f.toFile().getName().contains("$"))
											.filter(f -> f.toFile().getName().endsWith(".class"))
											.collect(Collectors.toList());

					if (items.size() > 1) { // todo: this feels like a very sketchy way to find the proper class
											// name
						// but it works.
						String mainname = src.getResourceRef().getFile().getName().replace(".java", ".class");
						items = items	.stream()
										.filter(f -> f.toFile().getName().equalsIgnoreCase(mainname))
										.collect(Collectors.toList());
					}

					if (items.size() != 1) {
						throw new ExitException(1,
								"Could not locate unique class. Found " + items.size() + " candidates.");
					} else {
						Path classfile = items.get(0);
						// TODO: could we use jandex to find the right main class more sanely ?
						// String mainClass = findMainClass(tmpJarDir.toPath(), classfile);

						Indexer indexer = new Indexer();
						InputStream stream = new FileInputStream(classfile.toFile());
						indexer.index(stream);
						Index index = indexer.complete();

						Collection<ClassInfo> clazz = index.getKnownClasses();

						Optional<ClassInfo> main = clazz.stream()
														.filter(pubClass -> pubClass.method("main",
																STRINGARRAYTYPE) != null)
														.findFirst();

						if (main.isPresent()) {
							ctx.setMainClass(main.get().name().toString());
						}

						if (src.isAgent()) {

							Optional<ClassInfo> agentmain = clazz	.stream()
																	.filter(pubClass -> pubClass.method("agentmain",
																			STRINGTYPE,
																			INSTRUMENTATIONTYPE) != null
																			||
																			pubClass.method("agentmain",
																					STRINGTYPE) != null)
																	.findFirst();

							if (agentmain.isPresent()) {
								ctx.setAgentMainClass(agentmain.get().name().toString());
							}

							Optional<ClassInfo> premain = clazz	.stream()
																.filter(pubClass -> pubClass.method("premain",
																		STRINGTYPE,
																		INSTRUMENTATIONTYPE) != null
																		||
																		pubClass.method("premain",
																				STRINGTYPE) != null)
																.findFirst();

							if (premain.isPresent()) {
								ctx.setPreMainClass(premain.get().name().toString());
							}
						}

					}
				}
			} catch (IOException e) {
				throw new ExitException(1, e);
			}
		}
		ctx.setPersistentJvmArgs(integrationResult.javaArgs);
		createJarFile(src, ctx, tmpJarDir, outjar);
		createdJar = true;
		return integrationResult;
	}

	static void createJarFile(ScriptSource src, RunContext ctx, File path, File output) throws IOException {
		String mainclass = ctx.getMainClassOr(src);
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		if (mainclass != null) {
			manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainclass);
		}

		if (src.isAgent()) {
			if (ctx.getPreMainClass() != null) {
				manifest.getMainAttributes().put(new Attributes.Name(Source.ATTR_PREMAIN_CLASS), ctx.getPreMainClass());
			}
			if (ctx.getAgentMainClass() != null) {
				manifest.getMainAttributes().put(new Attributes.Name(Source.ATTR_AGENT_CLASS), ctx.getAgentMainClass());
			}

			for (ScriptSource.KeyValue kv : src.getAllAgentOptions()) {
				if (kv.getKey().trim().isEmpty()) {
					continue;
				}
				Attributes.Name k = new Attributes.Name(kv.getKey());
				String v = kv.getValue() == null ? "true" : kv.getValue();
				manifest.getMainAttributes().put(k, v);
			}

			if (ctx.getClassPath() != null) {
				String bootClasspath = ctx.getClassPath().getManifestPath();
				if (!bootClasspath.isEmpty()) {
					manifest.getMainAttributes().put(new Attributes.Name(Source.ATTR_BOOT_CLASS_PATH), bootClasspath);
				}
			}
		} else {
			if (ctx.getClassPath() != null) {
				String classpath = ctx.getClassPath().getManifestPath();
				if (!classpath.isEmpty()) {
					manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classpath);
				}
			}
		}

		if (ctx.getPersistentJvmArgs() != null) {
			manifest.getMainAttributes()
					.putValue(Source.ATTR_JBANG_JAVA_OPTIONS, String.join(" ", ctx.getPersistentJvmArgs()));
		}
		int buildJdk = ctx.getBuildJdk();
		if (buildJdk > 0) {
			String val = buildJdk >= 9 ? Integer.toString(buildJdk) : "1." + buildJdk;
			manifest.getMainAttributes().putValue(Source.ATTR_BUILD_JDK, val);
		}

		FileOutputStream target = new FileOutputStream(output);
		JarUtil.jar(target, path.listFiles(), null, null, manifest);
		target.close();
	}

	private void buildNative(Source src, RunContext ctx, File outjar, String requestedJavaVersion) throws IOException {
		List<String> optionList = new ArrayList<>();
		optionList.add(resolveInGraalVMHome("native-image", requestedJavaVersion));

		optionList.add("-H:+ReportExceptionStackTraces");

		optionList.add("--enable-https");

		String classpath = ctx.resolveClassPath(src, offline);
		if (!classpath.trim().isEmpty()) {
			optionList.add("--class-path=" + classpath);
		}

		optionList.add("-jar");
		optionList.add(outjar.toString());

		optionList.add(getImageName(outjar).toString());

		File nilog = File.createTempFile("jbang", "native-image");
		debug("native-image: " + String.join(" ", optionList));
		info("log: " + nilog.toString());

		Process process = new ProcessBuilder(optionList).inheritIO().redirectOutput(nilog).start();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			throw new ExitException(1, e);
		}

		if (process.exitValue() != 0) {
			throw new ExitException(1, "Error during native-image");
		}
	}

	/** based on jar what will the binary image name be. **/
	static protected File getImageName(File outjar) {
		if (Util.isWindows()) {
			return new File(outjar.toString() + ".exe");
		} else {
			return new File(outjar.toString() + ".bin");
		}
	}

	static public String findMainClass(Path base, Path classfile) {
		StringBuilder mainClass = new StringBuilder(classfile.getFileName().toString().replace(".class", ""));
		while (!classfile.getParent().equals(base)) {
			classfile = classfile.getParent();
			mainClass.insert(0, classfile.getFileName().toString() + ".");
		}
		return mainClass.toString();
	}

	protected static String resolveInJavaHome(String cmd, String requestedVersion) {
		Path jdkHome = JdkManager.getCurrentJdk(requestedVersion);
		if (jdkHome != null) {
			if (Util.isWindows()) {
				cmd = cmd + ".exe";
			}
			return jdkHome.resolve("bin").resolve(cmd).toAbsolutePath().toString();
		}
		return cmd;
	}

	private static String resolveInGraalVMHome(String cmd, String requestedVersion) {
		String newcmd = resolveInEnv("GRAALVM_HOME", cmd);

		if (newcmd.equals(cmd) &&
				!new File(newcmd).exists()) {
			return resolveInJavaHome(cmd, requestedVersion);
		} else {
			return newcmd;
		}
	}

	private static String resolveInEnv(String env, String cmd) {
		if (System.getenv(env) != null) {
			if (Util.isWindows()) {
				cmd = cmd + ".exe";
			}
			return new File(System.getenv(env)).toPath().resolve("bin").resolve(cmd).toAbsolutePath().toString();
		} else {
			return cmd;
		}
	}

}
