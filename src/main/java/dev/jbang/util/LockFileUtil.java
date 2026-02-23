package dev.jbang.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

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

	public static void writeDigest(Path lockFile, String ref, String digest) throws IOException {
		Properties p = new Properties();
		if (Files.exists(lockFile)) {
			try (InputStream in = Files.newInputStream(lockFile)) {
				p.load(in);
			}
		}
		p.setProperty(ref, digest);
		if (lockFile.getParent() != null) {
			Files.createDirectories(lockFile.getParent());
		}
		try (OutputStream out = Files.newOutputStream(lockFile)) {
			p.store(out, "JBang lockfile v1");
		}
	}
}
