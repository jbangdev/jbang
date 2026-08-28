package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

public class TestCompletion extends BaseTest {

	@Test
	void testFishCompletionIncludesKeepOrder() throws Exception {
		CaptureResult<Integer> result = checkedRun("completion", "-s", "fish");
		assertThat(result.result, org.hamcrest.Matchers.equalTo(BaseCommand.EXIT_OK));
		assertThat(result.normalizedOut(), containsString("complete -c jbang -f -k -a"));
	}

	@Test
	void testBashCompletionUnchanged() throws Exception {
		CaptureResult<Integer> result = checkedRun("completion", "-s", "bash");
		assertThat(result.result, org.hamcrest.Matchers.equalTo(BaseCommand.EXIT_OK));
		assertThat(result.normalizedOut(), containsString("complete -F _complete_jbang jbang"));
		assertThat(result.normalizedOut(), not(containsString("-k")));
	}

	@Test
	void testZshCompletionUnchanged() throws Exception {
		CaptureResult<Integer> result = checkedRun("completion", "-s", "zsh");
		assertThat(result.result, org.hamcrest.Matchers.equalTo(BaseCommand.EXIT_OK));
		assertThat(result.normalizedOut(), containsString("_jbang"));
		assertThat(result.normalizedOut(), not(containsString("-k")));
	}
}
