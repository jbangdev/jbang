package dev.jbang.source.scripts;

import static dev.jbang.util.JavaUtil.resolveInJavaHome;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;

import dev.jbang.source.JarBuilder;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.Script;

public class JavaScript extends Script {

	public JavaScript(String script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	public JavaScript(ResourceRef script, Function<String, String> replaceProperties) {
		super(script, replaceProperties);
	}

	protected JavaScript(ResourceRef ref, String script, Function<String, String> replaceProperties) {
		super(ref, script, replaceProperties);
	}

	@Override
	public List<String> getCompileOptions() {
		return collectOptions("JAVAC_OPTIONS");
	}

	@Override
	public List<String> getRuntimeOptions() {
		return collectOptions("JAVA_OPTIONS");
	}

	@Override
	public String getCompilerBinary(String requestedJavaVersion) {
		return resolveInJavaHome("javac", requestedJavaVersion);
	}

	@Override
	public Predicate<ClassInfo> getMainFinder() {
		return pubClass -> pubClass.method("main", JarBuilder.STRINGARRAYTYPE) != null;
	}

	@Override
	protected String getMainExtension() {
		return ".java";
	}
}
