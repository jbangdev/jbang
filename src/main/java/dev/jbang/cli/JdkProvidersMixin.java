package dev.jbang.cli;

import java.util.ArrayList;
import java.util.List;

import dev.jbang.devkitman.JdkManager;
import dev.jbang.util.JavaUtil;

import picocli.CommandLine;

public class JdkProvidersMixin {
	@CommandLine.Option(names = {
			"--jdk-providers" }, description = "Use the given providers to manage JDKs", split = ",", scope = CommandLine.ScopeType.INHERIT)
	List<String> jdkProviders;

	@CommandLine.Option(names = {
			"--jdk-distros" }, description = "Use the given distributions to install new JDKs", split = ",", scope = CommandLine.ScopeType.INHERIT)
	List<String> jdkDistros;

	@CommandLine.Option(names = {
			"--jdk-installer" }, description = "Use the given installer to install new JDKs", hidden = true, scope = CommandLine.ScopeType.INHERIT)
	String jdkInstaller;

	protected JdkManager jdkMan;

	protected JdkManager getJdkManager() {
		if (jdkMan == null) {
			jdkMan = JavaUtil.defaultJdkManager(jdkProviders, jdkDistros, jdkInstaller);
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
		if (jdkDistros != null) {
			for (String d : jdkDistros) {
				opts.add("--jdk-distros");
				opts.add(d);
			}
		}
		if (jdkInstaller != null) {
			opts.add("--jdk-installer");
			opts.add(jdkInstaller);
		}
		return opts;
	}
}
