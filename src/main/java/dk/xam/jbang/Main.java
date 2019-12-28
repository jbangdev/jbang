package dk.xam.jbang;

import static java.lang.System.*;
import static picocli.CommandLine.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

@Command(name="jbang", versionProvider = VersionProvider.class, description = "Compiles and runs .java/.jsh scripts.")
public class Main implements Callable<Integer> {

	@Spec
	CommandSpec spec;

	@Option(names = {"-d", "--debug"}, arity = "0", description = "Launch with java debug enabled.")
	boolean debug;

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Display help/info")
	boolean helpRequested;

	@Option(names = {"--version"}, versionHelp = true, arity = "0", description = "Display version info")
	boolean versionRequested;

	@Parameters
	String script;

	@Parameters(arity = "1..*")
	List<String> params;


	void info(String msg) {
		spec.commandLine().getErr().println(msg);
	}

	void quit(int status) {
		out.println(status == 0 ? "true" : "false");
		exit(status);
	}

	static String USAGE = "j'bang - Enhanced scripting support for Java on *nix-based systems.\n" + "\n"
			+ "			Usage:\n" + "			 jbang <script> [<script_args>]...\n" + "\n"
			+ "			The <script> is a java file (.java) with a main method. \n" + "\n"
			+ "			Copyright : 2019 Max Rydahl Andersen\n" + "			License   : MIT\n"
			+ "			Website   : https://github.com/maxandersen/jbang\n" + "".trim();
	private static File prepareScript;

	public static void main(String... args) throws FileNotFoundException {
		var main = new Main();

		new CommandLine(main).setOut(new PrintWriter(err, true)).setErr(new PrintWriter(err, true)).parseArgs(argsForJbang(args));
		System.exit(main.call());
	}

	@Override
	public Integer call() throws FileNotFoundException {

		if (helpRequested) {
			spec.commandLine().usage(err);
			quit(0);
		} else if (versionRequested) {
			spec.commandLine().printVersionHelp(err);
			quit(0);
		}


		prepareScript = prepareScript(script);

		Scanner sc = new Scanner(prepareScript);
		sc.useDelimiter("\\Z");

		Script script = new Script(sc.next());

		List<String> dependencies = script.collectDependencies();

		var classpath = new DependencyUtil().resolveDependencies(dependencies, Collections.emptyList(), true);
		StringBuffer optionalArgs = new StringBuffer(" ");

		var javacmd = "java";
		var sourceargs = " --source 11";
		if (prepareScript.getName().endsWith(".jsh")) {
			javacmd = "jshell";
			sourceargs = "";
			if (!classpath.isBlank()) {
				optionalArgs.append("--class-path " + classpath);
			}

		} else {
			// optionalArgs.append("
			// -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
			if (!classpath.isBlank()) {
				optionalArgs.append("-classpath " + classpath);
			}
		}

		var cmdline = new StringBuffer(javacmd).append(optionalArgs).append(sourceargs)
				.append(" " + getenv("JBANG_FILE"))
				.append(" " + params.stream().skip(1).collect(Collectors.joining(" ")));

		out.println(cmdline);

		return 0;
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
		for (var a : args) {
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
		return argsForJbang.toArray(new String[argsForJbang.size()]);

	}

	private static File prepareScript(String scriptresouce) {
		File scriptFile = null;

		// we need to keep track of the scripts dir or the working dir in case of stdin
		// script to correctly resolve includes
		// var includeContext = new File(".").toURI();

		// map script argument to script file
		var probe = new File(scriptresouce);

		if (!probe.canRead()) {
			// not a file so let's keep the script-file undefined here
			scriptFile = null;
		} else if (probe.getName().endsWith(".java") || probe.getName().endsWith(".jsh")) {
			scriptFile = probe;
		} else {
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
			throw new IllegalArgumentException("Could not read script argument " + scriptresouce);
		}

		// note script file must be not null at this point

		return scriptFile;
	}


}
