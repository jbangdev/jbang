package dev.jbang.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public final class LockFileUtil {

	private LockFileUtil() {
	}

	public static String readDigest(Path lockFile, String ref) throws IOException {
		if (!Files.exists(lockFile)) {
			return null;
		}
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(lockFile)) {
			p.load(in);
		}
		return p.getProperty(ref);
	}

	public static List<String> readSources(Path lockFile, String ref) throws IOException {
		return readList(lockFile, ref + ".sources");
	}

	public static List<String> readDeps(Path lockFile, String ref) throws IOException {
		return readList(lockFile, ref + ".deps");
	}

	private static List<String> readList(Path lockFile, String key) throws IOException {
		if (!Files.exists(lockFile)) {
			return java.util.Collections.emptyList();
		}
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(lockFile)) {
			p.load(in);
		}
		String val = p.getProperty(key);
		if (val == null || val.trim().isEmpty()) {
			return java.util.Collections.emptyList();
		}
		return Arrays.stream(val.split(",")).filter(s -> !s.trim().isEmpty()).collect(Collectors.toList());
	}

	public static void writeDigest(Path lockFile, String ref, String digest) throws IOException {
		write(lockFile, ref, digest, null, null);
	}

	public static void write(Path lockFile, String ref, String digest, List<String> sources, List<String> deps)
			throws IOException {
		Properties p = new Properties();
		if (Files.exists(lockFile)) {
			try (InputStream in = Files.newInputStream(lockFile)) {
				p.load(in);
			}
		}
		p.setProperty(ref, digest);
		if (sources != null && !sources.isEmpty()) {
			p.setProperty(ref + ".sources", String.join(",", sources));
		}
		if (deps != null && !deps.isEmpty()) {
			p.setProperty(ref + ".deps", String.join(",", deps));
		}
		if (lockFile.getParent() != null) {
			Files.createDirectories(lockFile.getParent());
		}
		try (OutputStream out = Files.newOutputStream(lockFile)) {
			p.store(out, "JBang lockfile v1");
		}
	}
}
