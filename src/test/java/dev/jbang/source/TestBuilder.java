package dev.jbang.source;

import static dev.jbang.util.Util.readString;
import static dev.jbang.util.Util.writeString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.source.builders.JavaBuilder;
import dev.jbang.util.Util;

public class TestBuilder extends BaseTest {

	@Test
	void testBuildAdditionalSources() throws IOException {
		Path foo = examplesTestFolder.resolve("foo.java").toAbsolutePath();
		Path bar = examplesTestFolder.resolve("bar/Bar.java").toAbsolutePath();
		RunContext ctx = RunContext.empty();
		ctx.setAdditionalSources(Arrays.asList(bar.toString()));
		Project prj = ctx.forResource(foo.toString());

		new JavaBuilder(prj) {
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
	void testAdditionalSourcesUsingAlias() throws IOException {
		String mainFile = examplesTestFolder.resolve("foo.java").toString();
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		CatalogUtil.addNearestAlias("bar", incFile, null, null, null, null, null, null, null, null, null, null, null);

		RunContext ctx = RunContext.empty();
		ctx.setAdditionalSources(Arrays.asList("bar"));
		Project prj = ctx.forResource(mainFile);

		new JavaBuilder(prj) {
			@Override
			protected void runCompiler(List<String> optionList)
					throws IOException {
				assertThat(optionList, hasItem(mainFile));
				assertThat(optionList, hasItem(incFile));
				// Skip the compiler
			}
		}.setFresh(true).build();
	}

	@Test
	void testIncludedSourcesUsingAlias(@TempDir Path dir) throws IOException {
		Path mainFile = dir.resolve("foo.java");
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		Path fooFile = examplesTestFolder.resolve("foo.java");
		String fooScript = readString(fooFile);
		writeString(mainFile, "//SOURCES bar@" + jbangTempDir + "\n" + fooScript);

		CatalogUtil.addNearestAlias("bar", incFile, null, null, null, null, null, null, null, null, null, null, null);

		RunContext ctx = RunContext.empty();
		Project prj = ctx.forResource(mainFile.toString());

		new JavaBuilder(prj) {
			@Override
			protected void runCompiler(List<String> optionList)
					throws IOException {
				assertThat(optionList, hasItem(mainFile.toString()));
				assertThat(optionList, hasItem(incFile));
				// Skip the compiler
			}
		}.setFresh(true).build();
	}

	@Test
	void testAdditionalSourcesGlobbing() throws IOException {
		Util.setCwd(examplesTestFolder);
		String mainFile = examplesTestFolder.resolve("foo.java").toString();
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		RunContext ctx = RunContext.empty();
		ctx.setAdditionalSources(Arrays.asList("bar/*.java"));
		Project prj = ctx.forResource(mainFile);

		new JavaBuilder(prj) {
			@Override
			protected void runCompiler(List<String> optionList)
					throws IOException {
				assertThat(optionList, hasItem(mainFile));
				assertThat(optionList, hasItem(incFile));
				// Skip the compiler
			}
		}.setFresh(true).build();
	}

	@Test
	void testAdditionalSourcesAbsGlobbing() throws IOException {
		String mainFile = examplesTestFolder.resolve("foo.java").toString();
		String incGlob = examplesTestFolder.resolve("bar").toString() + File.separatorChar + "*.java";
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		RunContext ctx = RunContext.empty();
		ctx.setAdditionalSources(Arrays.asList(incGlob));
		Project prj = ctx.forResource(mainFile);

		new JavaBuilder(prj) {
			@Override
			protected void runCompiler(List<String> optionList)
					throws IOException {
				assertThat(optionList, hasItem(mainFile));
				assertThat(optionList, hasItem(incFile));
				// Skip the compiler
			}
		}.setFresh(true).build();
	}

	@Test
	void testAdditionalSourcesFolder() throws IOException {
		Util.setCwd(examplesTestFolder);
		String mainFile = examplesTestFolder.resolve("foo.java").toString();
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		RunContext ctx = RunContext.empty();
		ctx.setAdditionalSources(Arrays.asList("bar"));
		Project prj = ctx.forResource(mainFile);

		new JavaBuilder(prj) {
			@Override
			protected void runCompiler(List<String> optionList)
					throws IOException {
				assertThat(optionList, hasItem(mainFile));
				assertThat(optionList, hasItem(incFile));
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
		Project prj = ctx.forResource(foo.toString());

		new JavaBuilder(prj) {
			@Override
			protected void runCompiler(List<String> optionList) {
				// Skip the compiler
			}

			@Override
			public void createJar() {
				assertThat(prj.getMainSourceSet().getResources().size(), is(2));
				assertThat(prj.getMainSourceSet().getResources().get(0).getSource().getFile().endsWith(res1), is(true));
				assertThat(prj.getMainSourceSet().getResources().get(1).getSource().getFile().endsWith(res2), is(true));
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
		Project prj = ctx.forResource(foo.toString());

		new JavaBuilder(prj) {
			@Override
			protected void runCompiler(List<String> optionList) {
				// Skip the compiler
			}

			@Override
			public void createJar() {
				assertThat(prj.getMainSourceSet().getResources().size(), is(2));
				assertThat(prj.getMainSourceSet().getResources().get(0).getTarget().toString(),
						is("somedir" + File.separator + "resource.properties"));
				assertThat(prj.getMainSourceSet().getResources().get(0).getSource().getFile().endsWith(res1), is(true));
				assertThat(prj.getMainSourceSet().getResources().get(1).getTarget().toString(),
						is("somedir" + File.separator + "sub.properties"));
				assertThat(prj.getMainSourceSet().getResources().get(1).getSource().getFile().endsWith(res2), is(true));
			}
		}.setFresh(true).build();
	}

	@Test
	void testAdditionalResourcesGlobbing() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path foo = Paths.get("foo.java");
		Path res1 = Paths.get("resource.properties");
		Path res2 = Paths.get("sub/sub.properties");
		RunContext ctx = RunContext.empty();
		ctx.setAdditionalResources(Arrays.asList("res/**.properties"));
		Project prj = ctx.forResource(foo.toString());

		new JavaBuilder(prj) {
			@Override
			protected void runCompiler(List<String> optionList) {
				assertThat(optionList, hasItem(endsWith(File.separator + "foo.java")));
				// Skip the compiler
			}

			@Override
			public void createJar() throws IOException {
				assertThat(prj.getMainSourceSet().getResources().size(), is(3));
				List<String> ps = prj	.getMainSourceSet()
										.getResources()
										.stream()
										.map(r -> r.getSource().getFile().toString())
										.collect(Collectors.toList());
				assertThat(ps, hasItem(endsWith("resource.properties")));
				assertThat(ps, hasItem(endsWith("test.properties")));
				assertThat(ps, hasItem(endsWith("sub" + File.separator + "sub.properties")));
			}
		}.setFresh(true).build();
	}

	@Test
	void testAdditionalResourcesFolder() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path foo = Paths.get("foo.java");
		Path res1 = Paths.get("resource.properties");
		Path res2 = Paths.get("sub/sub.properties");
		RunContext ctx = RunContext.empty();
		ctx.setAdditionalResources(Arrays.asList("res"));
		Project prj = ctx.forResource(foo.toString());

		new JavaBuilder(prj) {
			@Override
			protected void runCompiler(List<String> optionList) {
				assertThat(optionList, hasItem(endsWith(File.separator + "foo.java")));
				// Skip the compiler
			}

			@Override
			public void createJar() throws IOException {
				assertThat(prj.getMainSourceSet().getResources().size(), is(4));
				List<String> ps = prj	.getMainSourceSet()
										.getResources()
										.stream()
										.map(r -> r.getSource().getFile().toString())
										.collect(Collectors.toList());
				assertThat(ps, hasItem(endsWith("resource.java")));
				assertThat(ps, hasItem(endsWith("resource.properties")));
				assertThat(ps, hasItem(endsWith("test.properties")));
				assertThat(ps, hasItem(endsWith("sub" + File.separator + "sub.properties")));
			}
		}.setFresh(true).build();
	}

	@Test
	void testNativeOutputName() throws IOException {
		Util.setCwd(examplesTestFolder);
		String mainFile = examplesTestFolder.resolve("foo.java").toString();

		RunContext ctx = RunContext.empty();
		ctx.setNativeImage(true);
		Project prj = ctx.forResource(mainFile);

		new JavaBuilder(prj) {
			@Override
			protected void runCompiler(List<String> optionList) {
			}

			@Override
			protected void runNativeBuilder(List<String> optionList) throws IOException {
				if (Util.isWindows()) {
					assertThat(optionList.get(optionList.size() - 1), not(endsWith(".exe")));
				} else {
					assertThat(optionList.get(optionList.size() - 1), endsWith(".bin"));
				}
			}
		}.setFresh(true).build();
	}

	@Test
	void testManifest() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path mainFile = examplesTestFolder.resolve("helloworld.java");

		RunContext ctx = RunContext.empty();
		Project prj = ctx.forResource(mainFile.toFile().getAbsolutePath());

		prj.builder().build();

		assertThat(prj.getManifestAttributes().get("foo"), is("true"));
		assertThat(prj.getManifestAttributes().get("bar"), is("baz"));

		try (JarFile jf = new JarFile(prj.getJarFile().toFile())) {
			Attributes attrs = jf.getManifest().getMainAttributes();
			assertThat(attrs.getValue("foo"), equalTo("true"));
			assertThat(attrs.getValue("bar"), equalTo("baz"));
		}

	}
}
