package dev.jbang.source;

import java.io.IOException;

public interface Builder {
	Source build(ScriptSource src, RunContext ctx) throws IOException;
}
