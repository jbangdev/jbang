package dk.xam.jbang;

import static dk.xam.jbang.Settings.CP_SEPARATOR;
import static dk.xam.jbang.Util.swizzleURL;
import static java.lang.System.err;
import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static java.lang.System.out;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.IParameterConsumer;
import static picocli.CommandLine.InitializationException;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;
import static picocli.CommandLine.ScopeType;
import static picocli.CommandLine.Spec;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.text.StringEscapeUtils;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

import com.sun.nio.file.SensitivityWatchEventModifier;

import io.quarkus.qute.Template;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

abstract class JbangBaseCommand implements Callable<Integer> {

	static {
		Logger logger = Logger.getLogger("org.jboss.shrinkwrap.resolver");
		logger.setLevel(Level.SEVERE);
	}

	@Spec
	CommandSpec spec;

	@Option(names = { "-h", "--help" }, usageHelp = true, description = "Display help/info")
	boolean helpRequested;

	static boolean verbose = false;

	void info(String msg) {
		spec.commandLine().getErr().println("[jbang] " + msg);
	}

	void warn(String msg) {
		info("[WARNING] " + msg);
	}

	boolean isVerbose() {
		return verbose;
	}

	@Override
	public Integer call() throws IOException {
		try {
			return doCall();
		} catch (ExitException e) {
			if (isVerbose()) {
				e.printStackTrace();
			} else {
				info(e.getMessage());
				info("Run with --verbose for more details");
			}

			return e.getStatus();
		}
	}

	public abstract Integer doCall() throws IOException;
}

@Command(name = "jbang", footer = "\nCopyright: 2020 Max Rydahl Andersen, License: MIT\nWebsite: https://github.com/jbangdev/jbang", description = "Compiles and runs .java/.jsh scripts.", subcommands = {
		JbangRun.class, JbangEdit.class, JbangInit.class, JbangAlias.class, JbangTrust.class, JbangClearCache.class,
		JbangCompletion.class, JbangVersion.class })
public class Main extends JbangBaseCommand {

	@Option(names = { "--verbose" }, description = "jbang will be verbose on what it does.", scope = ScopeType.INHERIT)
	void setVerbose(boolean verbose) {
		JbangBaseCommand.verbose = verbose;
	}

	public Integer doCall() {
		spec.commandLine().usage(err);
		return 0;
	}

	public static void main(String... args) {
		CommandLine cli = getCommandLine();
		args = handleDefaultRun(cli.getCommandSpec(), args);
		int exitcode = cli.execute(args);
		System.exit(exitcode);
	}

	private static String[] handleDefaultRun(CommandSpec spec, String[] args) {
		List<String> leadingOpts = new ArrayList<>();
		List<String> remainingArgs = new ArrayList<>();
		boolean foundParam = false;
		for (String arg : args) {
			if (!arg.startsWith("-")) {
				foundParam = true;
			}
			if (foundParam) {
				remainingArgs.add(arg);
			} else {
				leadingOpts.add(arg);
			}
		}
		// Check if we have a parameter and it's not the same as any of the subcommand
		// names
		if (!remainingArgs.isEmpty() && !spec.subcommands().keySet().contains(remainingArgs.get(0))) {
			List<String> result = new ArrayList<>();
			result.add("run");
			result.addAll(leadingOpts);
			result.addAll(remainingArgs);
			args = result.toArray(args);
		}
		return args;
	}

	static CommandLine getCommandLine() {
		PrintWriter errW = new PrintWriter(err, true);

		return getCommandLine(errW, errW);
	}

	static CommandLine getCommandLine(PrintWriter localout, PrintWriter localerr) {
		return new CommandLine(new Main())	.setExitCodeExceptionMapper(new VersionProvider())
											.setStopAtPositional(true)
											.setOut(localout)
											.setErr(localerr);
	}

	public static List<MavenCoordinate> findDeps(File pom) {
		// todo use to dump out pom dependencies
		return Maven.resolver()
					.loadPomFromFile(pom)
					.importCompileAndRuntimeDependencies()
					.resolve()
					.withoutTransitivity()
					.asList(MavenCoordinate.class);
	}
}

abstract class JbangBaseScriptCommand extends JbangBaseCommand {

	@Option(names = { "-o", "--offline" }, description = "Work offline. Fail-fast if dependencies are missing.")
	boolean offline;

	@Parameters(index = "0", arity = "1", description = "A file with java code or if named .jsh will be run with jshell")
	String scriptOrFile;

