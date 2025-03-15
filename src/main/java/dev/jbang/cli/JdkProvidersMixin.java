package dev.jbang.cli;

import java.util.ArrayList;
import java.util.List;

import dev.jbang.devkitman.JdkManager;
import dev.jbang.util.JavaUtil;

import picocli.CommandLine;

public class JdkProvidersMixin {

	@CommandLine.Option(names = {
			"--jdk-providers" }, description = "Use the given providers to check for installed JDKs", split = ",", hidden = true)
	List<String> jdkProviders;

	private JdkManager jdkMan;

	protected JdkManager getJdkManager() {
		if (jdkMan == null) {
			jdkMan = JavaUtil.defaultJdkManager(jdkProviders);
		}
		return jdkMan;
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
