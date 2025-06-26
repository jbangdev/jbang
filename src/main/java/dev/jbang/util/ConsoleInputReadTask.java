package dev.jbang.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * A reader task that is run in a separate thread, using
 * {@code Executors.newSingleThreadExecutor()}, to read a line of text (String)
 * from the console. The calling thread will wait for a pre-determined number of
 * seconds for a result to be returned by the reader task. If the calling thread
 * times out, it immediately interrupts the reader task signalling that the read
 * operation should be aborted (in which case {@code null} is returned). Note
 * that the user must press {@code ENTER} on the keyboard to indicate that a
 * line of text has been entered. Empty lines of text are supported.
 */
public class ConsoleInputReadTask implements Callable<String> {
	private final Scanner sc;

	public ConsoleInputReadTask(InputStream in) {
		this.sc = new Scanner(in);
	}

	public String call() throws IOException {
		String input;
		do {
			try {
				// wait until we have data to complete a nextLine()
				while (!sc.hasNextLine()) {
					Thread.sleep(200);
				}
				input = sc.nextLine();
			} catch (InterruptedException e) {
				return null;
			}
		} while ("".equals(input));
		return input;
	}
}