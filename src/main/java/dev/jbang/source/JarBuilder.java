package dev.jbang.source;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.jandex.*;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.DependencyUtil;
import dev.jbang.net.JdkManager;
import dev.jbang.source.scripts.GroovyScript;
import dev.jbang.spi.IntegrationManager;
import dev.jbang.spi.IntegrationResult;
import dev.jbang.util.JarUtil;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;

public class JarBuilder implements Builder {
	private boolean fresh = Util.isFresh();
	private Util.Shell shell = Util.getShell();

	public static final Type STRINGARRAYTYPE = Type.create(DotName.createSimple("[Ljava.lang.String;"),
			Type.Kind.ARRAY);
	public static final Type STRINGTYPE = Type.create(DotName.createSimple("java.lang.String"), Type.Kind.CLASS);
	public static final Type INSTRUMENTATIONTYPE = Type.create(
			DotName.createSimple("java.lang.instrument.Instrumentation"), Type.Kind.CLASS);

	public JarBuilder setFresh(boolean fresh) {
		this.fresh = fresh;
		return this;
	}

	public JarBuilder setShell(Util.Shell shell) {
		this.shell = shell;
		return this;
	}

	@Override
	public Jar build(SourceSet ss, RunContext ctx) throws IOException {
		Jar result = null;

		File outjar = ss.getJarFile();
		boolean nativeBuildRequired = ctx.isNativeImage() && !getImageName(outjar).exists();
		IntegrationResult integrationResult = new IntegrationResult(null, null, null);
		String requestedJavaVersion = ctx.getJavaVersion() != null ? ctx.getJavaVersion()
				: ss.getJavaVersion().orElse(null);
		// always build the jar for native mode
		// it allows integrations the options to produce the native image
		boolean buildRequired = true;
		if (fresh) {
			Util.verboseMsg("Building as fresh build explicitly requested.");
		} else if (nativeBuildRequired) {
			Util.verboseMsg("Building as native build required.");
		} else if (outjar.canRead()) {
			// We already have a Jar, check if we can still use it
			Jar jarSrc = ss.asJar();

			if (jarSrc == null) {
				Util.verboseMsg("Building as previous built jar not found.");
			} else if (!jarSrc.isUpToDate()) {
				Util.verboseMsg("Building as previous build jar found but it or its dependencies not up-to-date.");
			} else if (JavaUtil.javaVersion(requestedJavaVersion) < JavaUtil.minRequestedVersion(
					jarSrc.getJavaVersion().orElse(null))) {
				Util.verboseMsg(
						String.format(
								"Building as requested Java version %s < than the java version used during last build %s",
								requestedJavaVersion, jarSrc.getJavaVersion()));
			} else {
				Util.verboseMsg("No build required. Reusing jar from " + jarSrc.getJarFile());
				result = (Jar) ctx.importJarMetadataFor(jarSrc);
				buildRequired = false;
			}
		} else {
			Util.verboseMsg("Build required as " + outjar + " not readable or not found.");
		}

		if (buildRequired) {
			// set up temporary folder for compilation
			File tmpJarDir = new File(outjar.getParentFile(), outjar.getName() + ".tmp");
			Util.deletePath(tmpJarDir.toPath(), true);
			tmpJarDir.mkdirs();
			// do the actual building
			try {
				integrationResult = buildJar(ss, ctx, tmpJarDir, outjar, requestedJavaVersion);
				result = ss.asJar();
			} finally {
				// clean up temporary folder
				Util.deletePath(tmpJarDir.toPath(), true);
			}
		}

		if (nativeBuildRequired) {
			if (integrationResult.nativeImagePath != null) {
				Files.move(integrationResult.nativeImagePath, getImageName(outjar).toPath());
			} else {
				buildNative(ss, ctx, outjar, requestedJavaVersion);
			}
		}

		return result;
	}

