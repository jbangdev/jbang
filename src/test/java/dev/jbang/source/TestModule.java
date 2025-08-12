package dev.jbang.source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.cli.ExitException;
import dev.jbang.source.buildsteps.JarBuildStep;
import dev.jbang.source.sources.JavaSource;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.ModuleUtil;
import dev.jbang.util.Util;

@DisabledOnJre(JRE.JAVA_8)
public class TestModule extends BaseTest {

	String srcInvalidMod = "//MODULE testmodule\n" +
			"public class moduletest {\n" +
			"    public static void main(String... args) {\n" +
			"        System.out.println(\"Hello World\");\n" +
			"    }\n" +
			"}\n";

	String srcWithModDep = "//MODULE testmodule\n" +
			"//DEPS info.picocli:picocli:4.6.3\n" +
			"package test;" +
			"public class moduletest {\n" +
			"    public static void main(String... args) {\n" +
			"        System.out.println(\"Hello World\");\n" +
			"    }\n" +
			"}\n";

	String srcWithEmptyVersionRangeDep = "//MODULE\n" +
			"//DEPS info.picocli:picocli:4.6+\n" +
			"package test;" +
			"public class moduletest {\n" +
			"    public static void main(String... args) {\n" +
			"        System.out.println(\"Hello World\");\n" +
			"    }\n" +
			"}\n";

	String srcWithEmptyModDep = "//MODULE\n" +
			"//DEPS info.picocli:picocli:4.6.3\n" +
			"package test;" +
			"public class moduletest {\n" +
			"    public static void main(String... args) {\n" +
			"        System.out.println(\"Hello World\");\n" +
			"    }\n" +
			"}\n";

	String srcWithoutMod = "//DEPS info.picocli:picocli:4.6.3\n" +
			"package test;" +
			"public class moduletest {\n" +
			"    public static void main(String... args) {\n" +
			"        System.out.println(\"Hello World\");\n" +
			"    }\n" +
			"}\n";

	@Test
	void testModuleInvalid(@TempDir File output) throws IOException {
		Path f = output.toPath().resolve("moduletest.java");
		Util.writeString(f, srcInvalidMod);

		Project prj = Project.builder().build(f);
		assertThrows(ExitException.class, () -> {
			prj.codeBuilder().build();
		});
	}

	@Test
	void testModule(@TempDir File output) throws IOException {
		Path f = output.toPath().resolve("moduletest.java");
		Util.writeString(f, srcWithModDep);

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(f);
		BuildContext ctx = BuildContext.forProject(prj);

		CmdGeneratorBuilder gen = new JavaSource.JavaAppBuilder(ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) throws IOException {
						assertThat(optionList, hasItems(endsWith("module-info.java")));

						Path modInfo = ctx.getGeneratedSourcesDir().resolve("module-info.java");
						assertThat(modInfo.toFile(), anExistingFile());
						assertThat(Util.readFileContent(modInfo), containsString("requires info.picocli;"));

						super.runCompiler(optionList);
					}
				};
			}

			@Override
			protected Builder<Project> getJarBuildStep() {
				return new JarBuildStep(ctx) {
					@Override
					public Project build() {
						assertThat(ctx.getCompileDir().resolve("module-info.class").toFile(), anExistingFile());
						// Skip building of JAR
						return ctx.getProject();
					}
				};
			}
		}.setFresh(true).build();

		String cmd = gen.build().generate();
		assertThat(cmd, endsWith(" -m testmodule/test.moduletest"));
	}

	@Test
	void testEmptyModule(@TempDir File output) throws IOException {
		testEmptyModule(srcWithEmptyModDep, output);
	}

	@Test
	void testEmptyModuleTrailingWhiteSpaces(@TempDir File output) throws IOException {
		testEmptyModule(srcWithEmptyModDep.replace("//MODULE", "//MODULE  \t  "), output);
	}

	@Test
	void testEmptyVersionRangedModule(@TempDir File output) throws IOException {
		testEmptyModule(srcWithEmptyVersionRangeDep, output);
	}

	void testEmptyModule(String script, File output) throws IOException {
		Path f = output.toPath().resolve("moduletest.java");
		Util.writeString(f, script);

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(f);
		BuildContext ctx = BuildContext.forProject(prj);

		CmdGeneratorBuilder gen = new JavaSource.JavaAppBuilder(ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) throws IOException {
						assertThat(optionList, hasItems(endsWith("module-info.java")));

						Path modInfo = ctx.getGeneratedSourcesDir().resolve("module-info.java");
						assertThat(modInfo.toFile(), anExistingFile());
						assertThat(Util.readFileContent(modInfo), containsString("requires info.picocli;"));

						super.runCompiler(optionList);
					}
				};
			}

			@Override
			protected Builder<Project> getJarBuildStep() {
				return new JarBuildStep(ctx) {
					@Override
					public Project build() {
						assertThat(ctx.getCompileDir().resolve("module-info.class").toFile(), anExistingFile());
						// Skip building of JAR
						return ctx.getProject();
					}
				};
			}
		}.setFresh(true).build();

		String cmd = gen.build().generate();
		assertThat(cmd, endsWith(" -m moduletest/test.moduletest"));
	}

	@Test
	void testModuleWithCustomModuleInfo(@TempDir File output) throws IOException {
		Path f = output.toPath().resolve("moduletest.java");
		Util.writeString(f, srcWithModDep);
		Path mi = output.toPath().resolve("module-info.java");
		Util.writeString(mi, "FAKE MODULE INFO");

		ProjectBuilder pb = Project.builder()
			.mainClass("test.moduletest")
			.additionalSources(Collections.singletonList(mi.toString()));
		Project prj = pb.build(f);
		BuildContext ctx = BuildContext.forProject(prj);

		CmdGeneratorBuilder gen = new JavaSource.JavaAppBuilder(ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) {
						assertThat(optionList, hasItems(endsWith("module-info.java")));

						Path modInfo = ctx.getGeneratedSourcesDir().resolve("module-info.java");
						assertThat(modInfo.toFile(), not(anExistingFile()));
						// Skip the compiler
					}
				};
			}
		}.setFresh(true).build();

		String cmd = gen.build().generate();
		assertThat(cmd, endsWith(" -m testmodule/test.moduletest"));
	}

	@Test
	void testForceModule(@TempDir File output) throws IOException {
		Path f = output.toPath().resolve("moduletest.java");
		Util.writeString(f, srcWithModDep);

		ProjectBuilder pb = Project.builder().moduleName("testmodule");
		Project prj = pb.build(f);
		BuildContext ctx = BuildContext.forProject(prj);

		CmdGeneratorBuilder gen = new JavaSource.JavaAppBuilder(ctx) {
			@Override
			protected Builder<Project> getCompileBuildStep() {
				return new JavaCompileBuildStep() {
					@Override
					protected void runCompiler(List<String> optionList) throws IOException {
						assertThat(optionList, hasItems(endsWith("module-info.java")));

						Path modInfo = ctx.getGeneratedSourcesDir().resolve("module-info.java");
						assertThat(modInfo.toFile(), anExistingFile());
						assertThat(Util.readFileContent(modInfo), containsString("requires info.picocli;"));

						super.runCompiler(optionList);
					}
				};
			}
		}.setFresh(true).build();

		String cmd = gen.build().generate();
		assertThat(cmd, endsWith(" -m testmodule/test.moduletest"));

		if (JavaUtil.getCurrentMajorJavaVersion() >= 9) {
			assertThat(ModuleUtil.getModuleName(ctx.getJarFile()), equalTo("testmodule"));
		}
	}
}
