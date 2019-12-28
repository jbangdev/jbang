package dk.xam.jbang;

import picocli.CommandLine;

public class VersionProvider implements CommandLine.IVersionProvider, CommandLine.IExitCodeExceptionMapper {
	@Override
	public String[] getVersion() throws Exception {
		return new String[]{dk.xam.jbang.BuildConfig.VERSION};
	}

	@Override
	public int getExitCode(Throwable t) {
		if (t instanceof ExitException) {
			return ((ExitException) t).getStatus();
		}
		return CommandLine.ExitCode.SOFTWARE;
	}
}
