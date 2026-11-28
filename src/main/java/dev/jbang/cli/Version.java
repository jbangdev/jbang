package dev.jbang.cli;

import java.io.IOException;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;

import dev.jbang.ExitException;
import dev.jbang.Settings;
import dev.jbang.dependencies.ArtifactResolver;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.Util;
import dev.jbang.util.VersionChecker;

@CommandDefinition(name = "version", description = "Display version info.")
public class Version extends BaseCommand {

	@Option(name = "check", hasValue = false, description = "Check if a new version of jbang is available (ignored if --update is specified)")
	boolean checkForUpdate;

	@Option(name = "update", hasValue = false, description = "Update jbang to the latest version")
	boolean update;

	@Option(name = "force", hasValue = false, description = "Force re-install even if already up to date")
	boolean force;

	@Override
	public Integer doCall() {
		boolean updated = false;
		if (update) {
			String pendingLauncherUpdate = App.AppInstall.cmdLauncherUpdateCommand();
			if (pendingLauncherUpdate != null) {
				System.out.println(pendingLauncherUpdate);
				return ExitException.EXIT_EXECUTE;
			}
			if (VersionChecker.updateOrInform(force)) {
				try {
					App.AppInstall.installJBang(true);
					String latest = VersionChecker.getLatestVersion();
					if (latest != null) {
						Util.infoMsg("jbang updated to " + latest + ".");
					} else {
						Util.infoMsg("jbang updated.");
					}
					updated = true;
				} catch (IOException e) {
					throw new ExitException(ExitException.EXIT_INTERNAL_ERROR, "Could not install command", e);
				}
			}
		} else if (checkForUpdate) {
			System.out.println(Util.getJBangVersion());
			VersionChecker.checkNowAndInform();
		} else {
			System.out.println(Util.getJBangVersion());
		}

		if (isVerbose()) {
			System.err.println("Cache: " + Settings.getCacheDir());
			System.err.println("Config: " + Settings.getConfigDir());
			System.err.println("Repository: " + ArtifactResolver.getLocalMavenRepo());
			System.err
				.println("Java: " + System.getProperty("java.home") + " [" + System.getProperty("java.version") + "]");
			System.err.println("OS: " + Util.getOS());
			System.err.println("Arch: " + Util.getArch());
			System.err.println("Shell: " + Util.getShell());
			System.err.println("Native Image: " + JavaUtil.inNativeImage());
		}

		if (updated) {
			String cmdLauncherUpdate = App.AppInstall.cmdLauncherUpdateCommand();
			if (cmdLauncherUpdate != null) {
				System.out.println(cmdLauncherUpdate);
				return ExitException.EXIT_EXECUTE;
			}
		}

		return ExitException.EXIT_OK;
	}
}
