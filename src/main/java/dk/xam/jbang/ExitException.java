package dk.xam.jbang;

/**
 * Used when wanting to exit app from a command.
 */
public class ExitException extends RuntimeException {

	private final int status;

	public ExitException(int status) {
		this.status = status;
	}

	public ExitException(int status, Exception cause) {
		super(cause);
		this.status = status;
	}

	public int getStatus() {
		return status;
	}
}
