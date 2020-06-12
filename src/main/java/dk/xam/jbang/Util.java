package dk.xam.jbang;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class Util {

	static public void info(String msg) {
		infoMsg(msg);
	}

	static public void infoMsg(String msg) {
		System.err.println("[jbang] " + msg);
	}

	static public void warnMsg(String msg) {
		System.err.println("[jbang] [WARN] " + msg);
	}

	static public void errorMsg(String msg) {
		System.err.println("[jbang] [ERROR] " + msg);
	}

	static public void errorMsg(String msg, Throwable t) {
		System.err.println("[jbang] [ERROR] " + msg);
		t.printStackTrace();
	}

	static public void quit(int status) {
		System.out.print(status > 0 ? "true" : "false");
		throw new ExitException(status);
	}

	/**
	 * Java 8 approximate version of Java 11 Files.readString()
	 **/
	static public String readString(Path toPath) throws IOException {
		return new String(Files.readAllBytes(toPath));
	}

	/**
	 * Java 8 approximate version of Java 11 Files.writeString()
	 **/
	static public void writeString(Path toPath, String scriptText) throws IOException {
		Files.write(toPath, scriptText.getBytes());
	}

	private static final int BUFFER_SIZE = 4096;

	private static final Pattern mainClassPattern = Pattern.compile(
			"(?sm)class *(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*) .*static void main");

	/**
	 * Download file from url but will swizzle output for sites that are known to
	 * possibly embed code (i.e. twitter and carbon)
	 * 
	 * @param fileURL
	 * @param saveDir
	 * @return
	 * @throws IOException
	 */
	public static Path downloadFileSwizzled(String fileURL, File saveDir) throws IOException {
		Path path = downloadFile(fileURL, saveDir);

		boolean twitter = fileURL.startsWith("https://mobile.twitter.com");
		if (twitter || fileURL.startsWith("https://carbon.now.sh")) { // sites known
																		// to have
																		// og:description
																		// meta name or
																		// property
			try {
				Document doc = Jsoup.parse(path.toFile(), "UTF-8", fileURL);

				String proposedString = null;
				if (twitter) {
					proposedString = doc.select("div[class=dir-ltr]").first().wholeText().trim();
				} else {
					proposedString = doc.select("meta[property=og:description],meta[name=og:description]")
										.first()
										.attr("content");
				}

				if (twitter) {
					// remove fake quotes
					// proposedString = proposedString.replace("\u201c", "");
					// proposedString = proposedString.replace("\u201d", "");
					// unescape properly
					proposedString = org.jsoup.parser.Parser.unescapeEntities(proposedString, true);

				}

				if (proposedString != null) {
					Matcher m = mainClassPattern.matcher(proposedString);
					String wantedfilename;
					if (m.find()) {
						String guessedClass = m.group(1);
						wantedfilename = guessedClass + ".java";
					} else {
						wantedfilename = path.getFileName() + ".jsh";
					}

					File f = path.toFile();
					File newFile = new File(f.getParent(), wantedfilename);
					f.renameTo(newFile);
					path = newFile.toPath();
					writeString(path, proposedString);
				}

			} catch (RuntimeException re) {
				// ignore any errors that can be caused by parsing
			}
		}

		return path;
	}

	/**
	 * Downloads a file from a URL
	 *
	 * @param fileURL HTTP URL of the file to be downloaded
	 * @param saveDir path of the directory to save the file
	 * @return
	 * @throws IOException
	 */
	public static Path downloadFile(String fileURL, File saveDir)
			throws IOException {
		URL url = new URL(fileURL);

		URLConnection urlConnection = url.openConnection();
		HttpURLConnection httpConn = null;

		String saveFilePath = null;
		String fileName = "";

		if (urlConnection instanceof HttpURLConnection) {
			int responseCode;
			int redirects = 0;
			while (true) {
				httpConn = (HttpURLConnection) urlConnection;
				httpConn.setInstanceFollowRedirects(false);
				responseCode = httpConn.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
						responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
					if (redirects++ > 8) {
						throw new IOException("Too many redirects");
					}
					String location = URLDecoder.decode(httpConn.getHeaderField("Location"), "UTF-8");
					url = new URL(url, location);
					fileURL = url.toExternalForm();
					info("Redirecting to: " + url);
					urlConnection = url.openConnection();
					continue;
				}
				break;
			}

			// always check HTTP response code first
			if (responseCode == HttpURLConnection.HTTP_OK) {
				String disposition = httpConn.getHeaderField("Content-Disposition");
				// String contentType = httpConn.getContentType();
				// int contentLength = httpConn.getContentLength();

				if (disposition != null) {
					// extracts file name from header field
					int index = disposition.indexOf("filename=");
					if (index > 0) {
						fileName = disposition.substring(index + 10,
								disposition.length() - 1);
					}
				}

				if (fileName == null || fileName.trim().isEmpty()) {
					// extracts file name from URL if nothing found
					fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1,
							fileURL.length());
				}

				/*
				 * System.out.println("Content-Type = " + contentType);
				 * System.out.println("Content-Disposition = " + disposition);
				 * System.out.println("Content-Length = " + contentLength);
				 * System.out.println("fileName = " + fileName);
				 */

			} else {
				throw new FileNotFoundException("No file to download. Server replied HTTP code: " + responseCode);
			}
		} else {
			fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1,
					fileURL.length());
		}

		// opens input stream from the HTTP connection
		InputStream inputStream = urlConnection.getInputStream();
		saveFilePath = saveDir + File.separator + fileName;

		// opens an output stream to save into file
		FileOutputStream outputStream = new FileOutputStream(saveFilePath);

		int bytesRead = -1;
		byte[] buffer = new byte[BUFFER_SIZE];
		while ((bytesRead = inputStream.read(buffer)) != -1) {
			outputStream.write(buffer, 0, bytesRead);
		}

		outputStream.close();
		inputStream.close();

		if (httpConn != null)
			httpConn.disconnect();

		return Paths.get(saveFilePath);

	}

}
