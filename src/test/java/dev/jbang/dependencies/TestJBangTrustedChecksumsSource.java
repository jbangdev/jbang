package dev.jbang.dependencies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.checksums.TrustedChecksumsSource;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.junit.jupiter.api.Test;

import dev.jbang.util.DigestUtil;
import dev.jbang.util.LockFileUtil;

public class TestJBangTrustedChecksumsSource {

	@Test
	void readsTrustedChecksumsFromLockFile() throws Exception {
		Path tmp = Files.createTempFile("jbang-tc-test", ".lock");
		Map<String, String> depDigests = new LinkedHashMap<>();
		depDigests.put("com.example:lib:jar:1.0", "sha256:abcdef1234567890");
		LockFileUtil.write(tmp, "myapp.java", "sha256:rootdigest", null,
				Arrays.asList("com.example:lib:jar:1.0"), depDigests);

		JBangTrustedChecksumsSource source = new JBangTrustedChecksumsSource(tmp, "myapp.java");
		DefaultArtifact artifact = new DefaultArtifact("com.example", "lib", "jar", "1.0");
		LocalRepository localRepo = new LocalRepository("local");

		List<ChecksumAlgorithmFactory> factories = DigestUtil.getChecksumFactories("SHA-256");
		Map<String, String> result = source.getTrustedArtifactChecksums(null, artifact, localRepo, factories);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("abcdef1234567890", result.get("SHA-256"));
	}

	@Test
	void returnsEmptyMapForMissingArtifact() throws Exception {
		Path tmp = Files.createTempFile("jbang-tc-miss", ".lock");
		LockFileUtil.write(tmp, "myapp.java", "sha256:rootdigest", null, null, null);

		JBangTrustedChecksumsSource source = new JBangTrustedChecksumsSource(tmp, "myapp.java");
		DefaultArtifact artifact = new DefaultArtifact("com.example", "missing", "jar", "1.0");
		LocalRepository localRepo = new LocalRepository("local");

		List<ChecksumAlgorithmFactory> factories = DigestUtil.getChecksumFactories("SHA-256");
		Map<String, String> result = source.getTrustedArtifactChecksums(null, artifact, localRepo, factories);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void writerRecordsChecksums() throws Exception {
		Path tmp = Files.createTempFile("jbang-tc-write", ".lock");
		LockFileUtil.write(tmp, "myapp.java", "sha256:rootdigest", null, null, null);

		JBangTrustedChecksumsSource source = new JBangTrustedChecksumsSource(tmp, "myapp.java");
		TrustedChecksumsSource.Writer writer = source.getTrustedArtifactChecksumsWriter(null);
		assertNotNull(writer);

		DefaultArtifact artifact = new DefaultArtifact("org.test", "foo", "jar", "2.0");
		LocalRepository localRepo = new LocalRepository("local");
		List<ChecksumAlgorithmFactory> factories = DigestUtil.getChecksumFactories("SHA-256");

		Map<String, String> checksums = Collections.singletonMap("SHA-256", "deadbeef12345678");
		writer.addTrustedArtifactChecksums(artifact, localRepo, factories, checksums);

		// Read back and verify
		Map<String, String> stored = LockFileUtil.readDepDigests(tmp, "myapp.java");
		assertTrue(stored.containsKey("org.test:foo:jar:2.0"));
		assertEquals("sha256:deadbeef12345678", stored.get("org.test:foo:jar:2.0"));
	}

	@Test
	void algorithmNameMapping() {
		assertEquals("SHA-256", JBangTrustedChecksumsSource.algorithmNameToResolverName("sha256"));
		assertEquals("SHA-512", JBangTrustedChecksumsSource.algorithmNameToResolverName("sha512"));
		assertEquals("SHA-1", JBangTrustedChecksumsSource.algorithmNameToResolverName("sha1"));
		assertEquals("sha256", JBangTrustedChecksumsSource.resolverNameToAlgorithmName("SHA-256"));
		assertEquals("sha512", JBangTrustedChecksumsSource.resolverNameToAlgorithmName("SHA-512"));
		assertEquals("sha1", JBangTrustedChecksumsSource.resolverNameToAlgorithmName("SHA-1"));
	}
}
