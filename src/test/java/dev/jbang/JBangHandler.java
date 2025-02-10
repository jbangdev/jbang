package dev.jbang;

import java.io.OutputStream;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

import dev.jbang.util.JBangFormatter;

public class JBangHandler extends StreamHandler {
	private OutputStream currentOut;

	public JBangHandler() {
		super(System.err, new JBangFormatter());
		currentOut = System.err;
	}

	@Override
	public void publish(LogRecord record) {
		updateStream();
		super.publish(record);
		flush();
	}

	@Override
	public void close() {
		updateStream();
		flush();
	}

	private void updateStream() {
		if (currentOut != System.err) {
			setOutputStream(System.err);
			currentOut = System.err;
		}
	}
}