	@Parameters(index = "1..*", arity = "0..*", description = "Parameters to pass on to the script")
	List<String> userParams = new ArrayList<>();

	protected Script script;

	static Script prepareScript(String scriptResource) throws IOException {
		File scriptFile = getScriptFile(scriptResource);
		if (scriptFile == null) {
			// Not found as such, so let's check the aliases
			if (Settings.getAliases().containsKey(scriptResource)) {
				scriptFile = getScriptFile(Settings.getAliases().get(scriptResource).scriptRef);
			}
		}

		// Support URLs as script files
		/*
		 * if(scriptResource.startsWith("http://")||scriptResource.startsWith("https://"
		 * )) { scriptFile = fetchFromURL(scriptResource)
		 *
		 * includeContext = URI(scriptResource.run { substring(lastIndexOf('/') + 1) })
		 * }
		 *
		 * // Support for support process substitution and direct script arguments
		 * if(scriptFile==null&&!scriptResource.endsWith(".kts")&&!scriptResource.
		 * endsWith(".kt")) { val scriptText = if (File(scriptResource).canRead()) {
		 * File(scriptResource).readText().trim() } else { // the last resort is to
		 * assume the input to be a java program scriptResource.trim() }
		 *
		 * scriptFile = createTmpScript(scriptText) }
		 */
		// just proceed if the script file is a regular file at this point
		if (scriptFile == null || !scriptFile.canRead()) {
			throw new IllegalArgumentException("Could not read script argument " + scriptResource);
		}

		// note script file must be not null at this point

		Script s = null;
		try {
			s = new Script(scriptFile);
			s.setOriginal(new File(scriptResource));
		} catch (FileNotFoundException e) {
			throw new ExitException(1, e);
		}
		return s;
	}

	private static File getScriptFile(String scriptResource) throws IOException {
		File scriptFile;

		// we need to keep track of the scripts dir or the working dir in case of stdin
		// script to correctly resolve includes
		// var includeContext = new File(".").toURI();

		// map script argument to script file
		File probe = new File(scriptResource);

		if (!probe.canRead()) {
			// not a file so let's keep the script-file undefined here
			scriptFile = null;
		} else if (probe.getName().endsWith(".java") || probe.getName().endsWith(".jsh")) {
			scriptFile = probe;
		} else {
			String original = Util.readString(probe.toPath());
			// TODO: move temp handling somewhere central
			String urlHash = getStableID(original);

			if (original.startsWith("#!")) { // strip bash !# if exists
				original = original.substring(original.indexOf("\n"));
			}

			File tempFile = new File(Settings.getCacheDir().toFile(),
					"/script_cache_" + urlHash + "/" + unkebabify(probe.getName()));
			tempFile.getParentFile().mkdirs();
			Util.writeString(tempFile.toPath().toAbsolutePath(), original);
			scriptFile = tempFile;

			// if we can "just" read from script resource create tmp file
			// i.e. script input is process substitution file handle
			// not FileInputStream(this).bufferedReader().use{ readText()} does not work nor
			// does this.readText
			// includeContext = this.absoluteFile.parentFile.toURI()
			// createTmpScript(FileInputStream(this).bufferedReader().readText())
		}

		// support stdin
		if (scriptResource.equals("-") || scriptResource.equals("/dev/stdin")) {
			String scriptText = new BufferedReader(
					new InputStreamReader(System.in, StandardCharsets.UTF_8))
																				.lines()
																				.collect(Collectors.joining(
																						System.lineSeparator()));

			String urlHash = getStableID(scriptText);
			File cache = new File(Settings.getCacheDir().toFile(), "/stdin_cache_" + urlHash);
			cache.mkdirs();
			scriptFile = new File(cache, urlHash + ".jsh");
			Util.writeString(scriptFile.toPath(), scriptText);
		} else if (scriptResource.startsWith("http://") || scriptResource.startsWith("https://")
				|| scriptResource.startsWith("file:/")) {
			// support url's as script files
			scriptFile = fetchFromURL(scriptResource);
		}

		return scriptFile;
	}

	static String getStableID(File backingFile) throws IOException {
		return getStableID(Util.readString(backingFile.toPath()));
	}

