package dev.jbang.util;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;

public abstract class ConsoleInput {
	private final int tries;
	private final int timeout;
	private final TimeUnit unit;

	private static final Path TTY = Paths.get("/dev/tty");

	/**
	 * Will either return a ConsoleInput that enables reading a line from the
	 * console (using stdin or a tty) or it will return <code>null</code> if no
	 * console is available.
	 */
	public static ConsoleInput get(int tries, int timeout, TimeUnit unit) {
		String preferGui = System.getenv(Util.JBANG_PREFER_GUI);
		if (preferGui != null && !GraphicsEnvironment.isHeadless()) {
			if ("true".equalsIgnoreCase(preferGui)) {
				return null;
			}
		}

		if (Util.haveConsole()) {
			return stdin(tries, timeout, unit);
		} else if (!Util.isWindows() && haveTTY()) {
			return tty(tries, timeout, unit);
		} else {
			return null;
		}
	}

	/**
	 * Returns a regular ConsoleInput based upon System.in
	 */
	private static ConsoleInput stdin(int tries, int timeout, TimeUnit unit) {
		return new ConsoleInput(tries, timeout, unit) {
			@Override
			protected Callable<String> readerTask() {
				return new ConsoleInputReadTask(System.in);
			}
		};
	}

	private static boolean haveTTY() {
		if (Files.isReadable(TTY)) {
			try (InputStream is = Files.newInputStream(TTY)) {
				return true;
			} catch (IOException e) {
				// Ignore
			}
		}
		return false;
	}

	/**
	 * Returns a ConsoleInput based upon /dev/tty which only works on Linux and Mac.
	 */
	private static ConsoleInput tty(int tries, int timeout, TimeUnit unit) {
		return new ConsoleInput(tries, timeout, unit) {
			@Override
			protected Callable<String> readerTask() throws IOException {
				return new ConsoleInputReadTask(Files.newInputStream(TTY));
			}
		};
	}

	private ConsoleInput(int tries, int timeout, TimeUnit unit) {
		this.tries = tries;
		this.timeout = timeout;
		this.unit = unit;
	}

	protected abstract Callable<String> readerTask() throws IOException;

	public String readLine() {
		ExecutorService ex = Executors.newSingleThreadExecutor();
		String input = null;
		try {
			// start working
			for (int i = 0; i < tries; i++) {
				Future<String> result = null;
				try {
					result = ex.submit(readerTask());
					input = result.get(timeout, unit);
					break;
				} catch (ExecutionException | IOException e) {
					Util.verboseMsg("Error accessing console", e);
				} catch (TimeoutException e) {
					result.cancel(true);
				} catch (InterruptedException ie) {
					throw new RuntimeException(ie);
				}
			}
		} finally {
			ex.shutdownNow();
		}
		return input;
	}
}