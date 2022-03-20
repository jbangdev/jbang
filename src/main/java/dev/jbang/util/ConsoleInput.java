package dev.jbang.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.*;

public abstract class ConsoleInput {
	private final int tries;
	private final int timeout;
	private final TimeUnit unit;

	/**
	 * Will either return a ConsoleInput that enables reading a line from the
	 * console (using stdin or a tty) or it will return <code>null</code> if no
	 * console is available.
	 */
	public static ConsoleInput get(int tries, int timeout, TimeUnit unit) {
		if (Util.haveConsole()) {
			return stdin(tries, timeout, unit);
		} else if (!Util.isWindows()) {
			return tty(tries, timeout, unit);
		} else {
			return null;
		}
	}

	/**
	 * Returns a regular ConsoleInput based upon System.in
	 */
	public static ConsoleInput stdin(int tries, int timeout, TimeUnit unit) {
		return new ConsoleInput(tries, timeout, unit) {
			@Override
			protected Callable<String> readerTask() {
				return new ConsoleInputReadTask(System.in);
			}
		};
	}

	/**
	 * Returns a ConsoleInput based upon /dev/tty which only works on Linux and Mac.
	 */
	public static ConsoleInput tty(int tries, int timeout, TimeUnit unit) {
		return new ConsoleInput(tries, timeout, unit) {
			@Override
			protected Callable<String> readerTask() throws IOException {
				return new ConsoleInputReadTask(new FileInputStream("/dev/tty"));
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
					e.getCause().printStackTrace();
				} catch (TimeoutException e) {
					if (result != null) {
						result.cancel(true);
					}
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