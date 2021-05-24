package dev.jbang.source;

import static dev.jbang.net.KotlinManager.resolveInKotlinHome;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;

import dev.jbang.cli.BaseBuildCommand;
import dev.jbang.net.KotlinManager;

public class KotlinScriptSource extends ScriptSource {

	protected KotlinScriptSource(ResourceRef script) {
		super(script);
	}

	@Override
	public List<String> getCompileOptions() {
		return Collections.emptyList();
	}

	@Override
	public String getCompilerBinary(String requestedJavaVersion) {
		return resolveInKotlinHome("kotlinc", getKotlinVersion());
	}

	@Override
	public Predicate<ClassInfo> getMainFinder() {
		return pubClass -> pubClass.method("main", BaseBuildCommand.STRINGARRAYTYPE) != null
				|| pubClass.method("main") != null;
	}

	@Override
	protected String getMainExtension() {
		return ".kt";
	}

	public String getKotlinVersion() {
		return collectAll((s) -> collectOptions("KOTLIN"))
															.stream()
															.findFirst()
															.orElse(KotlinManager.DEFAULT_KOTLIN_VERSION);
	}
}