	// build with javac and then jar... todo: split up in more testable chunks
	public IntegrationResult buildJar(SourceSet ss, RunContext ctx, File tmpJarDir, File outjar,
			String requestedJavaVersion) throws IOException {
		IntegrationResult integrationResult;
		List<String> optionList = new ArrayList<>();
		optionList.add(ss.getMainSource().getCompilerBinary(requestedJavaVersion));
		optionList.addAll(ss.getCompileOptions());
		String path = ss.getClassPath().getClassPath();
		if (!Util.isBlankString(path)) {
			optionList.addAll(Arrays.asList("-classpath", path));
		}
		optionList.addAll(Arrays.asList("-d", tmpJarDir.getAbsolutePath()));

		// add source files to compile
		optionList.addAll(ss.getSources()
							.stream()
							.map(x -> x.getResourceRef().getFile().getPath())
							.collect(Collectors.toList()));

		// add additional files
		ss.copyResourcesTo(tmpJarDir.toPath());

		Path pomPath = generatePom(ss, ctx, tmpJarDir);

		Util.infoMsg("Building jar...");
		Util.verboseMsg("compile: " + String.join(" ", optionList));

		runCompiler(ss, requestedJavaVersion, optionList);

		ctx.setBuildJdk(JavaUtil.javaVersion(requestedJavaVersion));
		// todo: setting properties to avoid loosing properties in integration call.
		Properties old = System.getProperties();
		Properties temporay = new Properties(System.getProperties());
		for (Map.Entry<String, String> entry : ctx.getProperties().entrySet()) {
			System.setProperty(entry.getKey(), entry.getValue());
		}
		integrationResult = IntegrationManager.runIntegration(ss.getRepositories(),
				ss.getClassPath().getArtifacts(),
				tmpJarDir.toPath(), pomPath,
				ss.getMainSource(), ctx.isNativeImage());
		System.setProperties(old);

		if (ctx.getMainClass() == null) { // if non-null user forced set main
			if (integrationResult.mainClass != null) {
				ctx.setMainClass(integrationResult.mainClass);
			} else {
				searchForMain(ss.getMainSource(), ctx, tmpJarDir);
			}
		}
		ctx.setIntegrationOptions(integrationResult.javaArgs);
		createJarFile(ss, ctx, tmpJarDir, outjar);
		return integrationResult;
	}

	protected void runCompiler(SourceSet ss, String requestedJavaVersion, List<String> optionList) throws IOException {
		final ProcessBuilder processBuilder = new ProcessBuilder(optionList).inheritIO();
		if (ss.getMainSource() instanceof GroovyScript) {
			processBuilder.environment().put("JAVA_HOME", JdkManager.getCurrentJdk(requestedJavaVersion).toString());
			processBuilder.environment().remove("GROOVY_HOME");
		}
		Process process = processBuilder.start();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			throw new ExitException(1, e);
		}