	static String getStableID(String input) {
		final MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new ExitException(-1, e);
		}
		final byte[] hashbytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
		StringBuilder sb = new StringBuilder();
		for (byte b : hashbytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	/**
	 *
	 * @param name script name
	 * @return camel case of kebab string if name does not end with .java or .jsh
	 */
	static String unkebabify(String name) {
		if (!(name.endsWith(".java") || name.endsWith(".jsh"))) {
			name = kebab2camel(name) + ".java";
		}
		return name;
	}

	static String kebab2camel(String name) {

		if (name.contains("-")) { // xyz-plug becomes XyzPlug
			return Arrays	.stream(name.split("\\-"))
							.map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase())
							.collect(Collectors.joining());
		} else {
			return name; // xyz stays xyz
		}
	}

	private static File fetchFromURL(String scriptURL) {
		try {
			java.net.URI uri = new java.net.URI(scriptURL);

			if (!Settings.getTrustedSources().isURLTrusted(uri)) {
				throw new ExitException(10, scriptURL + " is not from a trusted source thus aborting.\n" +
						"If you trust the url to be safe to run are here a few suggestions:\n" +
						"Limited trust:\n    jbang --trust=" + goodTrustURL(scriptURL) + "\n" +
						"Trust all subdomains:\n    jbang --trust=" + "*." + uri.getAuthority() + "\n" +
						"Trust all sources (WARNING! disables url protection):\n    jbang --trust=\"*\"" + "\n" +
						"\nFor more control edit ~/.jbang/trusted-sources.json" + "\n");
			}

			scriptURL = swizzleURL(scriptURL);

			String urlHash = getStableID(scriptURL);
			File urlCache = new File(Settings.getCacheDir().toFile(), "/url_cache_" + urlHash);
			urlCache.mkdirs();
			Path path = Util.downloadFileSwizzled(scriptURL, urlCache);

			return path.toFile();
		} catch (IOException | URISyntaxException e) {
			throw new ExitException(2, "Could not download " + scriptURL, e);
		}
	}

	private static String goodTrustURL(String url) {
		String originalUrl = url;

		url = url.replaceFirst("^https://github.com/(.*)/blob/(.*)$",
				"https://github.com/$1/");

		url = url.replaceFirst("^https://gitlab.com/(.*)/-/blob/(.*)$",
				"https://gitlab.com/$1/");

		url = url.replaceFirst("^https://bitbucket.org/(.*)/src/(.*)$",
				"https://bitbucket.org/$1/");

		url = url.replaceFirst("^https://twitter.com/(.*)/status/(.*)$",
				"https://twitter.com/$1/");

		if (url.equals(originalUrl)) {
			java.net.URI u = null;
			try {
				u = new java.net.URI(url);
			} catch (URISyntaxException e) {
				return url;
			}
			url = u.getScheme() + "://" + u.getAuthority();
		}

		return url;
	}

	public static String getBaseName(String fileName) {
		int index = fileName.lastIndexOf('.');
		if (index == -1) {
			return kebab2camel(fileName);
		} else {
			return fileName.substring(0, index);
		}
	}

}

@Command(name = "run", description = "Compile and run provided .java/.jsh script.")
class JbangRun extends JbangBaseScriptCommand {

	@Option(names = { "-d",
			"--debug" }, fallbackValue = "4004", parameterConsumer = IntFallbackConsumer.class, description = "Launch with java debug enabled on specified port (default: ${FALLBACK-VALUE}) ")
	int debugPort = -1;

	@Option(names = {
			"--cds" }, description = "If specified Class Data Sharing (CDS) will be used for building and running (requires Java 13+)", negatable = true)
	Boolean cds;

	Optional<Boolean> cds() {
		return Optional.ofNullable(cds);
	}

	boolean debug() {
		return debugPort >= 0;
	}

	@Option(names = { "-D" }, description = "set a system property")
	Map<String, String> properties = new HashMap<>();

	@Option(names = {
			"--insecure" }, description = "Enable insecure trust of all SSL certificates.", defaultValue = "false")
	boolean insecure;

	@Option(names = { "--interactive" }, description = "activate interactive mode")
	boolean interactive;

	@Option(names = {
			"-n", "--native" }, description = "Build via native-image and run", defaultValue = "false")
	boolean nativeImage;

	@Override
	public Integer doCall() throws IOException {
		if (insecure) {
			enableInsecure();
		}

		script = prepareScript(scriptOrFile);

		if (script.needsJar()) {
			build(script);
		}
		String cmdline = generateCommandLine(script);
		if (isVerbose()) {
			info("run: " + cmdline);
		}
		out.println(cmdline);

		return 0;
	}

