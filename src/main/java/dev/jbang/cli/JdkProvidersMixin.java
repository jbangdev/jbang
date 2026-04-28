package dev.jbang.cli;

import java.util.List;

import org.aesh.command.option.OptionList;
import org.aesh.command.option.OptionVisibility;

import dev.jbang.devkitman.JdkManager;
import dev.jbang.util.JavaUtil;

public class JdkProvidersMixin {

	@OptionList(name = "jdk-providers", valueSeparator = ',', visibility = OptionVisibility.HIDDEN, description = "Use the given providers to check for installed JDKs")
	List<String> jdkProviders;

	private JdkManager jdkMan;

	public List<String> getJdkProviders() {
		return jdkProviders;
	}

	public JdkManager getJdkManager() {
		if (jdkMan == null) {
			jdkMan = JavaUtil.defaultJdkManager(jdkProviders);
		}
		return jdkMan;
	}
}
