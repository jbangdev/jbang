package dev.jbang.source.builders;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import dev.jbang.source.*;
import dev.jbang.spi.IntegrationManager;
import dev.jbang.spi.IntegrationResult;
import dev.jbang.util.JarUtil;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;

public abstract class BaseBuilder implements Builder {
	protected final Project prj;

	protected boolean fresh = Util.isFresh();
	protected Util.Shell shell = Util.getShell();

	public static final String ATTR_BUILD_JDK = "Build-Jdk";
	public static final String ATTR_JBANG_JAVA_OPTIONS = "JBang-Java-Options";
	public static final String ATTR_BOOT_CLASS_PATH = "Boot-Class-Path";

	public static final Type STRINGARRAYTYPE = Type.create(DotName.createSimple("[Ljava.lang.String;"),
			Type.Kind.ARRAY);
	public static final Type STRINGTYPE = Type.create(DotName.createSimple("java.lang.String"), Type.Kind.CLASS);
	public static final Type INSTRUMENTATIONTYPE = Type.create(
			DotName.createSimple("java.lang.instrument.Instrumentation"), Type.Kind.CLASS);

	public BaseBuilder(Project prj) {
		this.prj = prj;
	}

	public BaseBuilder setFresh(boolean fresh) {
		this.fresh = fresh;
		return this;
	}

	public BaseBuilder setShell(Util.Shell shell) {
		this.shell = shell;
		return this;
	}

	@Override
	public Jar build() throws IOException {
		Jar result = null;

		Path outjar = prj.getJarFile();
		boolean nativeBuildRequired = prj.isNativeImage() && !Files.exists(getImageName(outjar));
		IntegrationResult integrationResult = new IntegrationResult(null, null, null);
		String requestedJavaVersion = prj.getJavaVersion();
		// always build the jar for native mode
		// it allows integrations the options to produce the native image
		boolean buildRequired = true;
		if (fresh) {
			Util.verboseMsg("Building as fresh build explicitly requested.");
		} else if (nativeBuildRequired) {
			Util.verboseMsg("Building as native build required.");
		} else if (Files.isReadable(outjar)) {
			// We already have a Jar, check if we can still use it
			Jar jarSrc = prj.asJar();

			if (jarSrc == null) {
				Util.verboseMsg("Building as previous built jar not found.");
			} else if (!jarSrc.isUpToDate()) {
				Util.verboseMsg("Building as previous build jar found but it or its dependencies not up-to-date.");
			} else if (JavaUtil.javaVersion(requestedJavaVersion) < JavaUtil.minRequestedVersion(
					jarSrc.getJavaVersion())) {
				Util.verboseMsg(
						String.format(
								"Building as requested Java version %s < than the java version used during last build %s",
								requestedJavaVersion, jarSrc.getJavaVersion()));
			} else {
				Util.verboseMsg("No build required. Reusing jar from " + jarSrc.getJarFile());
				result = jarSrc;
				buildRequired = false;
			}
		} else {
			Util.verboseMsg("Build required as " + outjar + " not readable or not found.");
		}

		if (buildRequired) {
			// set up temporary folder for compilation
			Path compileDir = getCompileDir();
			Util.deletePath(compileDir, true);
			compileDir.toFile().mkdirs();
			// do the actual building
			try {
				integrationResult = compile();
				createJar();
				result = prj.asJar();
			} finally {
				// clean up temporary folder
				Util.deletePath(compileDir, true);
			}
		}

		if (nativeBuildRequired) {
			if (integrationResult.nativeImagePath != null) {
				Files.move(integrationResult.nativeImagePath, getImageName(outjar));
			} else {
				buildNative();
			}
		}

		return result;
	}

