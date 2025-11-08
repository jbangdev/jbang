package dev.jbang.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public class UnpackUtil {

	public static void unpackEditor(Path archive, Path outputDir) throws IOException {
		String name = archive.toString().toLowerCase(Locale.ENGLISH);
		Path selectFolder = null; // Util.isMac() ? Paths.get("Contents/Home") : null;
		boolean stripRootFolder = Util.isMac();
		if (name.endsWith(".zip")) {
			unzip(archive, outputDir, stripRootFolder, selectFolder, UnpackUtil::defaultZipEntryCopy);
		} else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
			untargz(archive, outputDir, false, selectFolder);
		}
	}

	public static void unpackJdk(Path archive, Path outputDir) throws IOException {
		String name = archive.toString().toLowerCase(Locale.ENGLISH);
		Path selectFolder = Util.isMac() ? Paths.get("Contents/Home") : null;
		if (name.endsWith(".zip")) {
			unzip(archive, outputDir, true, selectFolder, UnpackUtil::defaultZipEntryCopy);
		} else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
			untargz(archive, outputDir, true, selectFolder);
		}
	}

	public static void unpack(Path archive, Path outputDir) throws IOException {
		unpack(archive, outputDir, false);
	}

	public static void unpack(Path archive, Path outputDir, boolean stripRootFolder) throws IOException {
		unpack(archive, outputDir, stripRootFolder, null);
	}

	public static void unpack(Path archive, Path outputDir, boolean stripRootFolder, Path selectFolder)
			throws IOException {
		String name = archive.toString().toLowerCase(Locale.ENGLISH);
		if (name.endsWith(".zip") || name.endsWith(".jar")) {
			unzip(archive, outputDir, stripRootFolder, selectFolder, UnpackUtil::defaultZipEntryCopy);
		} else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
			untargz(archive, outputDir, stripRootFolder, selectFolder);
		} else {
			throw new IllegalArgumentException("Unsupported archive format: " + Util.extension(archive.toString()));
		}
	}

	public static void unzip(Path zip, Path outputDir, boolean stripRootFolder, Path selectFolder,
			ZipFileHandler onZipFile) throws IOException {
		try (ZipFile zipFile = ZipFile.builder().setFile(zip.toFile()).get()) {
			Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
			while (entries.hasMoreElements()) {
				ZipArchiveEntry zipEntry = entries.nextElement();
				Path entry = Paths.get(zipEntry.getName());
				if (stripRootFolder) {
					if (entry.getNameCount() == 1) {
						continue;
					}
					entry = entry.subpath(1, entry.getNameCount());
				}
				if (selectFolder != null) {
					if (!entry.startsWith(selectFolder) || entry.equals(selectFolder)) {
						continue;
					}
					entry = entry.subpath(selectFolder.getNameCount(), entry.getNameCount());
				}
				entry = outputDir.resolve(entry).normalize();
				if (!entry.startsWith(outputDir)) {
					throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
				}
				try {
					if (zipEntry.isDirectory() && checkValidParent(entry)) {
						Files.createDirectories(entry);
					} else if (zipEntry.isUnixSymlink()) {
						try (Scanner s = new Scanner(zipFile.getInputStream(zipEntry)).useDelimiter("\\A")) {
							String result = s.hasNext() ? s.next() : "";
							Files.createSymbolicLink(entry, Paths.get(result));
						}
					} else {
						if (checkValidParent(entry.getParent())) {
							Files.createDirectories(entry.getParent());
						}
						onZipFile.handle(zipFile, zipEntry, entry, null);
					}
				} catch (Exception e) {
					onZipFile.handle(zipFile, zipEntry, entry, e);
				}
			}
		}
	}

	private static boolean checkValidParent(Path path) throws FileAlreadyExistsException {
		while (path != null && !Files.exists(path)) {
			path = path.getParent();
		}
		if (path != null && !Files.isDirectory(path)) {
			throw new FileAlreadyExistsException("Parent path is not a directory: " + path);
		}
		return true;
	}

	public interface ZipFileHandler {
		void handle(ZipFile zipFile, ZipArchiveEntry zipEntry, Path outFile, Exception ex) throws IOException;
	}

	public static void defaultZipEntryCopy(ZipFile zipFile, ZipArchiveEntry zipEntry, Path outFile, Exception ex)
			throws IOException {
		try (InputStream zis = zipFile.getInputStream(zipEntry)) {
			Files.copy(zis, outFile, StandardCopyOption.REPLACE_EXISTING);
		}
		int mode = zipEntry.getUnixMode();
		if (mode != 0 && !Util.isWindows()) {
			Set<PosixFilePermission> permissions = PosixFilePermissionSupport.toPosixFilePermissions(mode);
			Files.setPosixFilePermissions(outFile, permissions);
		}
	}

	public static void untargz(Path targz, Path outputDir, boolean stripRootFolder, Path selectFolder)
			throws IOException {
		try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(
				new GzipCompressorInputStream(new FileInputStream(targz.toFile())))) {

			TarArchiveEntry targzEntry;
			while ((targzEntry = tarArchiveInputStream.getNextEntry()) != null) {
				Path entry = Paths.get(targzEntry.getName()).normalize();

				if (stripRootFolder) {
					if (entry.getNameCount() == 1) {
						continue;
					}
					entry = entry.subpath(1, entry.getNameCount());
				}

				if (selectFolder != null) {
					if (!entry.startsWith(selectFolder) || entry.equals(selectFolder)) {
						continue;
					}
					entry = entry.subpath(selectFolder.getNameCount(), entry.getNameCount());
				}

				entry = outputDir.resolve(entry).normalize();

				if (!entry.startsWith(outputDir)) {
					throw new IOException("Entry is outside of the target dir: " + targzEntry.getName());
				}

				if (targzEntry.isDirectory()) {
					Files.createDirectories(entry);
				} else if (targzEntry.isSymbolicLink()) {
					// Handle symbolic links
					Path linkTarget = Paths.get(targzEntry.getLinkName());

					// Ensure parent directory exists
					Files.createDirectories(entry.getParent());

					// Create symbolic link (only if it doesn't exist)
					if (!Files.exists(entry)) {
						try {
							Files.createSymbolicLink(entry, linkTarget);
						} catch (IOException e) {
							Util.warnMsg("Could not create symbolic link " + entry + " -> " + linkTarget + " due to "
									+ e.getMessage(), e);
						}
					}
				} else {
					// Regular file extraction
					if (!Files.isDirectory(entry.getParent())) {
						Files.createDirectories(entry.getParent());
					}
					Files.copy(tarArchiveInputStream, entry, StandardCopyOption.REPLACE_EXISTING);

					int mode = targzEntry.getMode();
					if (mode != 0 && !Util.isWindows()) {
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
