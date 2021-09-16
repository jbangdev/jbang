package dev.jbang.cli;

import java.io.IOException;
import java.io.PrintWriter;

import dev.jbang.Settings;
import dev.jbang.util.Util;
import dev.jbang.util.VersionChecker;

import picocli.CommandLine;

@CommandLine.Command(name = "version", description = "Display version info.")
public class Version extends BaseCommand {

	@CommandLine.Option(names = { "--check" }, description = "Check if a new version of jbang is available")
	boolean checkForUpdate;

	@CommandLine.Option(names = { "--update" }, description = "Update jbang to the latest version")
	boolean update;

	@Override
	public Integer doCall() {
		if (update) {
			if (VersionChecker.updateOrInform(checkForUpdate)) {
				try {
					AppInstall.installJBang(true);
				} catch (IOException e) {
					throw new ExitException(EXIT_INTERNAL_ERROR, "Could not install command", e);
				}
			}
		} else if (checkForUpdate) {
			System.out.println(Util.getJBangVersion());
			VersionChecker.checkNowAndInform();
		} else {
			System.out.println(Util.getJBangVersion());
		}

		if (isVerbose()) {
			PrintWriter out = spec.commandLine().getOut();
			out.println("Cache: " + Settings.getCacheDir());
			out.println("Config: " + Settings.getConfigDir());
			out.println("Repository:" + Settings.getLocalMavenRepo());
		}

		return EXIT_OK;
	}
}
