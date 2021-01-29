package dev.jbang;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;

public class ConsoleInputReadTask implements Callable<String> {
	public String call() throws IOException {
		BufferedReader br = new BufferedReader(
				new InputStreamReader(System.in));
		// System.out.println("ConsoleInputReadTask run() called.");
		String input;
		do {
			// System.out.println("Please type something: ");
			try {
				// wait until we have data to complete a readLine()
				while (!br.ready()) {
					Thread.sleep(200);
				}
				input = br.readLine();
			} catch (InterruptedException e) {
				// System.out.println("ConsoleInputReadTask() cancelled");
				return null;
			}
		} while ("".equals(input));
		// System.out.println("Thank You for providing input!");
		return input;
	}
}