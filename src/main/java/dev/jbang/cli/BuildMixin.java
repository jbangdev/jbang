package dev.jbang.cli;

import picocli.CommandLine;

public class BuildMixin {
	public String javaVersion;

	@CommandLine.Option(names = { "-j",
			"--java" }, description = "JDK version to use for running the script.")
	void setJavaVersion(String javaVersion) {
		if (!javaVersion.matches("\\d+[+]?")) {
			throw new IllegalArgumentException(
					"Invalid version, should be a number optionally followed by a plus sign");
		}
		this.javaVersion = javaVersion;
	}

	@CommandLine.Option(names = { "-m",
			"--main" }, description = "Main class to use when running. Used primarily for running jar's.")
	String main;

	@CommandLine.Option(names = {
			"-n", "--native" }, description = "Build using native-image")
	boolean nativeImage;
}
