package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

class TestSourceType extends BaseTest {

	static String code = "        ///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n" +
			"        //JAVA 21+\n" +
			"        //PREVIEW\n" +
			"        void main(String[] args) {\n" +
			"        	System.out.println(\"Providers: \" );\n" +
			"        }\n";

	@Test
	void stdinHonoursForceType() throws Exception {
		// Feed some trivial Java code via stdin
		System.setIn(new ByteArrayInputStream(code.getBytes()));

		CaptureResult<Integer> res = checkedRun(null,
				"run", "--source-type=java", "--enable-preview", "--verbose", "-");

		// expect the JShell pipeline NOT to be used
		assertThat(res.err, not(containsString(".jsh")));
	}

	@Test
	void forceJavaOnJshFile() throws Exception {
		// create a .jsh file
		Path p = cwdDir.resolve("test.jsh");
		dev.jbang.util.Util.writeString(p,
				"class Test { public static void main(String...a) { } }");

		CaptureResult<Integer> res = checkedRun(null,
				"run", "--source-type=java", "--verbose", p.toString());

		// current (broken) behaviour emits "test.jsh.jar".
		// Once fixed, that message must disappear and the run should succeed.
		assertThat(res.err, not(containsString("test.jsh.jar")));
		assertThat(res.result, equalTo(BaseCommand.EXIT_EXECUTE));
	}
}
