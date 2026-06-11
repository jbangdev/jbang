package dev.jbang.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.aether.internal.impl.checksum.DefaultChecksumAlgorithmFactorySelector;
import org.eclipse.aether.internal.impl.checksum.Md5ChecksumAlgorithmFactory;
import org.eclipse.aether.internal.impl.checksum.Sha1ChecksumAlgorithmFactory;
import org.eclipse.aether.internal.impl.checksum.Sha256ChecksumAlgorithmFactory;
import org.eclipse.aether.internal.impl.checksum.Sha512ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmHelper;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;
import dev.jbang.dependencies.JBangTrustedChecksumsSource;
import dev.jbang.source.Project;

public final class DigestUtil {

	private static final int MIN_DIGEST_PREFIX_LENGTH = 12;

	private DigestUtil() {
	}

	/**
	 * Computes a digest of a file using the resolver's
	 * {@link ChecksumAlgorithmHelper}.
	 *
	 * @param path      the file to digest
	 * @param algorithm lock-file algorithm name (e.g. "sha256")
	 * @return digest string in lock-file format, e.g. "sha256:abcdef..."
	 */
	public static String digestPath(Path path, String algorithm) {
		try {
			String resolverName = JBangTrustedChecksumsSource.algorithmNameToResolverName(algorithm);
			List<ChecksumAlgorithmFactory> factories = getChecksumFactories(resolverName);
			Map<String, String> checksums = ChecksumAlgorithmHelper.calculate(path.toFile(), factories);
			String hex = checksums.get(resolverName);
			if (hex == null) {
				throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
						"Checksum algorithm " + resolverName + " not available", null);
			}
			return algorithm.toLowerCase(Locale.ROOT) + ":" + hex;
		} catch (IOException e) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Unable to digest file: " + path, e);
		}
	}

	public static String digestResource(Project prj, String algorithm) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance(algorithm.toUpperCase(Locale.ROOT));
			try (InputStream in = prj.getResourceRef().getInputStream()) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) >= 0) {
					md.update(buffer, 0, read);
				}
			}
			return algorithm.toLowerCase(Locale.ROOT) + ":" + toHex(md.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Unsupported digest algorithm: " + algorithm, e);
		}
	}

	public static Path resolveLockFile(Path lockFileOverride, String ref) {
		if (lockFileOverride != null) {
			return lockFileOverride;
		}
		if (ref != null) {
			Path p = Paths.get(ref);
			Path candidate = p.isAbsolute() ? p : Util.getCwd().resolve(p);
			if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
				return Paths.get(ref + ".lock");
			}
		}
		return Util.getCwd().resolve(".jbang.lock");
	}

	public static void verifyLockedSet(String kind, String ref,
			Set<String> expected, Set<String> actual) {
		if (!actual.equals(expected)) {
			Set<String> missing = new LinkedHashSet<>(expected);
			missing.removeAll(actual);
			Set<String> extra = new LinkedHashSet<>(actual);
			extra.removeAll(expected);
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Lock verification failed for " + ref + " (" + kind + " mismatch).\n"
							+ "Possible security issue: lock file and resolved content differ.\n"
							+ "Missing from resolved: " + missing + "\n"
							+ "Unexpected in resolved: " + extra + "\n"
							+ "What to do: inspect lock/source changes; regenerate with `jbang lock " + ref
							+ "` only if trusted.",
					null);
		}
	}

	public static void verifyDigestSpec(String actualDigest, String expectedDigest, String source) {
		verifyDigestSpec(actualDigest, expectedDigest, source, true);
	}

	public static void verifyDigestSpec(String actualDigest, String expectedDigest, String source,
			boolean allowPrefix) {
		String[] actualParts = actualDigest.split(":", 2);
		String[] expectedParts = expectedDigest.split(":", 2);
		if (expectedParts.length != 2) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Lock verification failed: invalid digest format from " + source + ": " + expectedDigest + "\n"
							+ "What to do: use `sha256:<digest>` (or valid algorithm prefix).",
					null);
		}
		if (!actualParts[0].equalsIgnoreCase(expectedParts[0])) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Lock verification failed: digest algorithm mismatch in " + source + ": expected "
							+ expectedParts[0] + ", got " + actualParts[0] + "\n"
							+ "What to do: regenerate lock or use matching digest algorithm if trusted.",
					null);
		}
		String expectedHex = expectedParts[1].toLowerCase(Locale.ROOT);
		String actualHex = actualParts[1].toLowerCase(Locale.ROOT);
		if (allowPrefix && expectedHex.length() < MIN_DIGEST_PREFIX_LENGTH) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Lock verification failed: digest prefix too short in " + source + ". Use at least "
							+ MIN_DIGEST_PREFIX_LENGTH + " hex characters.\n"
							+ "What to do: provide a longer checksum prefix or full digest.",
					null);
		}
		if (allowPrefix ? !actualHex.startsWith(expectedHex) : !actualHex.equals(expectedHex)) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Lock verification failed: digest mismatch in " + source + ": expected " + expectedDigest
							+ ", got " + actualDigest + "\n"
							+ "Possible security issue: content differs from lock/checksum.\n"
							+ "What to do: inspect changes; regenerate lock only if trusted.",
					null);
		}
	}

	/**
	 * Splits a ref like "alias@catalog#sha256:abcdef" into the ref part and the
	 * checksum part.
	 */
	public static RefWithChecksum splitRefAndChecksum(String ref) {
		int idx = ref.lastIndexOf('#');
		if (idx < 0 || idx == ref.length() - 1) {
			return new RefWithChecksum(ref, null);
		}
		String suffix = ref.substring(idx + 1);
		String digest = suffix.contains(":") ? suffix : "sha256:" + suffix;
		return new RefWithChecksum(ref.substring(0, idx), digest);
	}

	public static final class RefWithChecksum {
		public final String ref;
		public final String checksum;

		public RefWithChecksum(String ref, String checksum) {
			this.ref = ref;
			this.checksum = checksum;
		}
	}

	/**
	 * Returns a singleton list of {@link ChecksumAlgorithmFactory} for the given
	 * resolver algorithm name. Uses the resolver's built-in factories (SHA-256,
	 * SHA-512, SHA-1, MD5).
	 */
	public static List<ChecksumAlgorithmFactory> getChecksumFactories(String resolverName) {
		// Use the resolver's built-in factories directly — they're available via
		// the impl jar on the classpath. This avoids needing a Lookup/session.
		DefaultChecksumAlgorithmFactorySelector selector = new DefaultChecksumAlgorithmFactorySelector(
				getBuiltinChecksumAlgorithmFactories());
		return selector.selectList(Collections.singletonList(resolverName));
	}

	private static Map<String, ChecksumAlgorithmFactory> getBuiltinChecksumAlgorithmFactories() {
		Map<String, ChecksumAlgorithmFactory> result = new HashMap<>();
		result.put("SHA-256", new Sha256ChecksumAlgorithmFactory());
		result.put("SHA-512", new Sha512ChecksumAlgorithmFactory());
		result.put("SHA-1", new Sha1ChecksumAlgorithmFactory());
		result.put("MD5", new Md5ChecksumAlgorithmFactory());
		return result;
	}

	private static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}
}
