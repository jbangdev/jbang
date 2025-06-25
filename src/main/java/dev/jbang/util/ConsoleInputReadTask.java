package dev.jbang.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.concurrent.Callable;

public class ConsoleInputReadTask implements Callable<String> {
	private final InputStream in;

	public ConsoleInputReadTask(InputStream in) {
		this.in = in;
	}

	public String call() throws IOException {
		Scanner sc = new Scanner(in);
		return sc.nextLine();
	}
}