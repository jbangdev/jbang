package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.ExitException;

public class TestJarMainDetection extends BaseTest {

	@Test
	void testUnparseableClassesDoNotCrashMainDetection() throws Exception {
		// Jar without manifest containing classes jandex cannot parse
		// (regression for AIOOBE on e.g. class files newer than jandex supports)
		Path jar = cwdDir.resolve("nomain.jar");
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
			zos.putNextEntry(new ZipEntry("module-info.class"));
			zos.write(new byte[] { (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 69 });
			zos.closeEntry();
			zos.putNextEntry(new ZipEntry("com/example/Bogus.class"));
			zos.write("not a real class file".getBytes());
			zos.closeEntry();
		}

		// Should fail with the friendly "no main class" error, not an
		// ArrayIndexOutOfBoundsException from the class scanner
		ExitException e = assertThrows(ExitException.class, () -> checkedRun("run", jar.toString()));
		assertThat(e.getMessage(), containsString("No main class"));
	}

	@Test
	void testModuleDescriptorMainClass() throws Exception {
		// Modular jar without Main-Class manifest entry but with main-class
		// in its module descriptor should run without scanning or prompting
		Path src = cwdDir.resolve("modsrc");
		Files.createDirectories(src.resolve("pkg"));
		Files.write(src.resolve("module-info.java"), "module test.mod {}".getBytes());
		Files.write(src.resolve("pkg/Main.java"),
				"package pkg; public class Main { public static void main(String... a) {} }".getBytes());
		// A second main so a class scan would be ambiguous
		Files.write(src.resolve("pkg/Other.java"),
				"package pkg; public class Other { public static void main(String... a) {} }".getBytes());
		Path classes = cwdDir.resolve("modclasses");
		String javaHome = System.getProperty("java.home");
		exec(javaHome + "/bin/javac", "-d", classes.toString(),
				src.resolve("module-info.java").toString(),
				src.resolve("pkg/Main.java").toString(),
				src.resolve("pkg/Other.java").toString());
		Path jar = cwdDir.resolve("mod.jar");
		exec(javaHome + "/bin/jar", "--create", "--file", jar.toString(), "--main-class", "pkg.Main",
				"-C", classes.toString(), ".");

		CaptureResult<Integer> result = checkedRun("run", jar.toString());
		assertThat(result.result, org.hamcrest.Matchers.equalTo(ExitException.EXIT_EXECUTE));
		assertThat(result.out, containsString("pkg.Main"));

		// With --module the jar must be run as a module: either "-m test.mod"
		// (main resolved from descriptor by the JVM) or "-m test.mod/pkg.Main"
		result = checkedRun("run", "--module", jar.toString());
		assertThat(result.result, org.hamcrest.Matchers.equalTo(ExitException.EXIT_EXECUTE));
		assertThat(result.out, containsString("-m test.mod"));
	}

	private static void exec(String... cmd) throws Exception {
		Process p = new ProcessBuilder(cmd).inheritIO().start();
		if (p.waitFor() != 0) {
			throw new IllegalStateException("command failed: " + String.join(" ", cmd));
		}
	}
}