	private void enableInsecure() {
		try {
			// Create a trust manager that does not validate certificate chains
			TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				public void checkClientTrusted(X509Certificate[] certs, String authType) {
				}

				public void checkServerTrusted(X509Certificate[] certs, String authType) {
				}
			}
			};

			// Install the all-trusting trust manager
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

			// Create all-trusting host name verifier
			HostnameVerifier allHostsValid = new HostnameVerifier() {
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			};

			// Install the all-trusting host verifier
			HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
		} catch (NoSuchAlgorithmException | KeyManagementException ex) {
			throw new RuntimeException(ex);
		}
	}

	// build with javac and then jar... todo: split up in more testable chunks
	void build(Script script) throws IOException {
		File baseDir = new File(Settings.getCacheDir().toFile(), "jars");
		File tmpJarDir = new File(baseDir, script.backingFile.getName() +
				"." + getStableID(script.backingFile));
		tmpJarDir.mkdirs();

		File outjar = new File(tmpJarDir.getParentFile(), tmpJarDir.getName() + ".jar");

		if (!outjar.exists()) {
			List<String> optionList = new ArrayList<String>();
			optionList.add(resolveInJavaHome("javac"));
			optionList.addAll(script.collectCompileOptions());
			String path = script.resolveClassPath(offline);
			if (!path.trim().isEmpty()) {
				optionList.addAll(Arrays.asList("-classpath", path));
			}
			optionList.addAll(Arrays.asList("-d", tmpJarDir.getAbsolutePath()));
			optionList.addAll(Arrays.asList(script.backingFile.getPath()));

			Util.info("Building jar...");
			if (isVerbose())
				Util.info("compile: " + String.join(" ", optionList));

			Process process = new ProcessBuilder(optionList).inheritIO().start();
			try {
				process.waitFor();
			} catch (InterruptedException e) {
				throw new ExitException(1, e);
			}

			if (process.exitValue() != 0) {
				throw new ExitException(1, "Error during compile");
			}

			try {
				// using Files.walk method with try-with-resources
				try (Stream<Path> paths = Files.walk(tmpJarDir.toPath())) {
					List<Path> items = paths.filter(Files::isRegularFile)
											.filter(f -> !f.toFile().getName().contains("$"))
											.filter(f -> f.toFile().getName().endsWith(".class"))
											.collect(Collectors.toList());

					if (items.size() > 1) { // todo: this feels like a very sketchy way to find the proper class name
						// but it works.
						String mainname = script.backingFile.getName().replace(".java", ".class");
						items = items	.stream()
										.filter(f -> f.toFile().getName().equalsIgnoreCase(mainname))
										.collect(Collectors.toList());
					}

					if (items.size() != 1) {
						throw new ExitException(1,
								"Could not locate unique class. Found " + items.size() + " candidates.");
					} else {
						Path classfile = items.get(0);
						String mainClass = findMainClass(tmpJarDir.toPath(), classfile);
						script.setMainClass(mainClass);
					}
				}
			} catch (IOException e) {
				throw new ExitException(1, e);
			}
			script.createJarFile(tmpJarDir, outjar);

		} else {
			try (JarFile jf = new JarFile(outjar)) {
				script.setMainClass(
						jf.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
			}
		}

		if (nativeImage && !getImageName(outjar).exists()) {
			List<String> optionList = new ArrayList<String>();
			optionList.add(resolveInGraalVMHome("native-image"));

			optionList.add("-H:+ReportExceptionStackTraces");

			optionList.add("--enable-https");

			String classpath = script.resolveClassPath(offline);
			optionList.add("--class-path=" + classpath);

			optionList.add("-jar");
			optionList.add(outjar.toString());

			optionList.add(getImageName(outjar).toString());

			File nilog = File.createTempFile("jbang", "native-image");
			if (isVerbose())
				Util.info("native-image: " + String.join(" ", optionList));
			Util.info("log: " + nilog.toString());

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
		script.setJar(outjar);
	}

	String generateCommandLine(Script script) throws IOException {

		List<String> fullArgs = new ArrayList<>();

		if (nativeImage) {
			String imagename = getImageName(script.getJar()).toString();
			if (new File(imagename).exists()) {
				fullArgs.add(imagename);
			} else {
				warn("native built image not found - running in java mode.");
			}
		}

		if (fullArgs.isEmpty()) {
			String classpath = script.resolveClassPath(offline);

			List<String> optionalArgs = new ArrayList<String>();

			String javacmd = resolveInJavaHome("java");
			if (script.backingFile.getName().endsWith(".jsh")) {

				javacmd = resolveInJavaHome("jshell");
				if (!classpath.trim().isEmpty()) {
					optionalArgs.add("--class-path=" + classpath);
				}

				optionalArgs.add("--startup=DEFAULT");

				File tempFile = File.createTempFile("jbang_arguments_", script.backingFile.getName());
				Util.writeString(tempFile.toPath(), generateArgs(userParams, properties));

				optionalArgs.add("--startup=" + tempFile.getAbsolutePath());

				if (debug()) {
					info("debug not possible when running via jshell.");
				}

			} else {
				addPropertyFlags("-D", optionalArgs);

				// optionalArgs.add("--source 11");
				if (debug()) {
					optionalArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + debugPort);
				}
				if (!classpath.trim().isEmpty()) {
					optionalArgs.add("-classpath " + classpath);
				}

				if (optionActive(cds(), script.enableCDS())) {
					String cdsJsa = script.getJar().getAbsolutePath() + ".jsa";
					if (script.wasJarCreated()) {
						if (isVerbose()) {
							info("CDS: Archiving Classes At Exit at " + cdsJsa);
						}
						optionalArgs.add("-XX:ArchiveClassesAtExit=" + cdsJsa);
					} else {
						if (isVerbose()) {
							info("CDS: Using shared archive classes from " + cdsJsa);
						}
						optionalArgs.add("-XX:SharedArchiveFile=" + cdsJsa);
					}
				}
			}

			// protect against spaces in dirs in paths, especially on windows.
			if (javacmd.contains(" ")) {
				javacmd = '"' + javacmd + '"';
			}
			fullArgs.add(javacmd);
			fullArgs.addAll(script.collectRuntimeOptions());
			fullArgs.addAll(script.getAutoDetectedModuleArguments(javacmd, offline));
			fullArgs.addAll(optionalArgs);
			if (script.getMainClass() != null) {
				fullArgs.add(script.getMainClass());
			} else {
				fullArgs.add(script.backingFile.toString());
			}
		}

		if (!script.forJShell()) {
			fullArgs.addAll(userParams);
		} else if (!interactive) {
			File tempFile = File.createTempFile("jbang_exit_", script.backingFile.getName());
			Util.writeString(tempFile.toPath(), "/exit");
			fullArgs.add(tempFile.toString());
		}

		return String.join(" ", fullArgs);

	}

	static boolean optionActive(Optional<Boolean> master, boolean local) {
		return master.map(Boolean::booleanValue).orElse(local);
	}

	private void addPropertyFlags(String def, List<String> optionalArgs) {
		properties.forEach((k, e) -> {
			optionalArgs.add(def + k + "=" + e);
		});
	}

	/** based on jar what will the binary image name be. **/
	private File getImageName(File outjar) {

		if (getProperty("os.name").toLowerCase().startsWith("windows")) {
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

	String generateArgs(List<String> args, Map<String, String> properties) {

		String buf = "String[] args = { " +
				args.stream()
					.map(s -> '"' + StringEscapeUtils.escapeJava(s) + '"')
					.collect(Collectors.joining(", "))
				+
				" }" +
				(properties.isEmpty() ? "" : "\n") +
				properties	.entrySet()
							.stream()
							.map(x -> "System.setProperty(\"" + x.getKey() + "\",\"" + x.getValue() + "\");")
							.collect(Collectors.joining("\n"));
		return buf;
	}

	static String resolveInJavaHome(String cmd) {
		return resolveInEnv("JAVA_HOME", cmd);
	}

	static String resolveInGraalVMHome(String cmd) {
		String newcmd = resolveInEnv("GRAALVM_HOME", cmd);

		if (newcmd.equals(cmd) &&
				!new File(newcmd).exists()) {
			return resolveInJavaHome(cmd);
		} else {
			return newcmd;
		}
	}

	private static String resolveInEnv(String env, String cmd) {
		if (getenv(env) != null) {
			if (getProperty("os.name").toLowerCase().startsWith("windows")) {
				cmd = cmd + ".exe";
			}
			return new File(getenv(env)).toPath().resolve("bin").resolve(cmd).toAbsolutePath().toString();
		} else {
			return cmd;
		}
	}

	static class IntFallbackConsumer implements IParameterConsumer {
		@Override
		public void consumeParameters(Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec) {
			String arg = args.pop();
			try {
				int port = Integer.parseInt(arg);
				argSpec.setValue(port);
			} catch (Exception ex) {
				String fallbackValue = (argSpec.isOption()) ? ((OptionSpec) argSpec).fallbackValue() : null;
				try {
					int fallbackPort = Integer.parseInt(fallbackValue);
					argSpec.setValue(fallbackPort);
				} catch (Exception badFallbackValue) {
					throw new InitializationException("FallbackValue for --debug must be an int", badFallbackValue);
				}
				args.push(arg); // put it back
			}
		}
	}
}

@Command(name = "edit", description = "Edit a .java/.jsh script.")
class JbangEdit extends JbangBaseScriptCommand {

	@Option(names = {
			"--live" }, description = "Setup temporary project, launch editor and regenerate project on dependency changes.")
	String liveeditor;

	@Override
	public Integer doCall() throws IOException {
		script = prepareScript(scriptOrFile);
		File project = createProjectForEdit(script, userParams, false);
		// err.println(project.getAbsolutePath());
		if (liveeditor == null) {
			out.println("echo " + project.getAbsolutePath()); // quit(project.getAbsolutePath());
		} else {
			if (liveeditor.isEmpty()) {
				liveeditor = System	.getenv()
									.getOrDefault("JBANG_EDITOR",
											System	.getenv()
													.getOrDefault("VISUAL",
															System.getenv().getOrDefault("EDITOR", "")));
			}
			if ("gitpod".equals(liveeditor) && System.getenv("GITPOD_WORKSPACE_URL") != null) {
				info("Open this url to edit the project in your gitpod session:\n\n"
						+ System.getenv("GITPOD_WORKSPACE_URL") + "#" + project.getAbsolutePath() + "\n\n");
			} else {
				List<String> optionList = new ArrayList<>();
				optionList.add(liveeditor);
				optionList.add(project.getAbsolutePath());
				info("Running `" + String.join(" ", optionList) + "`");
				Process process = new ProcessBuilder(optionList).start();
			}
			try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
				Path watched = script.getOriginalFile().getAbsoluteFile().getParentFile().toPath();
				final WatchKey watchKey = watched.register(watchService,
						new WatchEvent.Kind[] { StandardWatchEventKinds.ENTRY_MODIFY },
						SensitivityWatchEventModifier.HIGH);
				info("Watching for changes in " + watched);
				while (true) {
					final WatchKey wk = watchService.take();
					for (WatchEvent<?> event : wk.pollEvents()) {
						// we only register "ENTRY_MODIFY" so the context is always a Path.
						final Path changed = (Path) event.context();
						// info(changed.toString());
						if (Files.isSameFile(script.getOriginalFile().toPath(), changed)) {
							try {
								// TODO only regenerate when dependencies changes.
								info("Regenerating project.");
								script = prepareScript(scriptOrFile);
								createProjectForEdit(script, userParams, true);
							} catch (RuntimeException ee) {
								warn("Error when re-generating project. Ignoring it, but state might be undefined: "
										+ ee.getMessage());
							}
						}
					}
					// reset the key
					boolean valid = wk.reset();
					if (!valid) {
						warn("edit-live file watch key no longer valid!");
					}
				}
			} catch (InterruptedException e) {
				warn("edit-live interrupted");
			}
		}
		return 0;
	}

	/** Create Project to use for editing **/
	File createProjectForEdit(Script script, List<String> userParams, boolean reload) throws IOException {

		List<String> collectDependencies = script.collectDependencies();
		String cp = script.resolveClassPath(offline);
		List<String> resolvedDependencies = Arrays.asList(cp.split(CP_SEPARATOR));

		File baseDir = new File(Settings.getCacheDir().toFile(), "temp_projects");

		String name = script.getOriginalFile().getName();
		name = unkebabify(name);

		File tmpProjectDir = new File(baseDir, name + "_jbang_" +
				getStableID(script.getOriginalFile().getAbsolutePath()));
		tmpProjectDir.mkdirs();
		tmpProjectDir = new File(tmpProjectDir, stripPrefix(name));
		tmpProjectDir.mkdirs();

		File srcDir = new File(tmpProjectDir, "src");
		srcDir.mkdir();

		File srcFile = new File(srcDir, name);
		if (!srcFile.exists()
				&& !createSymbolicLink(srcFile.toPath(), script.getOriginalFile().getAbsoluteFile().toPath())) {
			createHardLink(srcFile.toPath(), script.getOriginalFile().getAbsoluteFile().toPath());
		}

		// create build gradle
		String baseName = getBaseName(name);
		String templateName = "build.qute.gradle";
		Path destination = new File(tmpProjectDir, "build.gradle").toPath();
		TemplateEngine engine = Settings.getTemplateEngine();

		renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName, userParams,
				destination);

		// setup eclipse
		templateName = ".qute.classpath";
		destination = new File(tmpProjectDir, ".classpath").toPath();
		renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName, userParams,
				destination);

		templateName = ".qute.project";
		destination = new File(tmpProjectDir, ".project").toPath();
		renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName, userParams,
				destination);

		templateName = "main.qute.launch";
		destination = new File(tmpProjectDir, ".eclipse/" + baseName + ".launch").toPath();
		destination.toFile().getParentFile().mkdirs();
		renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName, userParams,
				destination);

		templateName = "main-port-4004.qute.launch";
		destination = new File(tmpProjectDir, ".eclipse/" + baseName + "-port-4004.launch").toPath();
		renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName, userParams,
				destination);

		// setup vscode
		templateName = "launch.qute.json";
		destination = new File(tmpProjectDir, ".vscode/launch.json").toPath();
		if (isNeeded(reload, destination)) {
			destination.toFile().getParentFile().mkdirs();
			renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName, userParams,
					destination);
		}

		templateName = "settings.qute.json";
		destination = new File(tmpProjectDir, ".vscode/settings.json").toPath();
		if (isNeeded(reload, destination)) {
			destination.toFile().getParentFile().mkdirs();
			renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName, userParams,
					destination);
		}

		// setup intellij - disabled for now as idea was not picking these up directly
		/*
		 * templateName = "idea-port-4004.qute.xml"; destination = new
		 * File(tmpProjectDir, ".idea/runConfigurations/" + baseName +
		 * "-port-4004.xml").toPath(); destination.toFile().getParentFile().mkdirs();
		 * renderTemplate(engine, collectDependencies, baseName, resolvedDependencies,
		 * templateName, userParams, destination);
		 *
		 * templateName = "idea.qute.xml"; destination = new File(tmpProjectDir,
		 * ".idea/runConfigurations/" + baseName + ".xml").toPath();
		 * destination.toFile().getParentFile().mkdirs(); renderTemplate(engine,
		 * collectDependencies, baseName, resolvedDependencies, templateName,
		 * userParams, destination);
		 */

		return tmpProjectDir;
	}

	private boolean isNeeded(boolean reload, Path file) {
		return !file.toFile().exists() && !reload;
	}

	private boolean createSymbolicLink(Path src, Path target) {
		try {
			Files.createSymbolicLink(src, target);
			return true;
		} catch (IOException e) {
			info(e.toString());
		}
		info("Creation of symbolic link failed.");
		return false;
	}

	private boolean createHardLink(Path src, Path target) {
		try {
			info("Now try creating a hard link instead of symbolic.");
			Files.createLink(src, target);
		} catch (IOException e) {
			info("Creation of hard link failed. Script must be on the same drive as $JBANG_CACHE_DIR (typically under $HOME) for hardlink creation to work. Or call the command with admin rights.");
			throw new ExitException(1, e);
		}
		return true;
	}

	private void renderTemplate(TemplateEngine engine, List<String> collectDependencies, String baseName,
			List<String> resolvedDependencies, String templateName,
			List<String> userParams, Path destination)
			throws IOException {
		Template template = engine.getTemplate(templateName);
		if (template == null)
			throw new ExitException(1, "Could not locate template named: '" + templateName + "'");
		String result = template
								.data("dependencies", collectDependencies)
								.data("baseName", baseName)
								.data("classpath",
										resolvedDependencies.stream()
															.filter(t -> !t.isEmpty())
															.collect(Collectors.toList()))
								.data("userParams", String.join(" ", userParams))
								.data("cwd", System.getProperty("user.dir"))
								.render();

		Util.writeString(destination, result);
	}

	static String stripPrefix(String fileName) {
		if (fileName.indexOf(".") > 0) {
			return fileName.substring(0, fileName.lastIndexOf("."));
		} else {
			return fileName;
		}
	}
}

