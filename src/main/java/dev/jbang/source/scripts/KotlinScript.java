package dev.jbang.source.scripts;

import static dev.jbang.net.KotlinManager.resolveInKotlinHome;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;

import dev.jbang.net.KotlinManager;
import dev.jbang.source.JarBuilder;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.Script;

public class KotlinScript extends Script {

	public KotlinScript(ResourceRef script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	@Override
	public List<String> getCompileOptions() {
		return Collections.emptyList();
	}

	@Override
	public List<String> getRuntimeOptions() {
		return collectOptions("JAVA_OPTIONS");
	}

	@Override
	public String getCompilerBinary(String requestedJavaVersion) {
		return resolveInKotlinHome("kotlinc", getKotlinVersion());
	}

	@Override
	public Predicate<ClassInfo> getMainFinder() {
		return pubClass -> pubClass.method("main", JarBuilder.STRINGARRAYTYPE) != null
				|| pubClass.method("main") != null;
	}

	@Override
	protected String getMainExtension() {
		return ".kt";
	}

	public String getKotlinVersion() {
		return collectOptions("KOTLIN")
										.stream()
										.findFirst()
										.orElse(KotlinManager.DEFAULT_KOTLIN_VERSION);
	}
}
