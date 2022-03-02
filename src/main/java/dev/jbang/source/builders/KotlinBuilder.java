package dev.jbang.source.builders;

import static dev.jbang.net.KotlinManager.resolveInKotlinHome;

import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;

import dev.jbang.source.RunContext;
import dev.jbang.source.SourceSet;
import dev.jbang.source.scripts.KotlinScript;

public class KotlinBuilder extends BaseBuilder {

	public KotlinBuilder(SourceSet ss, RunContext ctx) {
		super(ss, ctx);
	}

	@Override
	protected String getCompilerBinary(String requestedJavaVersion) {
		return resolveInKotlinHome("kotlinc", ((KotlinScript) ss.getMainSource()).getKotlinVersion());
	}

	@Override
	protected Predicate<ClassInfo> getMainFinder() {
		return pubClass -> pubClass.method("main", BaseBuilder.STRINGARRAYTYPE) != null
				|| pubClass.method("main") != null;
	}

	@Override
	protected String getMainExtension() {
		return ".kt";
	}
}
