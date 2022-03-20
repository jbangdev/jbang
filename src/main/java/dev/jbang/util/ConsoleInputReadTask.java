package dev.jbang.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;

public class ConsoleInputReadTask implements Callable<String> {
	private final InputStream in;

	public ConsoleInputReadTask(InputStream in) {
		this.in = in;
	}

	public String call() throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		String input;
		do {
			try {
				// wait until we have data to complete a readLine()
				while (!br.ready()) {
					Thread.sleep(200);
				}
				input = br.readLine();
			} catch (InterruptedException e) {
				return null;
			}
		} while ("".equals(input));
		return input;
	}
}