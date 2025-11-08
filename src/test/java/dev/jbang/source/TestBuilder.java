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
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.Settings;
import dev.jbang.catalog.Alias;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.cli.ExitException;
import dev.jbang.source.buildsteps.IntegrationBuildStep;
import dev.jbang.source.buildsteps.JarBuildStep;
import dev.jbang.source.buildsteps.NativeBuildStep;
import dev.jbang.source.sources.JavaSource;
import dev.jbang.spi.IntegrationResult;
import dev.jbang.util.Util;

public class TestBuilder extends BaseTest {

	@Test
	void testHelloworld() throws IOException {
		Path foo = examplesTestFolder.resolve("helloworld.java").toAbsolutePath();
		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		AtomicBoolean integrationStepCalled = new AtomicBoolean(false);
		AtomicBoolean jarStepCalled = new AtomicBoolean(false);
		AtomicBoolean nativeStepCalled = new AtomicBoolean(false);
		runBuild(ctx, (ctxx, optionList) -> assertThat(optionList, hasItems(foo.toString(), "-g")), (ctxx) -> {
			integrationStepCalled.set(true);
			return new IntegrationResult(null, null, null);
		}, (ctxx) -> {
			jarStepCalled.set(true);
			return ctxx.getProject();
		}, (ctxx, nativeStep) -> {
			nativeStepCalled.set(true);
		});
		assertThat(integrationStepCalled.get(), is(true));
		assertThat(jarStepCalled.get(), is(true));
		assertThat(nativeStepCalled.get(), is(false));
	}