		if (process.exitValue() != 0) {
			throw new ExitException(1, "Error during compile");
		}
	}

	public void createJarFile(SourceSet ss, RunContext ctx, File path, File output) throws IOException {
		String mainclass = ctx.getMainClassOr(ss);
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		if (mainclass != null) {
			manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainclass);
		}

		if (ss.getMainSource().isAgent()) {
			if (ctx.getPreMainClass() != null) {
				manifest.getMainAttributes().put(new Attributes.Name(Input.ATTR_PREMAIN_CLASS), ctx.getPreMainClass());
			}
			if (ctx.getAgentMainClass() != null) {
				manifest.getMainAttributes().put(new Attributes.Name(Input.ATTR_AGENT_CLASS), ctx.getAgentMainClass());
			}

			for (KeyValue kv : ss.getAgentOptions()) {
				if (Util.isBlankString(kv.getKey())) {
					continue;
				}
				Attributes.Name k = new Attributes.Name(kv.getKey());
				String v = kv.getValue() == null ? "true" : kv.getValue();
				manifest.getMainAttributes().put(k, v);
			}

			String bootClasspath = ss.getClassPath().getManifestPath();
			if (!bootClasspath.isEmpty()) {
				manifest.getMainAttributes().put(new Attributes.Name(Input.ATTR_BOOT_CLASS_PATH), bootClasspath);
			}
		} else {
			String classpath = ss.getClassPath().getManifestPath();
			if (!classpath.isEmpty()) {
				manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classpath);
			}
		}

		// When persistent JVM args are set they are appended to any runtime
		// options set on the Source (that way persistent args can override
		// options set on the Source)
		List<String> rtArgs = ctx.getRuntimeOptionsMerged(ss);
		String runtimeOpts = String.join(" ", escapeArguments(rtArgs));
		if (!runtimeOpts.isEmpty()) {
			manifest.getMainAttributes()
					.putValue(Input.ATTR_JBANG_JAVA_OPTIONS, runtimeOpts);
		}
		int buildJdk = ctx.getBuildJdk();
		if (buildJdk > 0) {
			String val = buildJdk >= 9 ? Integer.toString(buildJdk) : "1." + buildJdk;
			manifest.getMainAttributes().putValue(Input.ATTR_BUILD_JDK, val);
		}

		FileOutputStream target = new FileOutputStream(output);
		JarUtil.jar(target, path.listFiles(), null, null, manifest);
		target.close();
	}

	protected void buildNative(SourceSet ss, RunContext ctx, File outjar, String requestedJavaVersion)
			throws IOException {
		List<String> optionList = new ArrayList<>();
		optionList.add(resolveInGraalVMHome("native-image", requestedJavaVersion));

		optionList.add("-H:+ReportExceptionStackTraces");

		optionList.add("--enable-https");

		String classpath = ss.getClassPath().getClassPath();
		if (!Util.isBlankString(classpath)) {
			optionList.add("--class-path=" + classpath);
		}

		optionList.add("-jar");
		optionList.add(outjar.toString());

		optionList.add(getImageName(outjar).toString());

		runNativeBuilder(optionList);
	}

	protected void runNativeBuilder(List<String> optionList) throws IOException {
		File nilog = File.createTempFile("jbang", "native-image");
		Util.verboseMsg("native-image: " + String.join(" ", optionList));
		Util.infoMsg("log: " + nilog.toString());

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
	public static File getImageName(File outjar) {
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
			if (Util.isWindows()) {
				cmd = cmd + ".exe";
			}
			return new File(System.getenv(env)).toPath().resolve("bin").resolve(cmd).toAbsolutePath().toString();
		} else {
			return cmd;
		}
	}

	// NB: This might not be a definitive list of safe characters
	static Pattern cmdSafeChars = Pattern.compile("[a-zA-Z0-9.,_+=:;@()-]*");
	// TODO: Figure out what the real list of safe characters is for PowerShell
	static Pattern pwrSafeChars = Pattern.compile("[a-zA-Z0-9.,_+=:;@()-]*");
	static Pattern shellSafeChars = Pattern.compile("[a-zA-Z0-9._+=:@%/-]*");

	/**
	 * Escapes list of arguments where necessary using the current OS' way of
	 * escaping
	 */
	public static List<String> escapeOSArguments(List<String> args, Util.Shell shell) {
		return args.stream().map(arg -> escapeOSArgument(arg, shell)).collect(Collectors.toList());
	}

	/**
	 * Escapes list of arguments where necessary using a generic way of escaping
	 * (we'll just be using the Unix way)
	 */
	static List<String> escapeArguments(List<String> args) {
		return args.stream().map(JarBuilder::escapeUnixArgument).collect(Collectors.toList());
	}

	public static String escapeOSArgument(String arg, Util.Shell shell) {
		switch (shell) {
		case bash:
			return escapeUnixArgument(arg);
		case cmd:
			return escapeCmdArgument(arg);
		case powershell:
			return escapePowershellArgument(arg);
		}
		return arg;
	}

	static String escapeUnixArgument(String arg) {
		if (!shellSafeChars.matcher(arg).matches()) {
			arg = arg.replaceAll("(['])", "'\\\\''");
			arg = "'" + arg + "'";
		}
		return arg;
	}

	public static String escapeArgsFileArgument(String arg) {
		if (!shellSafeChars.matcher(arg).matches()) {
			arg = arg.replaceAll("([\"'\\\\])", "\\\\$1");
			arg = "\"" + arg + "\"";
		}
		return arg;
	}

	static String escapeCmdArgument(String arg) {
		if (!cmdSafeChars.matcher(arg).matches()) {
			// Windows quoting is just weird
			arg = arg.replaceAll("([()!^<>&|% ])", "^$1");
			arg = arg.replaceAll("([\"])", "\\\\^$1");
			arg = "^\"" + arg + "^\"";
		}
		return arg;
	}

	static String escapePowershellArgument(String arg) {
		if (!pwrSafeChars.matcher(arg).matches()) {
			arg = arg.replaceAll("(['])", "''");
			arg = "'" + arg + "'";
		}
		return arg;
	}

	protected void searchForMain(Script src, RunContext ctx, File tmpJarDir) {
		try {
			// using Files.walk method with try-with-resources
			try (Stream<Path> paths = Files.walk(tmpJarDir.toPath())) {
				List<Path> items = paths.filter(Files::isRegularFile)
										.filter(f -> !f.toFile().getName().contains("$"))
										.filter(f -> f.toFile().getName().endsWith(".class"))
										.collect(Collectors.toList());

				Indexer indexer = new Indexer();
				Index index;
				for (Path item : items) {
					try (InputStream stream = new FileInputStream(item.toFile())) {
						indexer.index(stream);
					}
				}
				index = indexer.complete();

				Collection<ClassInfo> classes = index.getKnownClasses();

				List<ClassInfo> mains = classes	.stream()
												.filter(src.getMainFinder())
												.collect(Collectors.toList());
				String mainName = src.getSuggestedMain();
				if (mains.size() > 1 && mainName != null) {
					List<ClassInfo> suggestedmain = mains	.stream()
															.filter(ci -> ci.simpleName().equals(mainName))
															.collect(Collectors.toList());
					if (!suggestedmain.isEmpty()) {
						mains = suggestedmain;
					}
				}

				if (!mains.isEmpty()) {
					ctx.setMainClass(mains.get(0).name().toString());
					if (mains.size() > 1) {
						Util.warnMsg(
								"Could not locate unique main() method. Use -m to specify explicit main method. Falling back to use first found: "
										+ mains	.stream()
												.map(x -> x.name().toString())
												.collect(Collectors.joining(",")));
					}
				}

				if (src.isAgent()) {
					Optional<ClassInfo> agentmain = classes	.stream()
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

					Optional<ClassInfo> premain = classes	.stream()
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
		} catch (IOException e) {
			throw new ExitException(1, e);
		}
	}

	private Predicate<ClassInfo> getMainFinder() {
		return pubClass -> pubClass.method("main", STRINGARRAYTYPE) != null;
	}

	protected Path generatePom(SourceSet ss, RunContext ctx, File tmpJarDir) throws IOException {
		Template pomTemplate = TemplateEngine.instance().getTemplate("pom.qute.xml");

		Path pomPath = null;
		if (pomTemplate == null) {
			// ignore
			Util.warnMsg("Could not locate pom.xml template");
		} else {
			String baseName = Util.getBaseName(ss.getResourceRef().getFile().getName());
			String group = "group";
			String artifact = baseName;
			String version = "999-SNAPSHOT";
			if (ss.getGav().isPresent()) {
				MavenCoordinate coord = DependencyUtil.depIdToArtifact(
						DependencyUtil.gavWithVersion(ss.getGav().get()));
				group = coord.getGroupId();
				artifact = coord.getArtifactId();
				version = coord.getVersion();
			}
			String pomfile = pomTemplate
										.data("baseName", baseName)
										.data("group", group)
										.data("artifact", artifact)
										.data("version", version)
										.data("description", ss.getDescription().orElse(""))
										.data("dependencies", ss.getClassPath().getArtifacts())
										.render();

			pomPath = new File(tmpJarDir, "META-INF/maven/" + group.replace(".", "/") + "/pom.xml").toPath();
			Files.createDirectories(pomPath.getParent());
			Util.writeString(pomPath, pomfile);
		}
		return pomPath;
	}
}
