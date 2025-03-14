package dev.jbang.util;

import java.net.URL;
import java.net.URLConnection;

public class NoOpUrlConnection extends URLConnection {

	protected NoOpUrlConnection(URL url) {
		super(url);
	}

	@Override
	public void connect() {
	}
}
