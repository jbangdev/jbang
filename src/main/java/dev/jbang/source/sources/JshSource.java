package dev.jbang.source.sources;

import java.util.Properties;

import org.jspecify.annotations.NonNull;

import dev.jbang.resources.ResourceRef;
import dev.jbang.source.*;

public class JshSource extends JavaSource {
	public JshSource(ResourceRef script, Properties properties) {
		super(script, properties);
	}

	protected JshSource(ResourceRef ref, String script, Properties properties) {
		super(ref, script, properties);
	}

	@Override
	public @NonNull Type getType() {
		return Type.jshell;
	}

	@Override
	public Builder<CmdGeneratorBuilder> getBuilder(BuildContext ctx) {
		return () -> CmdGenerator.builder(ctx);
	}
}
