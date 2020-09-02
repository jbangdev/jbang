package dev.jbang.cli;

import java.io.IOException;
import java.io.PrintWriter;

import dev.jbang.JdkManager;
import dev.jbang.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "jdk", description = "Manage Java Development Kits installed by jbang.")
public class Jdk {

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	@CommandLine.Command(name = "install", description = "Installs a JDK.")
	public Integer install(
			@CommandLine.Option(names = { "--force",
					"-f" }, description = "set a system property", defaultValue = "false") boolean force,
			@CommandLine.Parameters(paramLabel = "version", index = "0", description = "The version to install", arity = "1") int version)
			throws IOException {
		if (force || !JdkManager.isInstalledJdk(version)) {
			JdkManager.downloadAndInstallJdk(version, force);
		} else {
			Util.infoMsg("JDK " + version + " is already installed");
		}
		return CommandLine.ExitCode.SOFTWARE;
	}

	@CommandLine.Command(name = "list", description = "Lists installed JDKs.")
	public Integer list() throws IOException {
		PrintWriter err = spec.commandLine().getErr();
		JdkManager.listInstalledJdks().forEach(jdk -> err.println(jdk));
		return CommandLine.ExitCode.SOFTWARE;
	}

	@CommandLine.Command(name = "uninstall", description = "Uninstalls an existing JDK.")
	public Integer remove(
			@CommandLine.Parameters(paramLabel = "version", index = "0", description = "The version to install", arity = "1") int version)
			throws IOException {
		JdkManager.uninstallJdk(version);
		return CommandLine.ExitCode.SOFTWARE;
	}
}
