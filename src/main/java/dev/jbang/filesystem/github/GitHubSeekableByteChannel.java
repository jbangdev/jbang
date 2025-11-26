package dev.jbang.filesystem.github;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import org.jspecify.annotations.NonNull;

/**
 * A SeekableByteChannel implementation for GitHub files.
 * Note: This is a simplified implementation that doesn't support seeking.
 */
class GitHubSeekableByteChannel implements SeekableByteChannel {

	private final GitHubPath path;
	private InputStream inputStream;
	private long position;
	private long size;
	private boolean open = true;

	GitHubSeekableByteChannel(GitHubPath path) throws IOException {
		this.path = path;
		GitHubFileSystemProvider provider = (GitHubFileSystemProvider) path.getFileSystem().provider();
		InputStream is = provider.newInputStream(path);
		// Read all content into memory for size calculation and seeking support
		// This is not ideal but necessary for a read-only channel
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int bytesRead;
		while ((bytesRead = is.read(buffer)) != -1) {
			baos.write(buffer, 0, bytesRead);
		}
		is.close();
		byte[] content = baos.toByteArray();
		this.size = content.length;
		this.inputStream = new ByteArrayInputStream(content);
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {
		if (!open) {
			throw new IOException("Channel is closed");
		}
		int bytesRead = 0;
		while (dst.hasRemaining() && inputStream.available() > 0) {
			byte[] buffer = new byte[Math.min(dst.remaining(), inputStream.available())];
			int read = inputStream.read(buffer);
			if (read == -1) {
				break;
			}
			dst.put(buffer, 0, read);
			bytesRead += read;
			position += read;
		}
		return bytesRead > 0 ? bytesRead : -1;
	}

	@Override
	public int write(ByteBuffer src) throws IOException {
		throw new java.nio.channels.NonWritableChannelException();
	}

	@Override
	public long position() throws IOException {
		return position;
	}

	@Override
	public SeekableByteChannel position(long newPosition) throws IOException {
		if (newPosition < 0) {
			throw new IllegalArgumentException("Position cannot be negative");
		}
		if (newPosition != position) {
			// Reset and skip
			inputStream.close();
			GitHubFileSystemProvider provider = (GitHubFileSystemProvider) path.getFileSystem().provider();
			inputStream = provider.newInputStream(path);
			inputStream.skip(newPosition);
			position = newPosition;
		}
		return this;
	}

	@Override
	public long size() throws IOException {
		return size;
	}

	@Override
	public SeekableByteChannel truncate(long size) throws IOException {
		throw new java.nio.channels.NonWritableChannelException();
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	@Override
	public void close() throws IOException {
		if (open) {
			open = false;
			if (inputStream != null) {
				inputStream.close();
			}
		}
	}
}

