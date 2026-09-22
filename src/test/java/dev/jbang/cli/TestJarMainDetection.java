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
}
