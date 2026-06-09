package dev.jbang.dependencies;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.spi.checksums.TrustedChecksumsSource;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;

import dev.jbang.util.LockFileUtil;

/**
 * A {@link TrustedChecksumsSource} backed by JBang's {@code .jbang.lock} file.
 * <p>
 * Lock file entries use the format:
 * {@code <ref>.dep.<groupId>:<artifactId>:<type>:<version>=<algorithmName>:<hex>}
 * <p>
 * Today this is used standalone by {@code Lock} and {@code Run} commands. When
 * MIMA gains an extension point for custom trusted-checksums sources, this
 * implementation can be injected directly into the resolver pipeline.
 */
public class JBangTrustedChecksumsSource implements TrustedChecksumsSource {

	private final Path lockFile;
	private final String ref;

	public JBangTrustedChecksumsSource(Path lockFile, String ref) {
		this.lockFile = lockFile;
		this.ref = ref;
	}

	/**
	 * Returns trusted checksums for the given artifact from the lock file, or an
	 * empty map if no entry exists. Returns {@code null} only if the lock file
	 * itself does not exist (meaning "source not enabled").
	 */
	@Override
	public Map<String, String> getTrustedArtifactChecksums(
			RepositorySystemSession session,
			Artifact artifact,
			ArtifactRepository artifactRepository,
			List<ChecksumAlgorithmFactory> checksumAlgorithmFactories) {

		try {
			Map<String, String> depDigests = LockFileUtil.readDepDigests(lockFile, ref);
			if (depDigests.isEmpty()) {
				return Collections.emptyMap();
			}

			String coord = toCoordKey(artifact);
			String stored = depDigests.get(coord);
			if (stored == null) {
				return Collections.emptyMap();
			}

			// stored format is "sha256:abcdef..." — split into algorithm name + hex
			String[] parts = stored.split(":", 2);
			if (parts.length != 2) {
				return Collections.emptyMap();
			}

			// Map the stored algorithm name to the requested factory names
			String storedAlgorithm = algorithmNameToResolverName(parts[0]);
			String storedHex = parts[1];

			Map<String, String> result = new HashMap<>();
			for (ChecksumAlgorithmFactory factory : checksumAlgorithmFactories) {
				if (factory.getName().equalsIgnoreCase(storedAlgorithm)) {
					result.put(factory.getName(), storedHex);
				}
			}
			return result;
		} catch (IOException e) {
			return Collections.emptyMap();
		}
	}

	@Override
	public Writer getTrustedArtifactChecksumsWriter(RepositorySystemSession session) {
		return new JBangLockWriter();
	}

	/**
	 * Writer that records artifact checksums into the lock file.
	 */
	private class JBangLockWriter implements Writer {
		@Override
		public void addTrustedArtifactChecksums(
				Artifact artifact,
				ArtifactRepository artifactRepository,
				List<ChecksumAlgorithmFactory> checksumAlgorithmFactories,
				Map<String, String> trustedArtifactChecksums) throws IOException {

			String coord = toCoordKey(artifact);
			Map<String, String> depDigests = new HashMap<>();

			// Store all provided checksums, preferring SHA-256
			for (ChecksumAlgorithmFactory factory : checksumAlgorithmFactories) {
				String hex = trustedArtifactChecksums.get(factory.getName());
				if (hex != null) {
					String lockAlgName = resolverNameToAlgorithmName(factory.getName());
					depDigests.put(coord, lockAlgName + ":" + hex);
					break; // store one algorithm per artifact (prefer first = typically SHA-256)
				}
			}

			if (!depDigests.isEmpty()) {
				// Merge into existing lock file
				LockFileUtil.write(lockFile, ref, null, null, null, depDigests);
			}
		}
	}

	/**
	 * Converts an artifact to the coordinate key used in lock file entries. Matches
	 * the format produced by
	 * {@link dev.jbang.dependencies.MavenCoordinate#toCanonicalForm()}.
	 */
	static String toCoordKey(Artifact artifact) {
		StringBuilder sb = new StringBuilder();
		sb.append(artifact.getGroupId()).append(':').append(artifact.getArtifactId());
		String ext = artifact.getExtension();
		String cls = artifact.getClassifier();
		if (ext != null && !ext.isEmpty()) {
			if (cls != null && !cls.isEmpty()) {
				sb.append(':').append(cls);
			}
			sb.append(':').append(ext);
		}
		sb.append(':').append(artifact.getVersion());
		return sb.toString();
	}

	/**
	 * Maps lock-file algorithm names (lowercase, e.g. "sha256") to resolver names
	 * (e.g. "SHA-256").
	 */
	public static String algorithmNameToResolverName(String lockName) {
		switch (lockName.toLowerCase()) {
		case "sha256":
			return "SHA-256";
		case "sha512":
			return "SHA-512";
		case "sha1":
			return "SHA-1";
		case "md5":
			return "MD5";
		default:
			return lockName;
		}
	}

	/**
	 * Maps resolver algorithm names (e.g. "SHA-256") to lock-file names (e.g.
	 * "sha256").
	 */
	public static String resolverNameToAlgorithmName(String resolverName) {
		switch (resolverName) {
		case "SHA-256":
			return "sha256";
		case "SHA-512":
			return "sha512";
		case "SHA-1":
			return "sha1";
		case "MD5":
			return "md5";
		default:
			return resolverName.toLowerCase();
		}
	}
}
