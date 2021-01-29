package dev.jbang;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ConsoleInput {
	private final int tries;
	private final int timeout;
	private final TimeUnit unit;

	public ConsoleInput(int tries, int timeout, TimeUnit unit) {
		this.tries = tries;
		this.timeout = timeout;
		this.unit = unit;
	}

	public String readLine() {
		ExecutorService ex = Executors.newSingleThreadExecutor();
		String input = null;
		try {
			// start working
			for (int i = 0; i < tries; i++) {
				// Util.infoMsg(String.valueOf(i + 1) + ". loop");
				Future<String> result = ex.submit(
						new ConsoleInputReadTask());
				try {
					input = result.get(timeout, unit);
					break;
				} catch (ExecutionException e) {
					e.getCause().printStackTrace();
				} catch (TimeoutException e) {
					// Util.infoMsg("Cancelling reading task");
					result.cancel(true);
					// Util.infoMsg("\nThread cancelled. input is null");
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