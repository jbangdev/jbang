package dev.jbang.source;

import java.io.IOException;

public interface Builder {
	Input build(SourceSet ss, RunContext ctx) throws IOException;
}
