package dev.jbang.source;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.util.Util;

public class TestMain extends BaseTest {

	String srcBasic = "package test;" +
			"public class maintest {\n" +
			"    public static void main(String... args) {\n" +
			"        System.out.println(\"one\");\n" +
			"    }\n" +
			"}\n" +
			"class two {\n" +
			"    public static void main(String... args) {\n" +
			"        System.out.println(\"two\");\n" +
			"    }\n" +
			"}\n" +
			"class three {\n" +
			"    public static void main(String... args) {\n" +
			"        System.out.println(\"three\");\n" +
			"    }\n" +
			"}\n";

	@Test
	public void testMainDefault(@TempDir File output) throws IOException {
		Path f = output.toPath().resolve("maintest.java");
		Util.writeString(f, srcBasic);

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(f);
		BuildContext ctx = BuildContext.forProject(prj);
		Project.codeBuilder(ctx).build();

		try (JarInputStream jarStream = new JarInputStream(Files.newInputStream(ctx.getJarFile()))) {
			Manifest mf = jarStream.getManifest();
			String main = mf.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
			assertThat(main, equalTo("test.maintest"));
		}
	}

	@Test
	public void testMainWithTag(@TempDir File output) throws IOException {
		Path f = output.toPath().resolve("maintest.java");
		Util.writeString(f, "//MAIN test.two\n" + srcBasic);

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(f);
		BuildContext ctx = BuildContext.forProject(prj);
		Project.codeBuilder(ctx).build();

		try (JarInputStream jarStream = new JarInputStream(Files.newInputStream(ctx.getJarFile()))) {
			Manifest mf = jarStream.getManifest();
			String main = mf.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
			assertThat(main, equalTo("test.two"));
		}
	}

	@Test
	public void testMainWithTagAndOverride(@TempDir File output) throws IOException {
		Path f = output.toPath().resolve("maintest.java");
		Util.writeString(f, "//MAIN test.two\n" + srcBasic);

		ProjectBuilder pb = Project.builder();
		Project prj = pb.build(f);
		BuildContext ctx = BuildContext.forProject(prj);
		CmdGeneratorBuilder gen = Project.codeBuilder(ctx).build();

		try (JarInputStream jarStream = new JarInputStream(Files.newInputStream(ctx.getJarFile()))) {
			Manifest mf = jarStream.getManifest();
			String main = mf.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
			assertThat(main, equalTo("test.two"));
		}

		String cmd = gen.mainClass("test.three").build().generate();
		assertThat(cmd, containsString("test.three"));
	}
}
