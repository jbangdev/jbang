package dev.jbang.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

public class TestLockFileUtil {

	@Test
	void writesAndReadsDigestAndSources() throws Exception {
		Path tmp = Files.createTempFile("jbang-lock", ".lock");
		java.util.Map<String, String> depDigests = new java.util.LinkedHashMap<>();
		depDigests.put("g:a:1", "sha256:aaaabbbbcccc");
		depDigests.put("g:b:2", "sha256:ddddeeeeffff");
		LockFileUtil.write(tmp, "alias@catalog", "sha256:abcdef123456", Arrays.asList("a.java", "b.java"),
				Arrays.asList("g:a:1", "g:b:2"), depDigests);
		assertEquals("sha256:abcdef123456", LockFileUtil.readDigest(tmp, "alias@catalog"));
		assertEquals(Arrays.asList("a.java", "b.java"), LockFileUtil.readSources(tmp, "alias@catalog"));
		assertEquals(Arrays.asList("g:a:1", "g:b:2"), LockFileUtil.readDeps(tmp, "alias@catalog"));
		assertEquals(depDigests, LockFileUtil.readDepDigests(tmp, "alias@catalog"));
	}

	@Test
	void missingEntriesReturnEmptyCollectionsOrNull() throws Exception {
		Path tmp = Files.createTempFile("jbang-lock-empty", ".lock");
		assertEquals(null, LockFileUtil.readDigest(tmp, "missing@ref"));
		assertEquals(java.util.Collections.emptyList(), LockFileUtil.readSources(tmp, "missing@ref"));
		assertEquals(java.util.Collections.emptyList(), LockFileUtil.readDeps(tmp, "missing@ref"));
	}

	@Test
	void writeDigestDoesNotRequireSourcesOrDeps() throws Exception {
		Path tmp = Files.createTempFile("jbang-lock-digest", ".lock");
		LockFileUtil.writeDigest(tmp, "x:y:z", "sha256:deadbeefdead");
		assertEquals("sha256:deadbeefdead", LockFileUtil.readDigest(tmp, "x:y:z"));
		assertEquals(java.util.Collections.emptyList(), LockFileUtil.readSources(tmp, "x:y:z"));
		assertEquals(java.util.Collections.emptyList(), LockFileUtil.readDeps(tmp, "x:y:z"));
	}
}