	@Test
	void testEnablePreview() throws IOException {
		Path foo = examplesTestFolder.resolve("helloworld.java").toAbsolutePath();
		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(foo.toString());
		prj.setEnablePreviewRequested(true);
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> assertThat(optionList, hasItems(foo.toString(), "--enable-preview")), null,
				null, null);
	}

	@Test
	void testDualHelloworld(@TempDir File out1, @TempDir File out2) throws IOException {
		Path foo = examplesTestFolder.resolve("helloworld.java").toAbsolutePath();
		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(foo.toString());
		BuildContext ctx1 = BuildContext.forProject(prj, out1.toPath());
		BuildContext ctx2 = BuildContext.forProject(prj, out2.toPath());
		runBuild(ctx1, (ctxx, optionList) -> {
			assertThat(optionList, hasItems(containsString(out1.toString())));
			assertThat(optionList, not(hasItems(containsString(out2.toString()))));
			assertThat(optionList, not(hasItems(containsString(Settings.getCacheDir().toString()))));
		}, null, ctxx -> {
			assertThat(ctxx.getJarFile().toString(), containsString(out1.toString()));
			assertThat(ctxx.getJarFile().toString(), not(containsString(out2.toString())));
			assertThat(ctxx.getJarFile().toString(), not(containsString(Settings.getCacheDir().toString())));
			return ctxx.getProject();
		}, null);
		runBuild(ctx2, (ctxx, optionList) -> {
			assertThat(optionList, hasItems(containsString(out2.toString())));
			assertThat(optionList, not(hasItems(containsString(out1.toString()))));
			assertThat(optionList, not(hasItems(containsString(Settings.getCacheDir().toString()))));
		}, null, ctxx -> {
			assertThat(ctxx.getJarFile().toString(), containsString(out2.toString()));
			assertThat(ctxx.getJarFile().toString(), not(containsString(out1.toString())));
			assertThat(ctxx.getJarFile().toString(), not(containsString(Settings.getCacheDir().toString())));
			return ctxx.getProject();
		}, null);
	}

	@Test
	void testCompileOptions() throws IOException {
		Path foo = examplesTestFolder.resolve("helloworld.java").toAbsolutePath();
		ProjectBuilder pb = Project.builder().compileOptions(Arrays.asList("--foo", "--bar"));
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> assertThat(optionList, hasItems(foo.toString(), "-g", "--foo", "--bar")),
				null, null, null);
	}

	@Test
	void testKotlinCompileOptions() throws IOException {
		Path foo = examplesTestFolder.resolve("helloworld.kt").toAbsolutePath();
		ProjectBuilder pb = Project.builder().compileOptions(Arrays.asList("--foo", "--bar"));
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> assertThat(optionList, hasItems(foo.toString(), "--foo", "--bar")), null,
				null, null);
	}

	@Test
	void testBuildAdditionalSources() throws IOException {
		Path foo = examplesTestFolder.resolve("foo.java").toAbsolutePath();
		Path bar = examplesTestFolder.resolve("bar/Bar.java").toAbsolutePath();
		ProjectBuilder pb = Project.builder();
		pb.additionalSources(Arrays.asList(bar.toString()));
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> {
			assertThat(optionList, hasItem(foo.toString()));
			assertThat(optionList, hasItem(bar.toString()));
		}, null, null, null);
	}

	@Test
	void testAdditionalSourcesUsingAlias() throws IOException {
		String mainFile = examplesTestFolder.resolve("foo.java").toString();
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		CatalogUtil.addNearestAlias("bar", new Alias().withScriptRef(incFile));

		ProjectBuilder pb = Project.builder();
		pb.additionalSources(Arrays.asList("bar"));
		Project prj = pb.build(mainFile);
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> {
			assertThat(optionList, hasItem(mainFile));
			assertThat(optionList, hasItem(incFile));
		}, null, null, null);
	}

	@Test
	void testAliasInvalidSourceType() throws IOException {
		String mainFile = examplesTestFolder.resolve("helloworld.java").toString();

		CatalogUtil.addNearestAlias("bar", new Alias().withScriptRef(mainFile).withForceType("INVALID"));

		ProjectBuilder pb = Project.builder();
		try {
			pb.build("bar");
		} catch (IllegalArgumentException ex) {
			assertThat(ex.getMessage(), containsString("No enum constant dev.jbang.source.Source.Type.INVALID"));
		}
	}

	@Test
	void testIncludedSourcesUsingAlias(@TempDir Path dir) throws IOException {
		Path mainFile = dir.resolve("foo.java");
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		Path fooFile = examplesTestFolder.resolve("foo.java");
		String fooScript = readString(fooFile);
		writeString(mainFile, "//SOURCES bar@" + jbangTempDir + "\n" + fooScript);

		CatalogUtil.addNearestAlias("bar", new Alias().withScriptRef(incFile));

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(mainFile.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> {
			assertThat(optionList, hasItem(mainFile.toString()));
			assertThat(optionList, hasItem(incFile));
		}, null, null, null);
	}

	@Test
	void testAdditionalSourcesGlobbing() throws IOException {
		Util.setCwd(examplesTestFolder);
		String mainFile = examplesTestFolder.resolve("foo.java").toString();
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		ProjectBuilder pb = Project.builder();
		pb.additionalSources(Arrays.asList("bar/*.java"));
		Project prj = pb.build(mainFile);
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> {
			assertThat(optionList, hasItem(mainFile));
			assertThat(optionList, hasItem(incFile));
		}, null, null, null);
	}

	@Test
	void testAdditionalSourcesAbsGlobbing() throws IOException {
		String mainFile = examplesTestFolder.resolve("foo.java").toString();
		String incGlob = examplesTestFolder.resolve("bar").toString() + File.separatorChar + "*.java";
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		ProjectBuilder pb = Project.builder();
		pb.additionalSources(Arrays.asList(incGlob));
		Project prj = pb.build(mainFile);
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> {
			assertThat(optionList, hasItem(mainFile));
			assertThat(optionList, hasItem(incFile));
		}, null, null, null);
	}

	@Test
	void testAdditionalSourcesFolder() throws IOException {
		Util.setCwd(examplesTestFolder);
		String mainFile = examplesTestFolder.resolve("foo.java").toString();
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		ProjectBuilder pb = Project.builder();
		pb.additionalSources(Arrays.asList("bar"));
		Project prj = pb.build(mainFile);
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> {
			assertThat(optionList, hasItem(mainFile));
			assertThat(optionList, hasItem(incFile));
		}, null, null, null);
	}

	@Test
	void testBuildAdditionalResources() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path foo = Paths.get("foo.java");
		Path res1 = Paths.get("resource.properties");
		Path res2 = Paths.get("sub/sub.properties");
		ProjectBuilder pb = Project.builder();
		pb.additionalResources(Arrays.asList(
				Paths.get("res").resolve(res1).toString(),
				Paths.get("res").resolve(res2).toString()));
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> {
		}, null, ctxx -> {
			Project project = ctxx.getProject();
			assertThat(project.getMainSourceSet().getResources().size(), is(2));
			assertThat(
					project.getMainSourceSet().getResources().get(0).getSource().getFile().endsWith(res1),
					is(true));
			assertThat(
					project.getMainSourceSet().getResources().get(1).getSource().getFile().endsWith(res2),
					is(true));
			return project;
		}, null);
	}

	@Test
	void testBuildAdditionalResourcesMounted() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path foo = Paths.get("foo.java");
		Path res1 = Paths.get("resource.properties");
		Path res2 = Paths.get("sub/sub.properties");
		ProjectBuilder pb = Project.builder();
		pb.additionalResources(Arrays.asList(
				"somedir/=" + Paths.get("res").resolve(res1),
				"somedir/=" + Paths.get("res").resolve(res2)));
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> {
		}, null, ctxx -> {
			Project project = ctxx.getProject();
			assertThat(project.getMainSourceSet().getResources().size(), is(2));
			assertThat(project.getMainSourceSet().getResources().get(0).getTarget().toString(),
					is("somedir" + File.separator + "resource.properties"));
			assertThat(
					project.getMainSourceSet().getResources().get(0).getSource().getFile().endsWith(res1),
					is(true));
			assertThat(project.getMainSourceSet().getResources().get(1).getTarget().toString(),
					is("somedir" + File.separator + "sub.properties"));
			assertThat(
					project.getMainSourceSet().getResources().get(1).getSource().getFile().endsWith(res2),
					is(true));
			return project;
		}, null);
	}

	@Test
	void testAdditionalResourcesGlobbing() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path foo = Paths.get("foo.java");
		ProjectBuilder pb = Project.builder();
		pb.additionalResources(Arrays.asList("res/**.properties"));
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> {
			assertThat(optionList, hasItem(endsWith(File.separator + "foo.java")));
		}, null, ctxx -> {
			Project project = ctxx.getProject();
			assertThat(project.getMainSourceSet().getResources().size(), is(3));
			List<String> ps = project.getMainSourceSet()
				.getResources()
				.stream()
				.map(r -> r.getSource().getFile().toString())
				.collect(Collectors.toList());
			assertThat(ps, hasItem(endsWith("resource.properties")));
			assertThat(ps, hasItem(endsWith("test.properties")));
			assertThat(ps, hasItem(endsWith("sub" + File.separator + "sub.properties")));
			return project;
		}, null);
	}

	@Test
	void testAdditionalResourcesFolder() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path foo = Paths.get("foo.java");
		ProjectBuilder pb = Project.builder();
		pb.additionalResources(Arrays.asList("res"));
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> {
			assertThat(optionList, hasItem(endsWith(File.separator + "foo.java")));
		}, null, ctxx -> {
			Project project = ctxx.getProject();
			assertThat(project.getMainSourceSet().getResources().size(), is(4));
			List<String> ps = project.getMainSourceSet()
				.getResources()
				.stream()
				.map(r -> r.getSource().getFile().toString())
				.collect(Collectors.toList());
			assertThat(ps, hasItem(endsWith("resource.java")));
			assertThat(ps, hasItem(endsWith("resource.properties")));
			assertThat(ps, hasItem(endsWith("test.properties")));
			assertThat(ps, hasItem(endsWith("sub" + File.separator + "sub.properties")));
			return project;
		}, null);
	}

	@Test
	void testNativeOutputName() throws IOException {
		Util.setCwd(examplesTestFolder);
		String mainFile = examplesTestFolder.resolve("foo.java").toString();

		ProjectBuilder pb = Project.builder();
		pb.nativeImage(true);
		Project prj = pb.build(mainFile);
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> {
			assertThat(optionList, hasItem(endsWith(File.separator + "foo.java")));
		}, null, null, (ctxx, optionList) -> {
			if (Util.isWindows()) {
				assertThat(optionList.get(optionList.size() - 1), not(endsWith(".exe")));
			} else {
				assertThat(optionList.get(optionList.size() - 1), endsWith(".bin"));
			}
		});
	}

	@Test
	void testManifest() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path mainFile = examplesTestFolder.resolve("helloworld.java");

		ProjectBuilder pb = Project.builder();
		HashMap<String, String> props = new HashMap<>();
		props.put("bazprop", "algo");
		pb.setProperties(props);
		Project prj = pb.build(mainFile.toFile().getAbsolutePath());
		BuildContext ctx = BuildContext.forProject(prj);
		Project.codeBuilder(ctx).build();

		assertThat(prj.getManifestAttributes().get("foo"), is("true"));
		assertThat(prj.getManifestAttributes().get("bar"), is("baz"));
		assertThat(prj.getManifestAttributes().get("baz"), is("algo"));

		try (JarFile jf = new JarFile(ctx.getJarFile().toFile())) {
			Attributes attrs = jf.getManifest().getMainAttributes();
			assertThat(attrs.getValue("foo"), equalTo("true"));
			assertThat(attrs.getValue("bar"), equalTo("baz"));
			assertThat(attrs.getValue("baz"), equalTo("algo"));
		}
	}

	@Test
	void testNative() throws IOException {
		Path foo = examplesTestFolder.resolve("helloworld.java").toAbsolutePath();
		ProjectBuilder pb = Project.builder().nativeImage(true);
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		runBuild(ctx, (ctxx, optionList) -> {
		}, null, null, (ctxx, optionList) -> assertThat(optionList, hasItem("-O1")));
	}

	@Test
	void testNativeFresh() throws IOException {
		Path foo = examplesTestFolder.resolve("helloworld.java").toAbsolutePath();
		ProjectBuilder pb = Project.builder().nativeImage(true);
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		AtomicInteger callCount = new AtomicInteger(0);
		runBuild(ctx, (ctxx, optionList) -> {
		}, null, null, (ctxx, optionList) -> {
			try {
				ctxx.getNativeImageFile().toFile().createNewFile();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			callCount.incrementAndGet();
		});
		runBuild(ctx, (ctxx, optionList) -> {
		}, null, null, (ctxx, optionList) -> callCount.incrementAndGet());
		assertThat(callCount.get(), equalTo(2));
	}

	@Test
	void testSourceDep() throws IOException {
		Path onedep = examplesTestFolder.resolve("onedep.java").toAbsolutePath();
		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(onedep.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		AtomicInteger callCount = new AtomicInteger(0);
		runBuild(ctx, (ctxx, optionList) -> {
			int cc = callCount.incrementAndGet();
			if (cc == 1) {
				assertThat(ctxx, not(is(ctx)));
				assertThat(ctxx.getProject().getSubProjects(), is(empty()));
				assertThat(ctxx.resolveClassPath().getArtifacts(), hasSize(7));
			} else if (cc == 2) {
				assertThat(ctxx, is(ctx));
				assertThat(ctxx.getProject().getSubProjects(), hasSize(1));
				assertThat(ctxx.resolveClassPath().getArtifacts(), hasSize(8));
				assertThat(ctxx.resolveClassPath().getClassPath(), containsString("Two.jar"));
			} else {
				throw new IllegalStateException("Should not be called more than twice!");
			}
		}, null, null, null);
		assertThat(callCount.get(), equalTo(2));
	}

	@Test
	void testSourceSelfDep() throws IOException {
		try {
			Path selfdep = examplesTestFolder.resolve("selfdep.java").toAbsolutePath();
			ProjectBuilder pb = Project.builder();
			pb.build(selfdep.toString());
		} catch (ExitException ex) {
			assertThat(ex.getMessage(), startsWith("Self-referencing project dependency found for:"));
		}
	}

	@Test
	void testNoIntegrationsFlag() throws IOException {
		Path foo = examplesTestFolder.resolve("helloworld.java").toAbsolutePath();
		ProjectBuilder pb = Project.builder();
		pb.integrations(false);
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);
		AtomicBoolean integrationStepCalled = new AtomicBoolean(false);
		runBuild(ctx, null, (ctxx) -> {
			integrationStepCalled.set(true);
			return new IntegrationResult(null, null, null);
		}, null, null);
		assertThat(integrationStepCalled.get(), is(false));
	}

	@Test
	void testNoIntegrationsTag() throws IOException {
		Path foo = examplesTestFolder.resolve("noints.java").toAbsolutePath();
		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(foo.toString());
		assertThat(prj.disableIntegrations(), is(true));
		BuildContext ctx = BuildContext.forProject(prj);
		AtomicBoolean integrationStepCalled = new AtomicBoolean(false);
		runBuild(ctx, null, (ctxx) -> {
			integrationStepCalled.set(true);
			return new IntegrationResult(null, null, null);
		}, null, null);
		assertThat(integrationStepCalled.get(), is(false));
	}

	private void runBuild(BuildContext ctx, BiConsumer<BuildContext, List<String>> compileStep,
			Function<BuildContext, IntegrationResult> integrationStep, Function<BuildContext, Project> jarStep,
			BiConsumer<BuildContext, List<String>> nativeStep)
			throws IOException {
		new CodeBuilderProvider(ctx) {
			@NonNull
			@Override
			protected Builder<CmdGeneratorBuilder> getBuilder(BuildContext ctx) {
				return new JavaSource.JavaAppBuilder(ctx) {
					@Override
					protected Builder<Project> getCompileBuildStep() {
						return new JavaCompileBuildStep() {
							@Override
							protected void runCompiler(List<String> optionList) throws IOException {
								if (compileStep != null) {
									compileStep.accept(ctx, optionList);
								} else {
									super.runCompiler(optionList);
								}
							}
						};
					}

					@Override
					protected Builder<IntegrationResult> getIntegrationBuildStep() {
						return new IntegrationBuildStep(ctx) {
							@Override
							public IntegrationResult build() throws IOException {
								if (integrationStep != null) {
									return integrationStep.apply(ctx);
								} else {
									return super.build();
								}
							}
						};
					}

					@Override
					protected Builder<Project> getJarBuildStep() {
						return new JarBuildStep(ctx) {
							@Override
							public Project build() throws IOException {
								if (jarStep != null) {
									return jarStep.apply(ctx);
								} else {
									return super.build();
								}
							}
						};
					}

					@Override
					protected Builder<Project> getNativeBuildStep() {
						return new NativeBuildStep(ctx) {
							@Override
							protected void runNativeBuilder(List<String> optionList) throws IOException {
								if (nativeStep != null) {
									nativeStep.accept(ctx, optionList);
								} else {
									super.runNativeBuilder(optionList);
								}
							}
						};
					}
				}.setFresh(true);
			}
		}.get().build();
	}
}
