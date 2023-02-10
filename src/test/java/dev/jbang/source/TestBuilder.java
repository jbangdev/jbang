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
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.Settings;
import dev.jbang.catalog.CatalogUtil;
import dev.jbang.source.buildsteps.JarBuildStep;
import dev.jbang.source.buildsteps.NativeBuildStep;
import dev.jbang.source.sources.JavaSource;
import dev.jbang.source.sources.JavaSource.JavaAppBuilder.JavaCompileBuildStep;
import dev.jbang.source.sources.KotlinSource;
import dev.jbang.util.Util;

public class TestBuilder extends BaseTest {

	@Test
	void testHelloworld() throws IOException {
		Path foo = examplesTestFolder.resolve("helloworld.java").toAbsolutePath();
		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItems(foo.toString(), "-g"));
						// Skip the compiler
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testDualHelloworld(@TempDir File out1, @TempDir File out2) throws IOException {
		Path foo = examplesTestFolder.resolve("helloworld.java").toAbsolutePath();
		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(foo.toString());
		BuildContext ctx1 = BuildContext.forProject(prj, out1.toPath());
		BuildContext ctx2 = BuildContext.forProject(prj, out2.toPath());

		new JavaSource.JavaAppBuilder(prj, ctx1) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItems(containsString(out1.toString())));
						assertThat(optionList, not(hasItems(containsString(out2.toString()))));
						assertThat(optionList, not(hasItems(containsString(Settings.getCacheDir().toString()))));
						// Skip the compiler
					}
				};
			}

			@Override
			protected Builder<Project> getJarBuildStep() {
				return new JarBuildStep(project, ctx) {
					@Override
					public Project build() {
						assertThat(ctx.getJarFile().toString(), containsString(out1.toString()));
						assertThat(ctx.getJarFile().toString(), not(containsString(out2.toString())));
						assertThat(ctx.getJarFile().toString(), not(containsString(Settings.getCacheDir().toString())));
						return project;
					}
				};
			}
		}.setFresh(true).build();

		new JavaSource.JavaAppBuilder(prj, ctx2) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItems(containsString(out2.toString())));
						assertThat(optionList, not(hasItems(containsString(out1.toString()))));
						assertThat(optionList, not(hasItems(containsString(Settings.getCacheDir().toString()))));
						// Skip the compiler
					}
				};
			}

			@Override
			protected Builder<Project> getJarBuildStep() {
				return new JarBuildStep(project, ctx) {
					@Override
					public Project build() {
						assertThat(ctx.getJarFile().toString(), containsString(out2.toString()));
						assertThat(ctx.getJarFile().toString(), not(containsString(out1.toString())));
						assertThat(ctx.getJarFile().toString(), not(containsString(Settings.getCacheDir().toString())));
						return project;
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testCompileOptions() throws IOException {
		Path foo = examplesTestFolder.resolve("helloworld.java").toAbsolutePath();
		ProjectBuilder pb = ProjectBuilder.create().compileOptions(Arrays.asList("--foo", "--bar"));
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItems(foo.toString(), "-g", "--foo", "--bar"));
						// Skip the compiler
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testKotlinCompileOptions() throws IOException {
		Path foo = examplesTestFolder.resolve("helloworld.kt").toAbsolutePath();
		ProjectBuilder pb = ProjectBuilder.create().compileOptions(Arrays.asList("--foo", "--bar"));
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);

		new KotlinSource.KotlinAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new KotlinCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItems(foo.toString(), "--foo", "--bar"));
						// Skip the compiler
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testBuildAdditionalSources() throws IOException {
		Path foo = examplesTestFolder.resolve("foo.java").toAbsolutePath();
		Path bar = examplesTestFolder.resolve("bar/Bar.java").toAbsolutePath();
		ProjectBuilder pb = ProjectBuilder.create();
		pb.additionalSources(Arrays.asList(bar.toString()));
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItem(foo.toString()));
						assertThat(optionList, hasItem(bar.toString()));
						// Skip the compiler
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testAdditionalSourcesUsingAlias() throws IOException {
		String mainFile = examplesTestFolder.resolve("foo.java").toString();
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		CatalogUtil.addNearestAlias("bar", incFile, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null);

		ProjectBuilder pb = ProjectBuilder.create();
		pb.additionalSources(Arrays.asList("bar"));
		Project prj = pb.build(mainFile);
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItem(mainFile));
						assertThat(optionList, hasItem(incFile));
						// Skip the compiler
					}
				};
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

		CatalogUtil.addNearestAlias("bar", incFile, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null);

		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(mainFile.toString());
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItem(mainFile.toString()));
						assertThat(optionList, hasItem(incFile));
						// Skip the compiler
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testAdditionalSourcesGlobbing() throws IOException {
		Util.setCwd(examplesTestFolder);
		String mainFile = examplesTestFolder.resolve("foo.java").toString();
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		ProjectBuilder pb = ProjectBuilder.create();
		pb.additionalSources(Arrays.asList("bar/*.java"));
		Project prj = pb.build(mainFile);
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItem(mainFile));
						assertThat(optionList, hasItem(incFile));
						// Skip the compiler
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testAdditionalSourcesAbsGlobbing() throws IOException {
		String mainFile = examplesTestFolder.resolve("foo.java").toString();
		String incGlob = examplesTestFolder.resolve("bar").toString() + File.separatorChar + "*.java";
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		ProjectBuilder pb = ProjectBuilder.create();
		pb.additionalSources(Arrays.asList(incGlob));
		Project prj = pb.build(mainFile);
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItem(mainFile));
						assertThat(optionList, hasItem(incFile));
						// Skip the compiler
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testAdditionalSourcesFolder() throws IOException {
		Util.setCwd(examplesTestFolder);
		String mainFile = examplesTestFolder.resolve("foo.java").toString();
		String incFile = examplesTestFolder.resolve("bar/Bar.java").toString();

		ProjectBuilder pb = ProjectBuilder.create();
		pb.additionalSources(Arrays.asList("bar"));
		Project prj = pb.build(mainFile);
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItem(mainFile));
						assertThat(optionList, hasItem(incFile));
						// Skip the compiler
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testBuildAdditionalResources() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path foo = Paths.get("foo.java");
		Path res1 = Paths.get("resource.properties");
		Path res2 = Paths.get("sub/sub.properties");
		ProjectBuilder pb = ProjectBuilder.create();
		pb.additionalResources(Arrays.asList(
				Paths.get("res").resolve(res1).toString(),
				Paths.get("res").resolve(res2).toString()));
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						// Skip the compiler
					}
				};
			}

			@Override
			protected Builder<Project> getJarBuildStep() {
				return new JarBuildStep(project, ctx) {
					@Override
					public Project build() {
						assertThat(project.getMainSourceSet().getResources().size(), is(2));
						assertThat(
								project.getMainSourceSet().getResources().get(0).getSource().getFile().endsWith(res1),
								is(true));
						assertThat(
								project.getMainSourceSet().getResources().get(1).getSource().getFile().endsWith(res2),
								is(true));
						return project;
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testBuildAdditionalResourcesMounted() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path foo = Paths.get("foo.java");
		Path res1 = Paths.get("resource.properties");
		Path res2 = Paths.get("sub/sub.properties");
		ProjectBuilder pb = ProjectBuilder.create();
		pb.additionalResources(Arrays.asList(
				"somedir/=" + Paths.get("res").resolve(res1),
				"somedir/=" + Paths.get("res").resolve(res2)));
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						// Skip the compiler
					}
				};
			}

			@Override
			protected Builder<Project> getJarBuildStep() {
				return new JarBuildStep(project, ctx) {
					@Override
					public Project build() {
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
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testAdditionalResourcesGlobbing() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path foo = Paths.get("foo.java");
		Path res1 = Paths.get("resource.properties");
		Path res2 = Paths.get("sub/sub.properties");
		ProjectBuilder pb = ProjectBuilder.create();
		pb.additionalResources(Arrays.asList("res/**.properties"));
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItem(endsWith(File.separator + "foo.java")));
						// Skip the compiler
					}
				};
			}

			@Override
			protected Builder<Project> getJarBuildStep() {
				return new JarBuildStep(project, ctx) {
					@Override
					public Project build() {
						assertThat(project.getMainSourceSet().getResources().size(), is(3));
						List<String> ps = project	.getMainSourceSet()
													.getResources()
													.stream()
													.map(r -> r.getSource().getFile().toString())
													.collect(Collectors.toList());
						assertThat(ps, hasItem(endsWith("resource.properties")));
						assertThat(ps, hasItem(endsWith("test.properties")));
						assertThat(ps, hasItem(endsWith("sub" + File.separator + "sub.properties")));
						return project;
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testAdditionalResourcesFolder() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path foo = Paths.get("foo.java");
		ProjectBuilder pb = ProjectBuilder.create();
		pb.additionalResources(Arrays.asList("res"));
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItem(endsWith(File.separator + "foo.java")));
						// Skip the compiler
					}
				};
			}

			@Override
			protected Builder<Project> getJarBuildStep() {
				return new JarBuildStep(project, ctx) {
					@Override
					public Project build() {
						assertThat(project.getMainSourceSet().getResources().size(), is(4));
						List<String> ps = project	.getMainSourceSet()
													.getResources()
													.stream()
													.map(r -> r.getSource().getFile().toString())
													.collect(Collectors.toList());
						assertThat(ps, hasItem(endsWith("resource.java")));
						assertThat(ps, hasItem(endsWith("resource.properties")));
						assertThat(ps, hasItem(endsWith("test.properties")));
						assertThat(ps, hasItem(endsWith("sub" + File.separator + "sub.properties")));
						return project;
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testNativeOutputName() throws IOException {
		Util.setCwd(examplesTestFolder);
		String mainFile = examplesTestFolder.resolve("foo.java").toString();

		ProjectBuilder pb = ProjectBuilder.create();
		pb.nativeImage(true);
		Project prj = pb.build(mainFile);
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItem(endsWith(File.separator + "foo.java")));
						// Skip the compiler
					}
				};
			}

			@Override
			protected Builder<Project> getNativeBuildStep() {
				return new NativeBuildStep(project, ctx) {
					@Override
					protected void runNativeBuilder(List<String> optionList) throws IOException {
						if (Util.isWindows()) {
							assertThat(optionList.get(optionList.size() - 1), not(endsWith(".exe")));
						} else {
							assertThat(optionList.get(optionList.size() - 1), endsWith(".bin"));
						}
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testManifest() throws IOException {
		Util.setCwd(examplesTestFolder);
		Path mainFile = examplesTestFolder.resolve("helloworld.java");

		ProjectBuilder pb = ProjectBuilder.create();
		HashMap<String, String> props = new HashMap<>();
		props.put("bazprop", "algo");
		pb.setProperties(props);
		Project prj = pb.build(mainFile.toFile().getAbsolutePath());
		BuildContext ctx = BuildContext.forProject(prj);

		prj.builder(ctx).build();

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
		ProjectBuilder pb = ProjectBuilder.create().nativeImage(true);
		Project prj = pb.build(foo.toString());
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						// Skip the compiler
					}
				};
			}

			@Override
			protected Builder<Project> getNativeBuildStep() {
				return new NativeBuildStep(prj, ctx) {
					@Override
					protected void runNativeBuilder(List<String> optionList) throws IOException {
						// Skip the native image builder
						assertThat(optionList, hasItem("-O1"));
					}
				};
			}
		}.setFresh(true).build();
	}
}
