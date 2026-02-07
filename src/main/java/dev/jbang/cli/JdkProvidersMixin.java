package dev.jbang.cli;

import java.util.ArrayList;
import java.util.List;

import dev.jbang.devkitman.JdkManager;
import dev.jbang.util.JavaUtil;

import picocli.CommandLine;

public class JdkProvidersMixin {

	@CommandLine.Option(names = {
			"--jdk-providers" }, description = "Use the given providers to manage JDKs", split = ",", hidden = true)
	List<String> jdkProviders;

	@CommandLine.Option(names = {
			"--jdk-vendors" }, description = "Use the given vendors/distributions to install new JDKs", split = ",", hidden = true)
	List<String> jdkVendors;

	private JdkManager jdkMan;

	protected JdkManager getJdkManager() {
		if (jdkMan == null) {
			jdkMan = JavaUtil.defaultJdkManager(jdkProviders, jdkVendors);
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
		if (jdkVendors != null) {
			for (String v : jdkVendors) {
				opts.add("--jdk-vendors");
				opts.add(v);
			}
		}
		return opts;
	}
}