@Command(name = "init", description = "Initialize a .java/.jsh script.")
class JbangInit extends JbangBaseScriptCommand {

	@Option(names = {
			"--template" }, description = "Init script with a java class useful for scripting", defaultValue = "hello")
	String initTemplate;

	@Override
	public Integer doCall() throws IOException {
		File f = new File(scriptOrFile);
		if (f.exists()) {
			warn("File " + f + " already exists. Will not initialize.");
		} else {
			// Use try-with-resource to get auto-closeable writer instance
			try (BufferedWriter writer = Files.newBufferedWriter(f.toPath())) {
				String result = renderInitClass(f, initTemplate);
				writer.write(result);
				f.setExecutable(true);
			} catch (ExitException e) {
				f.delete(); // if template lookup fails we need to delete file to not end up with a empty
				// file.
				throw e;
			}
			info("File initialized. You can now run it with 'jbang " + scriptOrFile
					+ "' or edit it using 'code `jbang --edit " + scriptOrFile + "`'");
		}
		return 0;
	}

	String renderInitClass(File f, String template) {
		Template helloTemplate = Settings.getTemplateEngine().getTemplate("init-" + template + ".java.qute");

		if (helloTemplate == null) {
			throw new ExitException(1, "Could not find init template named: " + template);
		} else {
			return helloTemplate.data("baseName", getBaseName(f.getName())).render();
		}
	}
}

