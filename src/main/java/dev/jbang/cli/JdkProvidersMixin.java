package dev.jbang.cli;

import java.util.List;

import dev.jbang.net.JdkManager;

import picocli.CommandLine;

public class JdkProvidersMixin {

	@CommandLine.Option(names = {
			"--jdk-providers" }, description = "Use the given providers to check for installed JDKs", split = ",", hidden = true)
	List<String> jdkProviders;

	protected void initJdkProviders() {
		if (jdkProviders != null && !jdkProviders.isEmpty()) {
			JdkManager.initProvidersByName(jdkProviders);
		}
	}
}
