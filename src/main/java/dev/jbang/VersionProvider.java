package dev.jbang;

import picocli.CommandLine;

public class VersionProvider implements CommandLine.IVersionProvider {
	@Override
	public String[] getVersion() throws Exception {
		return new String[] { dev.jbang.BuildConfig.VERSION };
	}
}
