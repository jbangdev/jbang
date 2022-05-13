package dev.jbang.source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.source.builders.JavaBuilder;
import dev.jbang.util.Util;

public class TestBuilder extends BaseTest {

	@Test
	void testBuildAdditionalSources() throws IOException {
		Path foo = examplesTestFolder.resolve("foo.java").toAbsolutePath();
		Path bar = examplesTestFolder.resolve("bar/Bar.java").toAbsolutePath();
		RunContext ctx = RunContext.empty();
		ctx.setAdditionalSources(Arrays.asList(bar.toString()));
		SourceSet ss = (SourceSet) ctx.forResource(foo.toString());

		new JavaBuilder(ss, ctx) {
			@Override
			protected void runCompiler(List<String> optionList)
					throws IOException {
				assertThat(optionList, hasItem(foo.toString()));
				assertThat(optionList, hasItem(bar.toString()));
				// Skip the compiler
			}
		}.setFresh(true).build();
	}

	@Test
	void testBuildAdditionalResources() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path foo = Paths.get("foo.java");
		Path res = Paths.get("res/resource.properties");
		RunContext ctx = RunContext.empty();
		ctx.setAdditionalResources(Arrays.asList(res.toString()));
		SourceSet ss = (SourceSet) ctx.forResource(foo.toString());

		new JavaBuilder(ss, ctx) {
			@Override
			protected void runCompiler(List<String> optionList) throws IOException {
				// Skip the compiler
			}

			@Override
			public void createJar() throws IOException {
				assertThat(ss.getResources().size(), is(1));
				assertThat(ss.getResources().get(0).getSource().getFile().toPath().endsWith(res), is(true));
			}
		}.setFresh(true).build();
	}

	@Test
	void testBuildAdditionalResourcesMounted() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path foo = Paths.get("foo.java");
		Path res = Paths.get("res/resource.properties");
		RunContext ctx = RunContext.empty();
		ctx.setAdditionalResources(Arrays.asList("somedir=" + res.toString()));
		SourceSet ss = (SourceSet) ctx.forResource(foo.toString());

		new JavaBuilder(ss, ctx) {
			@Override
			protected void runCompiler(List<String> optionList) throws IOException {
				// Skip the compiler
			}

			@Override
			public void createJar() throws IOException {
				assertThat(ss.getResources().size(), is(1));
				assertThat(ss.getResources().get(0).getTarget().toString(), is("somedir"));
				assertThat(ss.getResources().get(0).getSource().getFile().toPath().endsWith(res), is(true));
			}
		}.setFresh(true).build();
	}
}
