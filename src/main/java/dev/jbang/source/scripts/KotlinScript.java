package dev.jbang.source.scripts;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import dev.jbang.net.KotlinManager;
import dev.jbang.source.ResourceRef;
import dev.jbang.source.RunContext;
import dev.jbang.source.Script;
import dev.jbang.source.SourceSet;
import dev.jbang.source.builders.BaseBuilder;
import dev.jbang.source.builders.KotlinBuilder;

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
	public BaseBuilder getBuilder(SourceSet ss, RunContext ctx) {
		return new KotlinBuilder(ss, ctx);
	}

	public String getKotlinVersion() {
		return collectOptions("KOTLIN")
										.stream()
										.findFirst()
										.orElse(KotlinManager.DEFAULT_KOTLIN_VERSION);
	}
}
