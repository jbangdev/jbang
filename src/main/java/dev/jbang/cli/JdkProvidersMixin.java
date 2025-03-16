package dev.jbang.cli;

import java.util.ArrayList;
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

	public List<String> opts() {
		List<String> opts = new ArrayList<>();
		if (jdkProviders != null) {
			for (String p : jdkProviders) {
				opts.add("--jdk-providers");
				opts.add(p);
			}
		}
		return opts;
	}
}
