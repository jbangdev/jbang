package dk.xam.jbang;

import static dk.xam.jbang.Settings.CP_SEPARATOR;
import static java.lang.System.*;
import static picocli.CommandLine.*;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.text.StringEscapeUtils;

import com.sun.nio.file.SensitivityWatchEventModifier;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateLocator;
import io.quarkus.qute.Variant;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

@Command(name = "jbang", footer = "\nCopyright: 2020 Max Rydahl Andersen, License: MIT\nWebsite: https://github.com/maxandersen/jbang", versionProvider = VersionProvider.class, description = "Compiles and runs .java/.jsh scripts.")
public class Main implements Callable<Integer> {

	@Spec
	CommandSpec spec;

	@Option(names = { "-d",
			"--debug" }, fallbackValue = "4004", parameterConsumer = IntFallbackConsumer.class, description = "Launch with java debug enabled on specified port(default: ${FALLBACK-VALUE}) ")
	int debugPort = -1;
	private Script script;

	boolean debug() {
		return debugPort >= 0;
	}

	@Option(names = { "-e", "--edit" }, description = "Edit script by setting up temporary project.")
	boolean edit;

	@Option(names = {
			"--edit-live" }, description = "Setup temporary project, launch editor and regenerate project on dependency changes.", parameterConsumer = PlainStringFallbackConsumer.class, arity = "0..1", fallbackValue = "${JBANG_EDITOR:-${VISUAL:-${EDITOR}}}")
	String liveeditor;

	@Option(names = { "-h", "--help" }, usageHelp = true, description = "Display help/info")
	boolean helpRequested;

	@Option(names = { "--version" }, versionHelp = true, arity = "0", description = "Display version info")
	boolean versionRequested;

	@Option(names = {
			"--clear-cache" }, help = true, description = "Clear cache of dependency list and temporary projects")
	boolean clearCache;

	@Option(names = { "--verbose" }, description = "jbang will be verbose on what it does.")
	boolean verbose;

	@Parameters(index = "0", description = "A file with java code or if named .jsh will be run with jshell")
	String scriptOrFile;

	@Parameters(index = "1..*", arity = "0..*", description = "Parameters to pass on to the script")
	List<String> userParams = new ArrayList<String>();

	@Option(names = {
			"--init" }, description = "Init script with a java class useful for scripting", parameterConsumer = PlainStringFallbackConsumer.class, arity = "0..1", fallbackValue = "hello")
	String initTemplate;

	@Option(names = "--completion", help = true, description = "Output auto-completion script for bash/zsh.\nUsage: source <(jbang --completion)")
	boolean completionRequested;

	@Option(names = { "-D" }, description = "set a system property")
	Map<String, String> properties = new HashMap<>();

	@Option(names = { "--naive" }, description = "Trust all SSL certificates.")
	boolean naive;

	public int completion() throws IOException {
		String script = AutoComplete.bash(
				spec.name(),
				spec.commandLine());
		// not PrintWriter.println: scripts with Windows line separators fail in strange
		// ways!

		File file = File.createTempFile("jbang-completion", "temp");
		Util.writeString(file.toPath(), script);

		out.print("cat " + file.getAbsolutePath());
		out.print('\n');
		out.flush();
		return 0;
	}

	void info(String msg) {
		spec.commandLine().getErr().println("[jbang] " + msg);
	}

	void warn(String msg) {
		info("[WARNING] " + msg);
	}

	/*
	 * void quit(int status) { out.println(status == 0 ? "true" : "false"); throw
	 * new ExitException(status); }
	 */

	/*
	 * void quit(String output) { out.println("echo " + output); throw new
	 * ExitException(0); }
	 */

	public static void main(String... args) {
		int exitcode = getCommandLine().execute(args);
		System.exit(exitcode);
	}

	static void createJarFile(File path, File output, String mainclass) throws IOException {
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		if (mainclass != null) {
			manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainclass);
		}

