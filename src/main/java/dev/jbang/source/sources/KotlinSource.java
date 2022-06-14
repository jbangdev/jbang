package dev.jbang.source.sources;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import dev.jbang.net.KotlinManager;
import dev.jbang.source.*;
import dev.jbang.source.builders.KotlinBuilder;

public class KotlinSource extends Source {

	public KotlinSource(ResourceRef script, Function<String, String> replaceProperties) {
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
	public Builder getBuilder(Project prj) {
		return new KotlinBuilder(prj);
	}

	public String getKotlinVersion() {
		return collectOptions("KOTLIN")
										.stream()
										.findFirst()
										.orElse(KotlinManager.DEFAULT_KOTLIN_VERSION);
	}
}
