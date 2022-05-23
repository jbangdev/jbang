package dev.jbang.source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
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
		Path res1 = Paths.get("resource.properties");
		Path res2 = Paths.get("sub/sub.properties");
		RunContext ctx = RunContext.empty();
		ctx.setAdditionalResources(Arrays.asList(
				Paths.get("res").resolve(res1).toString(),
				Paths.get("res").resolve(res2).toString()));
		SourceSet ss = (SourceSet) ctx.forResource(foo.toString());

		new JavaBuilder(ss, ctx) {
			@Override
			protected void runCompiler(List<String> optionList) {
				// Skip the compiler
			}

			@Override
			public void createJar() {
				assertThat(ss.getResources().size(), is(2));
				assertThat(ss.getResources().get(0).getSource().getFile().toPath().endsWith(res1), is(true));
				assertThat(ss.getResources().get(1).getSource().getFile().toPath().endsWith(res2), is(true));
			}
		}.setFresh(true).build();
	}

	@Test
	void testBuildAdditionalResourcesMounted() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path foo = Paths.get("foo.java");
		Path res1 = Paths.get("resource.properties");
		Path res2 = Paths.get("sub/sub.properties");
		RunContext ctx = RunContext.empty();
		ctx.setAdditionalResources(Arrays.asList(
				"somedir/=" + Paths.get("res").resolve(res1),
				"somedir/=" + Paths.get("res").resolve(res2)));
		SourceSet ss = (SourceSet) ctx.forResource(foo.toString());

		new JavaBuilder(ss, ctx) {
			@Override
			protected void runCompiler(List<String> optionList) {
				// Skip the compiler
			}

			@Override
			public void createJar() {
				assertThat(ss.getResources().size(), is(2));
				assertThat(ss.getResources().get(0).getTarget().toString(),
						is("somedir" + File.separator + "resource.properties"));
				assertThat(ss.getResources().get(0).getSource().getFile().toPath().endsWith(res1), is(true));
				assertThat(ss.getResources().get(1).getTarget().toString(),
						is("somedir" + File.separator + "sub.properties"));
				assertThat(ss.getResources().get(1).getSource().getFile().toPath().endsWith(res2), is(true));
			}
		}.setFresh(true).build();
	}
}
