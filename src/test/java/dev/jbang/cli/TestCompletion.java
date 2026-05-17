package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

public class TestCompletion {

	@Test
	void testFixFishCompletionScriptReplacesFunction() {
		String broken = "# header\n"
				+ "\n"
				+ "function __jbang_complete\n"
				+ "    set -l tokens (commandline -cop)\n"
				+ "    if string match -qr '\\s$' -- (commandline -cp)\n"
				+ "        set tokens $tokens ''\n"
				+ "    end\n"
				+ "    jbang --aesh-complete -- $tokens[2..]\n"
				+ "end\n"
				+ "\n"
				+ "complete -c jbang -f -a '(__jbang_complete)'\n";

		String fixed = Completion.fixFishCompletionScript(broken);

		assertThat(fixed, containsString("commandline -ct"));
		assertThat(fixed, containsString("$tokens[2..] $current"));
		assertThat(fixed, not(containsString("string match")));
		assertThat(fixed, containsString("complete -c jbang"));
		assertThat(fixed, containsString("# header"));
	}

	@Test
	void testFixFishCompletionScriptPreservesUnrecognised() {
		String unknown = "# some other script\necho hello\n";
		String result = Completion.fixFishCompletionScript(unknown);
		assertThat(result, is(unknown));
	}
}
