package dev.jbang.source;

import java.io.IOException;

public interface CmdGenerator {
	String generate(Source src, RunContext ctx) throws IOException;
}