		FileOutputStream target = new FileOutputStream(output);
		JarUtil.jar(target, path.listFiles(), null, null, manifest);
		target.close();
	}

	static CommandLine getCommandLine() {
		return getCommandLine(new PrintWriter(err), new PrintWriter(err));
	}

	static CommandLine getCommandLine(PrintWriter localout, PrintWriter localerr) {
		return new CommandLine(new Main())	.setExitCodeExceptionMapper(new VersionProvider())
											.setStopAtPositional(true)
											.setOut(new PrintWriter(localout, true))
											.setErr(new PrintWriter(localerr, true));
	}

	private Integer doCall() throws IOException {
		if (helpRequested) {
			spec.commandLine().usage(err);
			return 0; // quit(0);
		} else if (versionRequested) {
			spec.commandLine().printVersionHelp(err);
			return 0; // quit(0);
		} else if (completionRequested) {
			return completion();
		}

		if (naive) {
			try {
				// Create a trust manager that does not validate certificate chains
				TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
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

		if (clearCache) {
			info("Clearing cache at " + Settings.getCacheDir().toPath());
			// noinspection resource
			Files	.walk(Settings.getCacheDir().toPath())
					.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(File::delete);
		}

		if (initTemplate != null) {
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
		}
		if (edit || liveeditor != null) {
			script = prepareScript(scriptOrFile);
			File project = createProjectForEdit(script, userParams);
			// err.println(project.getAbsolutePath());
			if (edit) {
				out.println("echo " + project.getAbsolutePath()); // quit(project.getAbsolutePath());
			} else {
				List<String> optionList = new ArrayList<>();
				optionList.add(liveeditor);
				optionList.add(project.getAbsolutePath());
				info("Running `" + String.join(" ", optionList) + "`");
				Process process = new ProcessBuilder(optionList).start();
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
									createProjectForEdit(script, userParams);
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
		} else {
			if (initTemplate == null && scriptOrFile != null) {
				script = prepareScript(scriptOrFile);

				if (script.needsJar()) {
					build(script, this);
				}
				String cmdline = generateCommandLine(script);
				if (verbose) {
					info("run: " + cmdline);
				}
				out.println(cmdline);
			}
		}
		return 0;
	}

	@Override
	public Integer call() throws IOException {
		try {
			return doCall();
		} catch (ExitException e) {
			info(e.getMessage());
			if (verbose) {
				e.printStackTrace();
			} else if (e.getCause() != null) {
				info("Run with --verbose for more details");
			}
			return e.getStatus();
		}
	}

	// build with javac and then jar... todo: split up in more testable chunks
	static void build(Script script, Main m) throws IOException {
		File baseDir = new File(Settings.getCacheDir(), "jars");
		File tmpJarDir = new File(baseDir, script.backingFile.getName() +
				"." + getStableID(script.backingFile));
		tmpJarDir.mkdirs();

		File outjar = new File(tmpJarDir.getParentFile(), tmpJarDir.getName() + ".jar");

		if (!outjar.exists()) {
			List<String> optionList = new ArrayList<String>();
			optionList.add(resolveInJavaHome("javac"));
			optionList.addAll(script.collectCompileOptions());
			String path = script.resolveClassPath();
			if (!path.trim().isEmpty()) {
				optionList.addAll(Arrays.asList("-classpath", path));
			}
			optionList.addAll(Arrays.asList("-d", tmpJarDir.getAbsolutePath()));
			optionList.addAll(Arrays.asList(script.backingFile.getPath()));

			Util.info("Building jar...");
			if (m.verbose)
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
			createJarFile(tmpJarDir, outjar, script.getMainClass());
		} else {
			try (JarFile jf = new JarFile(outjar)) {
				script.setMainClass(
						jf.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
			}
		}

		script.setJar(outjar);
	}

	static public String findMainClass(Path base, Path classfile) {
		StringBuilder mainClass = new StringBuilder(classfile.getFileName().toString().replace(".class", ""));
		while (!classfile.getParent().equals(base)) {
			classfile = classfile.getParent();
			mainClass.insert(0, classfile.getFileName().toString() + ".");
		}
		return mainClass.toString();
	}

	/** Create Project to use for editing **/
	File createProjectForEdit(Script script, List<String> userParams) throws IOException {

		List<String> collectDependencies = script.collectDependencies();
		String cp = script.resolveClassPath();
		List<String> resolvedDependencies = Arrays.asList(cp.split(CP_SEPARATOR));

		Engine engine = Engine.builder().addDefaults().addLocator(this::locate).build();

		File baseDir = new File(Settings.getCacheDir(), "temp_projects");

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
		if (!srcFile.exists()) {
			Files.createSymbolicLink(srcFile.toPath(), script.getOriginalFile().getAbsoluteFile().toPath());
		}

		// create build gradle
		String baseName = getBaseName(name);
		String templateName = "build.qute.gradle";
		Path destination = new File(tmpProjectDir, "build.gradle").toPath();
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
		destination.toFile().getParentFile().mkdirs();
		renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName, userParams,
				destination);

		templateName = "settings.qute.json";
		destination = new File(tmpProjectDir, ".vscode/settings.json").toPath();
		destination.toFile().getParentFile().mkdirs();
		renderTemplate(engine, collectDependencies, baseName, resolvedDependencies, templateName, userParams,
				destination);

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

	private void renderTemplate(Engine engine, List<String> collectDependencies, String baseName,
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

	String renderInitClass(File f, String template) {
		Engine engine = Engine.builder().addDefaults().addLocator(this::locate).build();
		Template helloTemplate = engine.getTemplate("init-" + template + ".java.qute");

		if (helloTemplate == null) {
			throw new ExitException(1, "Could not find init template named: " + template);
		} else {
			return helloTemplate.data("baseName", getBaseName(f.getName())).render();
		}
	}

	/**
	 * @param path
	 * @return the optional reader
	 */
	private Optional<TemplateLocator.TemplateLocation> locate(String path) {
		URL resource = null;
		String basePath = "";
		String templatePath = basePath + path;
		// LOGGER.debugf("Locate template for %s", templatePath);
		resource = locatePath(templatePath);

		if (resource != null) {
			return Optional.of(new ResourceTemplateLocation(resource));
		}
		return Optional.empty();
	}

	private URL locatePath(String path) {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (cl == null) {
			cl = Main.class.getClassLoader();
		}
		return cl.getResource(path);
	}

	static class ResourceTemplateLocation implements TemplateLocator.TemplateLocation {

		private final URL resource;
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
		private Optional<Variant> variant = null;

		public ResourceTemplateLocation(URL resource) {
			this.resource = resource;
			this.variant = null;
		}

		@Override
		public Reader read() {
			try {
				return new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				return null;
			}
		}

		@Override
		public Optional<Variant> getVariant() {
			return variant;
		}

	}

	public static String getBaseName(String fileName) {
		int index = fileName.lastIndexOf('.');
		if (index == -1) {
			return kebab2camel(fileName);
		} else {
			return fileName.substring(0, index);
		}
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

	String generateCommandLine(Script script) throws IOException {

		String classpath = script.resolveClassPath();

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
		}

		// protect against spaces in dirs in paths, especially on windows.
		if (javacmd.contains(" ")) {
			javacmd = '"' + javacmd + '"';
		}
		List<String> fullArgs = new ArrayList<>();
		fullArgs.add(javacmd);
		fullArgs.addAll(script.collectRuntimeOptions());
		fullArgs.addAll(script.getAutoDetectedModuleArguments(javacmd));
		fullArgs.addAll(optionalArgs);
		if (script.getMainClass() != null) {
			fullArgs.add(script.getMainClass());
		} else {
			fullArgs.add(script.backingFile.toString());
		}
		if (!script.forJShell()) {
			fullArgs.addAll(userParams);
		}

		return fullArgs.stream().collect(Collectors.joining(" "));
	}

	private void addPropertyFlags(String def, List<String> optionalArgs) {
		properties.forEach((k, e) -> {
			optionalArgs.add(def + k + "=" + e);
		});
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

	static String resolveInJavaHome(String cmd) {

		if (getenv("JAVA_HOME") != null) {
			if (getProperty("os.name").toLowerCase().startsWith("windows")) {
				cmd = cmd + ".exe";
			}
			return new File(getenv("JAVA_HOME")).toPath().resolve("bin").resolve(cmd).toAbsolutePath().toString();
		} else {
			return cmd;
		}
	}

	/**
	 * return the list arguments that relate to jbang. First arguments that starts
	 * with '-' and is not just '-' for stdin goes to jbang, rest goes to the
	 * script.
	 *
	 * @param args
	 * @return the list arguments that relate to jbang
	 */
	static String[] argsForJbang(String... args) {
		final List<String> argsForScript = new ArrayList<>();
		final List<String> argsForJbang = new ArrayList<>();

		/*
		 * if (args.length > 0 && "--init".equals(args[0])) {
		 * argsForJbang.addAll(Arrays.asList(args)); return; }
		 */

		boolean found = false;
		for (String a : args) {
			if (!found && a.startsWith("-") && a.length() > 1) {
				argsForJbang.add(a);
			} else {
				found = true;
				argsForScript.add(a);
			}
		}

		if (!argsForScript.isEmpty() && !argsForJbang.isEmpty()) {

			argsForJbang.add("--");
		}

		argsForJbang.addAll(argsForScript);
		return argsForJbang.toArray(new String[0]);

	}

	static Script prepareScript(String scriptResource) throws IOException {
		File scriptFile = null;

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

			File tempFile = new File(Settings.getCacheDir(),
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
		/*
		 * if(scriptResource=="-"||scriptResource=="/dev/stdin") { val scriptText =
		 * generateSequence() { readLine() }.joinToString("\n").trim() scriptFile =
		 * createTmpScript(scriptText) }
		 */

		// support url's as script files
		if (scriptResource.startsWith("http://") || scriptResource.startsWith("https://")
				|| scriptResource.startsWith("file:/")) {

			scriptFile = fetchFromURL(scriptResource);
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
			s.setOriginal(probe);
		} catch (FileNotFoundException e) {
			throw new ExitException(1, e);
		}
		return s;
	}

	static String readStringFromURL(String requestURL) throws IOException {
		try (Scanner scanner = new Scanner(new URL(requestURL).openStream(),
				StandardCharsets.UTF_8.toString())) {
			scanner.useDelimiter("\\A");
			return scanner.hasNext() ? scanner.next() : "";
		}
	}

	public static String swizzleURL(String url) {
		url = url.replaceFirst("^https://github.com/(.*)/blob/(.*)$",
				"https://raw.githubusercontent.com/$1/$2");
		// TODO: for gist we need to be smarter when it comes to downloading as it gives
		// an invalid flag when jbang compiles
		// scriptResource =
		// scriptResource.replaceFirst("^https://gist.github.com/([a-zA-Z0-9]*)/([a-zA-Z0-9]*)$",
		// "https://gist.githubusercontent.com/$1/$2/raw");

		url = url.replaceFirst("^https://gitlab.com/(.*)/-/blob/(.*)$",
				"https://gitlab.com/$1/-/raw/$2");

		url = url.replaceFirst("^https://bitbucket.org/(.*)/src/(.*)$",
				"https://bitbucket.org/$1/raw/$2");

		return url;
	}

	private static File fetchFromURL(String scriptURL) {
		try {
			scriptURL = swizzleURL(scriptURL);

			String urlHash = getStableID(scriptURL);
			File urlCache = new File(Settings.getCacheDir(), "/url_cache_" + urlHash);
			urlCache.mkdirs();
			Path path = Util.downloadFile(scriptURL, urlCache);

			return path.toFile();
		} catch (IOException e) {
			throw new ExitException(2, "Could not download " + scriptURL, e);
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

	// used to make --init xyz.java not pickup what are filenames. if you need
	// --init xyz then do --init ./xyz.
	static class PlainStringFallbackConsumer implements IParameterConsumer {
		@Override
		public void consumeParameters(Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec) {
			String arg = args.pop();

			if (arg.contains(".") || arg.startsWith("--")) { // if a dot is in the argument assume it is a file name
				args.push(arg);
				argSpec.setValue(((OptionSpec) argSpec).fallbackValue());
			} else if (arg.trim().isEmpty()) { // if empty, means param= so use fallback value
				argSpec.setValue(((OptionSpec) argSpec).fallbackValue());
			} else { // great, looks like a single value we can use!
				argSpec.setValue(arg);
			}
		}
	}
}
