package dev.jbang;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public class UnpackUtil {

	public static void unpack(Path archive, Path outputDir) throws IOException {
		String name = archive.toString().toLowerCase(Locale.ENGLISH);
		if (name.endsWith(".zip")) {
			unzip(archive, outputDir, true);
		} else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
			untargz(archive, outputDir, true);
		}
	}

	public static void unzip(Path zip, Path outputDir, boolean stripRootFolder) throws IOException {
		try (ZipFile zipFile = new ZipFile(zip.toFile())) {
			Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
			while (entries.hasMoreElements()) {
				ZipArchiveEntry zipEntry = entries.nextElement();
				Path entry = Paths.get(zipEntry.getName());
				if (stripRootFolder) {
					if (entry.getNameCount() == 1) {
						continue;
					}
					entry = entry.subpath(1, entry.getNameCount() - 1);
				}
				entry = outputDir.resolve(entry).normalize();
				if (!entry.startsWith(outputDir)) {
					throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
				}
				if (zipEntry.isDirectory()) {
					Files.createDirectories(entry);
				} else {
					if (!Files.isDirectory(entry.getParent())) {
						Files.createDirectories(entry.getParent());
					}
					try (InputStream zis = zipFile.getInputStream(zipEntry)) {
						Files.copy(zis, entry);
					}
					int mode = zipEntry.getUnixMode();
					if (mode != 0) {
						Set<PosixFilePermission> permissions = PosixFilePermissionSupport.toPosixFilePermissions(mode);
						Files.setPosixFilePermissions(entry, permissions);
					}
				}
			}
		}
	}

	public static void untargz(Path targz, Path outputDir, boolean stripRootFolder) throws IOException {
		try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(
				new GzipCompressorInputStream(
						new FileInputStream(targz.toFile())))) {
			TarArchiveEntry targzEntry;
			while ((targzEntry = tarArchiveInputStream.getNextTarEntry()) != null) {
				Path entry = Paths.get(targzEntry.getName());
				if (stripRootFolder) {
					if (entry.getNameCount() == 1) {
						continue;
					}
					entry = entry.subpath(1, entry.getNameCount());
				}
				entry = outputDir.resolve(entry).normalize();
				if (!entry.startsWith(outputDir)) {
					throw new IOException("Entry is outside of the target dir: " + targzEntry.getName());
				}
				if (targzEntry.isDirectory()) {
					Files.createDirectories(entry);
				} else {
					if (!Files.isDirectory(entry.getParent())) {
						Files.createDirectories(entry.getParent());
					}
					Files.copy(tarArchiveInputStream, entry);
					int mode = targzEntry.getMode();
					if (mode != 0) {
						Set<PosixFilePermission> permissions = PosixFilePermissionSupport.toPosixFilePermissions(mode);
						Files.setPosixFilePermissions(entry, permissions);
					}
				}
			}
		}
	}
}

class PosixFilePermissionSupport {

	private static final int OWNER_READ_FILEMODE = 0b100_000_000;
	private static final int OWNER_WRITE_FILEMODE = 0b010_000_000;
	private static final int OWNER_EXEC_FILEMODE = 0b001_000_000;

	private static final int GROUP_READ_FILEMODE = 0b000_100_000;
	private static final int GROUP_WRITE_FILEMODE = 0b000_010_000;
	private static final int GROUP_EXEC_FILEMODE = 0b000_001_000;

	private static final int OTHERS_READ_FILEMODE = 0b000_000_100;
	private static final int OTHERS_WRITE_FILEMODE = 0b000_000_010;
	private static final int OTHERS_EXEC_FILEMODE = 0b000_000_001;

	private PosixFilePermissionSupport() {
	}

	static Set<PosixFilePermission> toPosixFilePermissions(int octalFileMode) {
		Set<PosixFilePermission> permissions = new LinkedHashSet<>();
		// Owner
		if ((octalFileMode & OWNER_READ_FILEMODE) == OWNER_READ_FILEMODE) {
			permissions.add(PosixFilePermission.OWNER_READ);
		}
		if ((octalFileMode & OWNER_WRITE_FILEMODE) == OWNER_WRITE_FILEMODE) {
			permissions.add(PosixFilePermission.OWNER_WRITE);
		}
		if ((octalFileMode & OWNER_EXEC_FILEMODE) == OWNER_EXEC_FILEMODE) {
			permissions.add(PosixFilePermission.OWNER_EXECUTE);
		}
		// Group
		if ((octalFileMode & GROUP_READ_FILEMODE) == GROUP_READ_FILEMODE) {
			permissions.add(PosixFilePermission.GROUP_READ);
		}
		if ((octalFileMode & GROUP_WRITE_FILEMODE) == GROUP_WRITE_FILEMODE) {
			permissions.add(PosixFilePermission.GROUP_WRITE);
		}
		if ((octalFileMode & GROUP_EXEC_FILEMODE) == GROUP_EXEC_FILEMODE) {
			permissions.add(PosixFilePermission.GROUP_EXECUTE);
		}
		// Others
		if ((octalFileMode & OTHERS_READ_FILEMODE) == OTHERS_READ_FILEMODE) {
			permissions.add(PosixFilePermission.OTHERS_READ);
		}
		if ((octalFileMode & OTHERS_WRITE_FILEMODE) == OTHERS_WRITE_FILEMODE) {
			permissions.add(PosixFilePermission.OTHERS_WRITE);
		}
		if ((octalFileMode & OTHERS_EXEC_FILEMODE) == OTHERS_EXEC_FILEMODE) {
			permissions.add(PosixFilePermission.OTHERS_EXECUTE);
		}
		return permissions;
	}
}
