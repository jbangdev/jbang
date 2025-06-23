package dev.jbang.util;

import java.io.IOException;
import java.util.concurrent.*;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class ConsoleInput {
	private final int tries;
	private final int timeout;
	private final TimeUnit unit;

	/**
	 * Will either return a ConsoleInput that enables reading a line from the
	 * console (using jline) or it will return <code>null</code> if no console is
	 * available.
	 */
	public static ConsoleInput get(int tries, int timeout, TimeUnit unit) {
		if (Util.haveConsole()) {
			return stdin(tries, timeout, unit);
		} else {
			return null;
		}
	}

	/**
	 * Returns a regular ConsoleInput based upon System.in
	 */
	private static ConsoleInput stdin(int tries, int timeout, TimeUnit unit) {
		return new ConsoleInput(tries, timeout, unit);
	}

	private ConsoleInput(int tries, int timeout, TimeUnit unit) {
		this.tries = tries;
		this.timeout = timeout;
		this.unit = unit;
	}

	public String readLine() {
		ExecutorService ex = Executors.newSingleThreadExecutor();
		String input = null;

		try (Terminal terminal = TerminalBuilder.builder().build()) {
			LineReader lineReader = LineReaderBuilder.builder()
				.terminal(terminal)
				.build();
			// start working
			for (int i = 0; i < tries; i++) {
				Callable<String> readerTask = () -> lineReader.readLine("");
				Future<String> result = ex.submit(readerTask);
				try {
					input = result.get(timeout, unit);
					break;
				} catch (ExecutionException e) {
					Util.verboseMsg("Error accessing console", e);
				} catch (TimeoutException e) {
					result.cancel(true);
				} catch (InterruptedException ie) {
					throw new RuntimeException(ie);
				}
			}
		} catch (IOException e) {
			Util.verboseMsg("Error accessing console", e);
		} finally {
			ex.shutdownNow();
		}
		return input;
	}

}