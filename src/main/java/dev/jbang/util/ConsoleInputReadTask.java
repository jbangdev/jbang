package dev.jbang.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * A reader task that is run in a separate thread, using
 * {@code Executors.newSingleThreadExecutor()}, to read a line of text (String)
 * from the console. Empty lines are ignored. The calling thread will wait for a
 * pre-determined number of seconds for a result to be returned by the reader
 * task. If the calling thread times out, it immediately interrupts the reader
 * task signalling that the read operation should be aborted (in which case
 * {@code null} is returned).
 */
public class ConsoleInputReadTask implements Callable<String> {

	/**
	 * Input stream state is maintained in the Scanner object across multiple calls
	 * to the reader task. The scanner object is never closed (do not call
	 * {@code Scanner.close} as it will close the underlying {@code InputStream}
	 * potentially closing {@code System.in}.
	 */
	private final Scanner sc;

	public ConsoleInputReadTask(InputStream in) {
		this.sc = new Scanner(in);
	}

	public String call() throws IOException {
		String input;
		do {
			try {
				// wait until we have enough data to complete a call to nextLine()
				while (!sc.hasNextLine()) {
					// Sleep for a brief moment to avoid busy waiting
					// and allow the reader task to be interrupted if the
					// calling thread times out
					Thread.sleep(200);
				}
				// read the available line of text from the InputStream
				input = sc.nextLine();
			} catch (InterruptedException e) {
				// the calling thread timed out and interrupted the reader task
				return null;
			}
			// if the input string is empty, retry by reading the next line
		} while ("".equals(input));
		return input;
	}
}