	// build with javac and then jar...
	public IntegrationResult compile() throws IOException {
		String requestedJavaVersion = prj.getJavaVersion();
		Path compileDir = getCompileDir();
		List<String> optionList = new ArrayList<>();
		optionList.add(getCompilerBinary(requestedJavaVersion));
		optionList.addAll(prj.getMainSourceSet().getCompileOptions());
		String path = prj.resolveClassPath().getClassPath();
		if (!Util.isBlankString(path)) {
			optionList.addAll(Arrays.asList("-classpath", path));
		}
		optionList.addAll(Arrays.asList("-d", compileDir.toAbsolutePath().toString()));

		// add source files to compile
		optionList.addAll(prj	.getMainSourceSet()
								.getSources()
								.stream()
								.map(x -> x.getFile().toString())
								.collect(Collectors.toList()));

		// add additional files
		prj.getMainSourceSet().copyResourcesTo(compileDir);

		Path pomPath = generatePom(compileDir);

		Util.infoMsg(String.format("Building %s...", prj.getMainSource().isAgent() ? "javaagent" : "jar"));
		Util.verboseMsg("Compile: " + String.join(" ", optionList));
		runCompiler(optionList);

		// todo: setting properties to avoid loosing properties in integration call.
		Properties old = System.getProperties();
		Properties temp = new Properties(System.getProperties());
		for (Map.Entry<String, String> entry : prj.getProperties().entrySet()) {
			System.setProperty(entry.getKey(), entry.getValue());
		}
		IntegrationResult integrationResult = IntegrationManager.runIntegrations(prj, compileDir, pomPath);
		System.setProperties(old);

		if (prj.getMainClass() == null) { // if non-null user forced set main
			if (integrationResult.mainClass != null) {
				prj.setMainClass(integrationResult.mainClass);
			} else {
				searchForMain(compileDir);
			}
		}
		if (integrationResult.javaArgs != null && !integrationResult.javaArgs.isEmpty()) {
			// Add integration options to the java options
			prj.addRuntimeOptions(integrationResult.javaArgs);
		}

		return integrationResult;
	}

	protected void runCompiler(List<String> optionList) throws IOException {
		runCompiler(new ProcessBuilder(optionList).inheritIO());
	}

	protected void runCompiler(ProcessBuilder processBuilder) throws IOException {
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

	public void createJar() throws IOException {
		createJar(prj, getCompileDir(), prj.getJarFile());
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
		String runtimeOpts = String.join(" ", escapeArguments(rtArgs));
		if (!runtimeOpts.isEmpty()) {
			manifest.getMainAttributes()
					.putValue(ATTR_JBANG_JAVA_OPTIONS, runtimeOpts);
		}
		int buildJdk = JavaUtil.javaVersion(prj.getJavaVersion());
		if (buildJdk > 0) {
			String val = buildJdk >= 9 ? Integer.toString(buildJdk) : "1." + buildJdk;
			manifest.getMainAttributes().putValue(ATTR_BUILD_JDK, val);
		}

		FileOutputStream target = new FileOutputStream(jarFile.toFile());
		JarUtil.jar(target, compileDir.toFile().listFiles(), null, null, manifest);
		target.close();
	}

	protected void buildNative()
			throws IOException {
		List<String> optionList = new ArrayList<>();
		optionList.add(resolveInGraalVMHome("native-image", prj.getJavaVersion()));

		optionList.add("-H:+ReportExceptionStackTraces");

		optionList.add("--enable-https");

		String classpath = prj.resolveClassPath().getClassPath();
		if (!Util.isBlankString(classpath)) {
			optionList.add("--class-path=" + classpath);
		}

		optionList.add("-jar");
		optionList.add(prj.getJarFile().toString());

		optionList.add(getNativeImageOutputName(prj.getJarFile()).toString());

		runNativeBuilder(optionList);
	}

	/**
	 * Based on the jar path this will return the path for the output file to be
	 * passed to the native-image compiler. NB: On Windows the compiler always adds
	 * `.exe` to the name!
	 */
	private static Path getNativeImageOutputName(Path outjar) {
		// Let's strip the .jar extension
		if (outjar.toString().endsWith(".jar")) {
			outjar = Paths.get(Util.getBaseName(outjar.toString()));
		}
		if (Util.isWindows()) {
			return outjar;
		} else {
			return Paths.get(outjar + ".bin");
		}
	}

	protected void runNativeBuilder(List<String> optionList) throws IOException {
		Path nilog = Files.createTempFile("jbang", "native-image");
		Util.verboseMsg("native-image: " + String.join(" ", optionList));
		Util.infoMsg("log: " + nilog.toString());

		Process process = new ProcessBuilder(optionList).inheritIO().redirectOutput(nilog.toFile()).start();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			throw new ExitException(1, e);
		}

		if (process.exitValue() != 0) {
			throw new ExitException(1, "Error during native-image");
		}
	}

