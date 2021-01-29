package dev.jbang;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class JarUtil {
	private JarUtil() {
	}

	/**
	 * <P>
	 * This function will create a Jar archive containing the src file/directory.
	 * The archive will be written to the specified OutputStream.
	 * </P>
	 *
	 * <P>
	 * This is a shortcut for<br>
	 * <code>jar(out, new File[] { src }, null, null, null);</code>
	 * </P>
	 *
	 * @param out The output stream to which the generated Jar archive is written.
	 * @param src The file or directory to jar up. Directories will be processed
	 *            recursively.
	 * @throws IOException
	 */
	public static void jar(OutputStream out, File src) throws IOException {
		jar(out, new File[] { src }, null, null, null);
	}

	/**
	 * <P>
	 * This function will create a Jar archive containing the src file/directory.
	 * The archive will be written to the specified OutputStream.
	 * </P>
	 *
	 * <P>
	 * This is a shortcut for<br>
	 * <code>jar(out, src, null, null, null);</code>
	 * </P>
	 *
	 * @param out The output stream to which the generated Jar archive is written.
	 * @param src The file or directory to jar up. Directories will be processed
	 *            recursively.
	 * @throws IOException
	 */
	public static void jar(OutputStream out, File[] src) throws IOException {
		jar(out, src, null, null, null);
	}

	/**
	 * <P>
	 * This function will create a Jar archive containing the src file/directory.
	 * The archive will be written to the specified OutputStream. Directories are
	 * processed recursively, applying the specified filter if it exists.
	 *
	 * <P>
	 * This is a shortcut for<br>
	 * <code>jar(out, src, filter, null, null);</code>
	 * </P>
	 *
	 * @param out    The output stream to which the generated Jar archive is
	 *               written.
	 * @param src    The file or directory to jar up. Directories will be processed
	 *               recursively.
	 * @param filter The filter to use while processing directories. Only those
	 *               files matching will be included in the jar archive. If null,
	 *               then all files are included.
	 * @throws IOException
	 */
	public static void jar(OutputStream out, File[] src, FileFilter filter)
			throws IOException {
		jar(out, src, filter, null, null);
	}

	/**
	 * <P>
	 * This function will create a Jar archive containing the src file/directory.
	 * The archive will be written to the specified OutputStream. Directories are
	 * processed recursively, applying the specified filter if it exists.
	 *
	 * @param out    The output stream to which the generated Jar archive is
	 *               written.
	 * @param src    The file or directory to jar up. Directories will be processed
	 *               recursively.
	 * @param filter The filter to use while processing directories. Only those
	 *               files matching will be included in the jar archive. If null,
	 *               then all files are included.
	 * @param prefix The name of an arbitrary directory that will precede all
	 *               entries in the jar archive. If null, then no prefix will be
	 *               used.
	 * @param man    The manifest to use for the Jar archive. If null, then no
	 *               manifest will be included.
	 * @throws IOException
	 */
	public static void jar(OutputStream out, File[] src, FileFilter filter,
			String prefix, Manifest man) throws IOException {

		for (File file : src) {
			if (!file.exists()) {
				throw new FileNotFoundException(file.toString());
			}
		}

		JarOutputStream jout;
		if (man == null) {
			// noinspection resource
			jout = new JarOutputStream(out);
		} else {
			// noinspection resource
			jout = new JarOutputStream(out, man);
		}
		if (prefix != null && prefix.length() > 0 && !prefix.equals("/")) {
			// strip leading '/'
			if (prefix.charAt(0) == '/') {
				prefix = prefix.substring(1);
			}
			// ensure trailing '/'
			if (prefix.charAt(prefix.length() - 1) != '/') {
				prefix = prefix + "/";
			}
		} else {
			prefix = "";
		}
		JarInfo info = new JarInfo(jout, filter);
		for (File file : src) {
			jar(file, prefix, info);
		}
		jout.close();
	}

	/**
	 * This simple convenience class is used by the jar method to reduce the number
	 * of arguments needed. It holds all non-changing attributes needed for the
	 * recursive jar method.
	 */
	private static class JarInfo {
		public JarOutputStream out;
		public FileFilter filter;
		public byte[] buffer;

		public JarInfo(JarOutputStream out, FileFilter filter) {
			this.out = out;
			this.filter = filter;
			buffer = new byte[1024];
		}
	}

	/**
	 * This recursive method writes all matching files and directories to the jar
	 * output stream.
	 */
	private static void jar(File src, String prefix, JarInfo info)
			throws IOException {

		JarOutputStream jout = info.out;
		if (src.isDirectory()) {
			// create / init the zip entry
			prefix = prefix + src.getName() + "/";
			ZipEntry entry = new ZipEntry(prefix);
			entry.setTime(src.lastModified());
			entry.setMethod(ZipOutputStream.STORED);
			entry.setSize(0L);
			entry.setCrc(0L);
			jout.putNextEntry(entry);
			jout.closeEntry();

			// process the sub-directories
			File[] files = src.listFiles(info.filter);
			for (File file : files) {
				jar(file, prefix, info);
			}
		} else if (src.isFile()) {
			// get the required info objects
			byte[] buffer = info.buffer;

			// create / init the zip entry
			ZipEntry entry = new ZipEntry(prefix + src.getName());
			entry.setTime(src.lastModified());
			jout.putNextEntry(entry);

			// dump the file
			try (FileInputStream in = new FileInputStream(src)) {
				int len;
				while ((len = in.read(buffer, 0, buffer.length)) != -1) {
					jout.write(buffer, 0, len);
				}
			}
			jout.closeEntry();

		}
	}
}
