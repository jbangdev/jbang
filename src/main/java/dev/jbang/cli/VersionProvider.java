package dev.jbang.cli;

import dev.jbang.util.Util;

import picocli.CommandLine;

public class VersionProvider implements CommandLine.IVersionProvider {
	@Override
	public String[] getVersion() throws Exception {
		return new String[] { Util.getJBangVersion() };
	}
}
