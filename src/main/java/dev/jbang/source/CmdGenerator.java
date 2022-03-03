package dev.jbang.source;

import java.io.IOException;

public interface CmdGenerator {
	String generate(Code code, RunContext ctx) throws IOException;
}
