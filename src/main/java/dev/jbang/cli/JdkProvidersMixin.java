package dev.jbang.cli;

import java.util.ArrayList;
import java.util.List;

import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.aesh.command.option.OptionVisibility;

import dev.jbang.devkitman.JdkManager;
import dev.jbang.util.JavaUtil;

public class JdkProvidersMixin {

	@OptionList(name = "jdk-providers", valueSeparator = ',', visibility = OptionVisibility.HIDDEN, description = "Use the given providers to check for installed JDKs")
	List<String> jdkProviders;

	@OptionList(name = "jdk-distros", valueSeparator = ',', visibility = OptionVisibility.HIDDEN, description = "Use the given distributions to install new JDKs")
	List<String> jdkDistros;

	@Option(name = "jdk-installer", visibility = OptionVisibility.HIDDEN, description = "Use the given installer to install new JDKs")
	String jdkInstaller;

	private JdkManager jdkMan;

	public List<String> getJdkProviders() {
		return jdkProviders;
	}

	public JdkManager getJdkManager() {
		if (jdkMan == null) {
			jdkMan = JavaUtil.defaultJdkManager(jdkProviders, jdkDistros, jdkInstaller);
		}
		return jdkMan;
	}

	public List<String> opts() {
		List<String> opts = new ArrayList<>();
		if (jdkProviders != null) {
			opts.add("--jdk-providers");
			opts.add(String.join(",", jdkProviders));
		}
		if (jdkDistros != null) {
			opts.add("--jdk-distros");
			opts.add(String.join(",", jdkDistros));
		}
		if (jdkInstaller != null) {
			opts.add("--jdk-installer");
			opts.add(jdkInstaller);
		}
		return opts;
	}
}