	/**
	 * Based on the jar path this will return the path to the output file as
	 * generated by the native-image compiler.
	 */
	public static Path getImageName(Path outjar) {
		outjar = getNativeImageOutputName(outjar);
		if (Util.isWindows()) {
			return Paths.get(outjar + ".exe");
		} else {
			return outjar;
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
		return args.stream().map(BaseBuilder::escapeUnixArgument).collect(Collectors.toList());
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

	protected void searchForMain(Path tmpJarDir) {
		try {
			// using Files.walk method with try-with-resources
			try (Stream<Path> paths = Files.walk(tmpJarDir)) {
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
												.filter(getMainFinder())
												.collect(Collectors.toList());
				String mainName = getSuggestedMain();
				if (mains.size() > 1 && mainName != null) {
					List<ClassInfo> suggestedmain = mains	.stream()
															.filter(ci -> ci.simpleName().equals(mainName))
															.collect(Collectors.toList());
					if (!suggestedmain.isEmpty()) {
						mains = suggestedmain;
					}
				}

				if (!mains.isEmpty()) {
					prj.setMainClass(mains.get(0).name().toString());
					if (mains.size() > 1) {
						Util.warnMsg(
								"Could not locate unique main() method. Use -m to specify explicit main method. Falling back to use first found: "
										+ mains	.stream()
												.map(x -> x.name().toString())
												.collect(Collectors.joining(",")));
					}
				}

				if (prj.getMainSource().isAgent()) {
					Optional<ClassInfo> agentmain = classes	.stream()
															.filter(pubClass -> pubClass.method("agentmain",
																	STRINGTYPE,
																	INSTRUMENTATIONTYPE) != null
																	||
																	pubClass.method("agentmain",
																			STRINGTYPE) != null)
															.findFirst();

					if (agentmain.isPresent()) {
						prj.setAgentMainClass(agentmain.get().name().toString());
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
						prj.setPreMainClass(premain.get().name().toString());
					}
				}
			}
		} catch (IOException e) {
			throw new ExitException(1, e);
		}
	}

	protected String getSuggestedMain() {
		if (!prj.getResourceRef().isStdin()) {
			return prj.getResourceRef().getFile().getFileName().toString().replace(getMainExtension(), "");
		} else {
			return null;
		}
	}

	protected abstract String getMainExtension();

	protected Predicate<ClassInfo> getMainFinder() {
		return pubClass -> pubClass.method("main", STRINGARRAYTYPE) != null;
	}

	protected abstract String getCompilerBinary(String requestedJavaVersion);

	protected Path generatePom(Path tmpJarDir) throws IOException {
		Template pomTemplate = TemplateEngine.instance().getTemplate("pom.qute.xml");

		Path pomPath = null;
		if (pomTemplate == null) {
			// ignore
			Util.warnMsg("Could not locate pom.xml template");
		} else {
			String baseName = Util.getBaseName(prj.getResourceRef().getFile().getFileName().toString());
			String group = "group";
			String artifact = baseName;
			String version = "999-SNAPSHOT";
			if (prj.getGav().isPresent()) {
				MavenCoordinate coord = DependencyUtil.depIdToArtifact(
						DependencyUtil.gavWithVersion(prj.getGav().get()));
				group = coord.getGroupId();
				artifact = coord.getArtifactId();
				version = coord.getVersion();
			}
			String pomfile = pomTemplate
										.data("baseName", baseName)
										.data("group", group)
										.data("artifact", artifact)
										.data("version", version)
										.data("description", prj.getDescription().orElse(""))
										.data("dependencies", prj.resolveClassPath().getArtifacts())
										.render();

			pomPath = tmpJarDir.resolve("META-INF/maven/" + group.replace(".", "/") + "/pom.xml");
			Files.createDirectories(pomPath.getParent());
			Util.writeString(pomPath, pomfile);
		}
		return pomPath;
	}

	protected Path getCompileDir() {
		return prj.getJarFile().getParent().resolve(prj.getJarFile().getFileName() + ".tmp");
	}
}
