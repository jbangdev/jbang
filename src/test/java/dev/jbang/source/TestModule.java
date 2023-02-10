package dev.jbang.source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.anExistingFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.source.buildsteps.JarBuildStep;
import dev.jbang.source.sources.JavaSource;
import dev.jbang.util.Util;

@DisabledOnJre(JRE.JAVA_8)
public class TestModule {

	String srcWithModDep = "//MODULE moduletest\n" +
			"//DEPS info.picocli:picocli:4.6.3\n" +
			"public class moduletest {\n" +
			"    public static void main(String... args) {\n" +
			"        System.out.println(\"Hello World\");\n" +
			"    }\n" +
			"}\n";

	@Test
	void testModule(@TempDir File output) throws IOException {
		Path f = output.toPath().resolve("moduletest.java");
		Util.writeString(f, srcWithModDep);

		ProjectBuilder pb = ProjectBuilder.create();
		Project prj = pb.build(f);
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
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
				return new JarBuildStep(project, ctx) {
					@Override
					public Project build() {
						assertThat(ctx.getCompileDir().resolve("module-info.class").toFile(), anExistingFile());
						// Skip building of JAR
						return project;
					}
				};
			}
		}.setFresh(true).build();
	}

	@Test
	void testModuleWithCustomModuleInfo(@TempDir File output) throws IOException {
		Path f = output.toPath().resolve("moduletest.java");
		Util.writeString(f, srcWithModDep);
		Path mi = output.toPath().resolve("module-info.java");
		Util.writeString(mi, "FAKE MODULE INFO");

		ProjectBuilder pb = ProjectBuilder.create().additionalSources(Collections.singletonList(mi.toString()));
		Project prj = pb.build(f);
		BuildContext ctx = BuildContext.forProject(prj);

		new JavaSource.JavaAppBuilder(prj, ctx) {
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
	}
}
