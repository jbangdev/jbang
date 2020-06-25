package dk.xam.jbang.cli;

import static java.lang.System.err;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.ScopeType;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

import dk.xam.jbang.*;

import picocli.CommandLine;

@Command(name = "jbang", footer = "\nCopyright: 2020 Max Rydahl Andersen, License: MIT\nWebsite: https://github.com/jbangdev/jbang", description = "Compiles and runs .java/.jsh scripts.", subcommands = {
		JbangRun.class, JbangEdit.class, JbangInit.class, JbangAlias.class, JbangTrust.class, JbangCache.class,
		JbangCompletion.class, JbangVersion.class })
public class Jbang extends JbangBaseCommand {

	@Option(names = { "--verbose" }, description = "jbang will be verbose on what it does.", scope = ScopeType.INHERIT)
	void setVerbose(boolean verbose) {
		JbangBaseCommand.verbose = verbose;
	}

	public Integer doCall() {
		spec.commandLine().usage(err);
		return 0;
	}

	public static CommandLine getCommandLine() {
		PrintWriter errW = new PrintWriter(err, true);

		return getCommandLine(errW, errW);
	}

	public static CommandLine getCommandLine(PrintWriter localout, PrintWriter localerr) {
		return new CommandLine(new Jbang())	.setExitCodeExceptionMapper(new VersionProvider())
											.setStopAtPositional(true)
											.setOut(localout)
											.setErr(localerr);
	}

	static List<MavenCoordinate> findDeps(File pom) {
		// todo use to dump out pom dependencies
		return Maven.resolver()
					.loadPomFromFile(pom)
					.importCompileAndRuntimeDependencies()
					.resolve()
					.withoutTransitivity()
					.asList(MavenCoordinate.class);
	}
}
