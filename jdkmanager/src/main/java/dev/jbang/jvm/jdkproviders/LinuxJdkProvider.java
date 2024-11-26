package dev.jbang.jvm.jdkproviders;

import dev.jbang.jvm.util.FileUtils;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This JDK provider is intended to detects JDKs that have been installed in
 * standard location of the users linux distro.
 *
 * For now just using `/usr/lib/jvm` as apparently fedora, debian, ubuntu and
 * centos/rhel use it.
 *
 * If need different behavior per linux distro its intended this provider will
 * adjust based on identified distro.
 *
 */
public class LinuxJdkProvider extends BaseFoldersJdkProvider {
	private static final Path JDKS_ROOT = Paths.get("/usr/lib/jvm");

	public LinuxJdkProvider() {
		super(JDKS_ROOT);
	}

	@Override
	protected boolean acceptFolder(Path jdkFolder) {
		return super.acceptFolder(jdkFolder) && !isSameFolderLink(jdkFolder);
	}

	// Returns true if a path is a (sym)link to an entry in the same folder
	private boolean isSameFolderLink(Path jdkFolder) {
		Path absFolder = jdkFolder.toAbsolutePath();
		try {
			if (FileUtils.isLink(absFolder)) {
				Path realPath = absFolder.toRealPath();
				return Files.isSameFile(absFolder.getParent(), realPath.getParent());
			}
		} catch (IOException e) {
			/* ignore */
		}
		return false;
	}
}
