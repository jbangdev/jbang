package dk.xam.jbang;

import picocli.CommandLine;

public class VersionProvider implements CommandLine.IVersionProvider {
	@Override
	public String[] getVersion() throws Exception {
		return new String[]{dk.xam.jbang.BuildConfig.VERSION};
	}
}
