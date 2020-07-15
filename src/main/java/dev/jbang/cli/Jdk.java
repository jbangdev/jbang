package dev.jbang.cli;

import java.io.IOException;
import java.nio.file.Path;

import dev.jbang.JdkManager;
import dev.jbang.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "jdk", description = "Manage JDKs.", hidden = true)
public class Jdk {

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	@CommandLine.Command(name = "install", description = "Installs a JDK.")
	public Integer install(
			@CommandLine.Option(names = { "--force",
					"-f" }, description = "set a system property", defaultValue = "false") boolean force,
			@CommandLine.Parameters(paramLabel = "name", index = "0", description = "The version to install", arity = "1") int version)
			throws IOException {
		if (force || !JdkManager.isInstalledJdk(version)) {
			System.err.println("Installing JDK " + version + "...");
			JdkManager.downloadAndInstallJdk(version);
		} else {
			System.err.println("JDK " + version + " is already installed");
		}
		return CommandLine.ExitCode.SOFTWARE;
	}

	@CommandLine.Command(name = "list", description = "Lists installed JDKs.")
	public Integer list() throws IOException {
		System.out.println("HUH?");
		JdkManager.listInstalledJdks().forEach(jdk -> System.out.println("XXX"));
		return CommandLine.ExitCode.SOFTWARE;
	}

	@CommandLine.Command(name = "uninstall", description = "Uninstalls an existing JDK.")
	public Integer remove(
			@CommandLine.Parameters(paramLabel = "name", index = "0", description = "The version to install", arity = "1") int version)
			throws IOException {
		Path jdkDir = JdkManager.getInstalledJdk(version);
		if (jdkDir != null) {
			Util.deleteFolder(jdkDir, false);
		}
		return CommandLine.ExitCode.SOFTWARE;
	}
}
