package dev.jbang.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import dev.jbang.source.BuildContext;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.util.LockFileUtil;
import dev.jbang.util.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "lock", description = "Generate or refresh lock entries for script references")
public class Lock extends BaseBuildCommand {

	@CommandLine.Option(names = { "--lock-file" }, description = "Path to lock file (default: .jbang.lock)")
	Path lockFile;

	@CommandLine.Option(names = { "--algorithm" }, description = "Digest algorithm (default: sha256)")
	String algorithm = "sha256";

	@Override
	public Integer doCall() throws IOException {
		scriptMixin.validate(true);
		String ref = scriptMixin.scriptOrFile;
		Run.RefWithChecksum parsed = Run.splitRefAndChecksum(ref);
		ref = parsed.ref;

		ProjectBuilder pb = createBaseProjectBuilder();
		Project prj = pb.build(ref);

		String digest = digestResource(prj, algorithm);
		Path effectiveLockFile = resolveLockFile(ref);
		List<String> sources = prj.getMainSourceSet()
			.getSources()
			.stream()
			.map(s -> relativizeSafe(s.getOriginalResource()))
			.collect(Collectors.toList());
		List<String> deps = BuildContext.forProject(prj)
			.resolveClassPath()
			.getArtifacts()
			.stream()
			.map(a -> a.getCoordinate() == null ? "" : a.getCoordinate().toCanonicalForm())
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toList());
		LockFileUtil.write(effectiveLockFile, ref, digest, sources, deps);
		info("Locked " + ref + " => " + digest + " in " + effectiveLockFile);
		return EXIT_OK;
	}

	private Path resolveLockFile(String ref) {
		if (lockFile != null) {
			return lockFile;
		}
		java.nio.file.Path p = java.nio.file.Paths.get(ref);
		java.nio.file.Path candidate = p.isAbsolute() ? p : Util.getCwd().resolve(p);
		if (java.nio.file.Files.exists(candidate) && java.nio.file.Files.isRegularFile(candidate)) {
			return java.nio.file.Paths.get(ref + ".lock");
		}
		return Util.getCwd().resolve(".jbang.lock");
	}

	private static String relativizeSafe(String val) {
		if (val == null) {
			return "";
		}
		return val;
	}

	private static String digestResource(Project prj, String algorithm) throws IOException {
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
			throw new ExitException(EXIT_INVALID_INPUT, "Unsupported digest algorithm: " + algorithm, e);
		}
	}

	private static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}
}
