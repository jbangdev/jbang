package dev.jbang.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.concurrent.Callable;

public class ConsoleInputReadTask implements Callable<String> {
	private final Scanner sc;

	public ConsoleInputReadTask(InputStream in) {
		this.sc = new Scanner(in);
	}

	public String call() throws IOException {
		String input;
		do {
			try {
				// wait until we have data to complete a readLine()
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