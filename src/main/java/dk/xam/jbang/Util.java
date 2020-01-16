package dk.xam.jbang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Util {

	static public void info(String msg) {
		System.err.println(msg);
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

	static public void quit(int status) {
		System.out.print(status > 0 ? "true" : "false");
		throw new ExitException(status);
	}

	/** Java 8 approximate version of Java 11 Files.readString() **/
	static public String readString(Path toPath) throws IOException {
		return new String(Files.readAllBytes(toPath));
	}

	/** Java 8 approximate version of Java 11 Files.writeString() **/
	static public void writeString(Path toPath, String scriptText) throws IOException {
		Files.write(toPath, scriptText.getBytes());
	}
}
