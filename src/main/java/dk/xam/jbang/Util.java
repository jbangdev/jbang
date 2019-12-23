package dk.xam.jbang;

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
		System.exit(status);
	}

}
