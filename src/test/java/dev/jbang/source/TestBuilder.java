package dev.jbang.source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;

public class TestBuilder extends BaseTest {

	@Test
	void testBuildAdditionalSources() throws IOException {
		Path foo = examplesTestFolder.resolve("foo.java").toAbsolutePath();
		Path bar = examplesTestFolder.resolve("bar/Bar.java").toAbsolutePath();
		RunContext ctx = RunContext.empty();
		ctx.setAdditionalSources(Arrays.asList(bar.toString()));
		SourceSet ss = ctx.createSourceSet(foo.toString());

		Builder b = new JarBuilder() {
			@Override
			protected void runCompiler(SourceSet ss, String requestedJavaVersion, List<String> optionList)
					throws IOException {
				assertThat(optionList, hasItem(foo.toString()));
				assertThat(optionList, hasItem(bar.toString()));
				// Skip the compiler
			}
		}.setFresh(true);
		b.build(ss, ctx);
	}
}