@Command(name = "alias", description = "Manage aliases.", subcommands = { JbangAliasAdd.class, JbangAliasList.class,
		JbangAliasRemove.class })
class JbangAlias {
}

@Command(name = "add", description = "Add alias for script reference.")
class JbangAliasAdd extends JbangBaseCommand {

	@Parameters(index = "0", description = "A name for the alias", arity = "1")
	String name;

	@Parameters(index = "1", description = "A file or URL to a Java code file", arity = "1")
	String scriptOrFile;

	@Override
	public Integer doCall() {
		Settings.addAlias(name, scriptOrFile);
		return CommandLine.ExitCode.SOFTWARE;
	}
}

@Command(name = "list", description = "Add alias for script reference.")
class JbangAliasList extends JbangBaseCommand {

	@Override
	public Integer doCall() {
		Settings.getAliases().keySet().stream().sorted().forEach(a -> spec.commandLine().getOut().println(a));
		return CommandLine.ExitCode.SOFTWARE;
	}
}

@Command(name = "remove", description = "Remove existing alias.")
class JbangAliasRemove extends JbangBaseCommand {

	@Parameters(index = "0", description = "A name for the alias", arity = "1")
	String name;

	@Override
	public Integer doCall() {
		Settings.removeAlias(name);
		return CommandLine.ExitCode.SOFTWARE;
	}
}

