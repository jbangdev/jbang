package dev.jbang.util;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
			Path root = realRoot(outputDir);
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
				if (!entry.startsWith(outputDir.normalize())) {
					throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
				}
				try {
					if (zipEntry.isDirectory()) {
						assertParentInside(root, entry, zipEntry.getName());
						Files.createDirectories(entry);
					} else if (zipEntry.isUnixSymlink()) {
						try (Scanner s = new Scanner(zipFile.getInputStream(zipEntry)).useDelimiter("\\A")) {
							String result = s.hasNext() ? s.next() : "";
							Path linkTarget = Paths.get(result);
							assertSymlinkInside(root, entry, linkTarget, zipEntry.getName());
							Files.createSymbolicLink(entry, linkTarget);
						}
					} else {
						assertParentInside(root, entry, zipEntry.getName());
						onZipFile.handle(zipFile, zipEntry, entry, null);
					}
				} catch (Exception e) {
					onZipFile.handle(zipFile, zipEntry, entry, e);
				}
			}
		}
	}

	/**
	 * Real (symlink-resolved) path of the extraction root, creating it if needed.
	 */
	private static Path realRoot(Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		return outputDir.toRealPath();
	}

	/**
	 * Ensure the parent directory of {@code entry} exists and, after following any
	 * already-extracted symlinks, still lives inside {@code root}. This defeats
	 * "Zip Slip" escapes that chain symlinks (e.g. {@code a -> .} then
	 * {@code a/b -> ..}) which pass a purely lexical check. Returns the real
	 * parent.
	 */
	private static Path assertParentInside(Path root, Path entry, String name) throws IOException {
		Path parent = entry.getParent();
		if (!Files.exists(parent)) {
			Files.createDirectories(parent);
		}
		// toRealPath follows any already-extracted symlinks in the ancestor chain,
		// so a link like a -> . then a/b -> .. is caught here rather than lexically.
		Path realParent = parent.toRealPath();
		if (!realParent.startsWith(root)) {
			throw new IOException("Symbolic link escapes the target dir: " + name);
		}
		return realParent;
	}

	/**
	 * Verify that a symlink entry {@code entry} pointing at {@code linkTarget}
	 * resolves (against its real parent) to a location inside {@code root}.
	 */
	private static void assertSymlinkInside(Path root, Path entry, Path linkTarget, String name) throws IOException {
		Path realParent = assertParentInside(root, entry, name);
		Path resolved = realParent.resolve(linkTarget).normalize();
		if (!resolved.startsWith(root)) {
			throw new IOException("Symbolic link escapes the target dir: " + name + " -> " + linkTarget);
		}
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

	/**
	 * Reads a single entry from a .tar.gz archive, optionally after stripping the
	 * single root folder (as GitHub source tarballs have). Returns {@code null} if
	 * the entry is not found.
	 */
	public static byte[] readEntry(Path targz, boolean stripRootFolder, Path relative) throws IOException {
		try (TarArchiveInputStream in = new TarArchiveInputStream(
				new GzipCompressorInputStream(new FileInputStream(targz.toFile())))) {
			TarArchiveEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				Path p = Paths.get(entry.getName()).normalize();
				// Ignore entries that would escape the archive root ("Zip Slip")
				if (p.isAbsolute() || p.startsWith("..")) {
					continue;
				}
				if (stripRootFolder) {
					if (p.getNameCount() == 1) {
						continue;
					}
					p = p.subpath(1, p.getNameCount());
				}
				if (p.equals(relative) && !entry.isDirectory()) {
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					byte[] buf = new byte[8192];
					int n;
					while ((n = in.read(buf)) != -1) {
						bos.write(buf, 0, n);
					}
					return bos.toByteArray();
				}
			}
		}
		return null;
	}

	/**
	 * Lists the top-level folder names inside a .tar.gz archive, optionally after
	 * stripping the single root folder (as GitHub source tarballs have).
	 */
	public static Set<String> listTopFolders(Path targz, boolean stripRootFolder) throws IOException {
		Set<String> folders = new LinkedHashSet<>();
		try (TarArchiveInputStream in = new TarArchiveInputStream(
				new GzipCompressorInputStream(new FileInputStream(targz.toFile())))) {
			TarArchiveEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				Path p = Paths.get(entry.getName()).normalize();
				// Ignore entries that would escape the archive root ("Zip Slip")
				if (p.isAbsolute() || p.startsWith("..")) {
					continue;
				}
				if (stripRootFolder) {
					if (p.getNameCount() == 1) {
						continue;
					}
					p = p.subpath(1, p.getNameCount());
				}
				// only count paths with content below them, i.e. actual folders
				if (p.getNameCount() >= 2) {
					folders.add(p.getName(0).toString());
				}
			}
		}
		return folders;
	}

	public static void untargz(Path targz, Path outputDir, boolean stripRootFolder, Path selectFolder)
			throws IOException {
		try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(
				new GzipCompressorInputStream(new FileInputStream(targz.toFile())))) {

			Path root = realRoot(outputDir);
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

				if (!entry.startsWith(outputDir.normalize())) {
					throw new IOException("Entry is outside of the target dir: " + targzEntry.getName());
				}

				if (targzEntry.isDirectory()) {
					assertParentInside(root, entry, targzEntry.getName());
					Files.createDirectories(entry);
				} else if (targzEntry.isSymbolicLink()) {
					// Handle symbolic links
					Path linkTarget = Paths.get(targzEntry.getLinkName());

					assertSymlinkInside(root, entry, linkTarget, targzEntry.getName());

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
					assertParentInside(root, entry, targzEntry.getName());
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
