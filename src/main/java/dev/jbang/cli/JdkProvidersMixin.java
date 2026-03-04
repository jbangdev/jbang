package dev.jbang.cli;

import java.util.ArrayList;
import java.util.List;

import dev.jbang.devkitman.JdkManager;
import dev.jbang.util.JavaUtil;

import picocli.CommandLine;

public class JdkProvidersMixin {
	protected static List<String> jdkProviders;
	protected static List<String> jdkVendors;
	protected static String jdkInstaller;

	@CommandLine.Option(names = {
			"--jdk-providers" }, description = "Use the given providers to manage JDKs", split = ",")
	void setJdkProviders(List<String> jdkProviders) {
		JdkProvidersMixin.jdkProviders = jdkProviders;
	}

	@CommandLine.Option(names = {
			"--jdk-vendors" }, description = "Use the given vendors/distributions to install new JDKs", split = ",")
	void setJdkVendors(List<String> jdkVendors) {
		JdkProvidersMixin.jdkVendors = jdkVendors;
	}

	@CommandLine.Option(names = {
			"--jdk-installer" }, description = "Use the given installer to install new JDKs", hidden = true)
	void setJdkInstaller(String jdkInstaller) {
		JdkProvidersMixin.jdkInstaller = jdkInstaller;
	}

	protected JdkManager jdkMan;

	protected JdkManager getJdkManager() {
		if (jdkMan == null) {
			jdkMan = JavaUtil.defaultJdkManager(jdkProviders, jdkVendors, jdkInstaller);
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