@Command(name = "trust", description = "Manage trusted domains.", subcommands = { JbangTrustAdd.class })
class JbangTrust {
}

@Command(name = "add", description = "Add trusted domains.")
class JbangTrustAdd extends JbangBaseCommand {

	@Parameters(index = "0", description = "Rules for trusted sources", arity = "1..*")
	List<String> rules = new ArrayList<>();

	@Override
	public Integer doCall() {
		Settings.getTrustedSources().add(rules, Settings.getTrustedSourcesFile().toFile());
		return CommandLine.ExitCode.SOFTWARE;
	}
}

@Command(name = "clear-cache", description = "Clear cache of dependency list and temporary projects.")
class JbangClearCache extends JbangBaseCommand {

	@Override
	public Integer doCall() {
		info("Clearing cache at " + Settings.getCacheDir());
		// noinspection resource
		Settings.clearCache();
		return CommandLine.ExitCode.SOFTWARE;
	}
}

@Command(name = "completion", description = "Output auto-completion script for bash/zsh.\nUsage: source <(jbang --completion)")
class JbangCompletion extends JbangBaseCommand {

	@Override
	public Integer doCall() throws IOException {
		return completion();
	}

	public int completion() throws IOException {
		String script = AutoComplete.bash(
				spec.parent().name(),
				spec.parent().commandLine());
		// not PrintWriter.println: scripts with Windows line separators fail in strange
		// ways!

		File file = File.createTempFile("jbang-completion", "temp");
		Util.writeString(file.toPath(), script);

		out.print("cat " + file.getAbsolutePath());
		out.print('\n');
		out.flush();
		return 0;
	}
}

@Command(name = "version", description = "Display version info.")
class JbangVersion extends JbangBaseCommand {

	@Override
	public Integer doCall() {
		spec.commandLine().getOut().println(dk.xam.jbang.BuildConfig.VERSION);
		return CommandLine.ExitCode.SOFTWARE;
	}
}
