package dev.jbang.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

public class TestLockFileUtil {

	@Test
	void writesAndReadsDigestAndSources() throws Exception {
		Path tmp = Files.createTempFile("jbang-lock", ".lock");
		LockFileUtil.write(tmp, "alias@catalog", "sha256:abcdef123456", Arrays.asList("a.java", "b.java"),
				Arrays.asList("g:a:1", "g:b:2"));
		assertEquals("sha256:abcdef123456", LockFileUtil.readDigest(tmp, "alias@catalog"));
		assertEquals(Arrays.asList("a.java", "b.java"), LockFileUtil.readSources(tmp, "alias@catalog"));
		assertEquals(Arrays.asList("g:a:1", "g:b:2"), LockFileUtil.readDeps(tmp, "alias@catalog"));
	}
}
