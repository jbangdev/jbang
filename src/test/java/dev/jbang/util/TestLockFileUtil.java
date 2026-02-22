package dev.jbang.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

public class TestLockFileUtil {

	@Test
	void writesAndReadsDigestAndSources() throws Exception {
		Path tmp = Files.createTempFile("jbang-lock", ".lock");
		LockFileUtil.write(tmp, "alias@catalog", "sha256:abcdef123456", List.of("a.java", "b.java"));
		assertEquals("sha256:abcdef123456", LockFileUtil.readDigest(tmp, "alias@catalog"));
		assertEquals(List.of("a.java", "b.java"), LockFileUtil.readSources(tmp, "alias@catalog"));
	}
}
