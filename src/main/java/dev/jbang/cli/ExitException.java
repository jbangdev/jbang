package dev.jbang.cli;

/**
 * Used when wanting to exit app from a command.
 */
public class ExitException extends RuntimeException {

	public static final int EXIT_OK = 0;
	public static final int EXIT_GENERIC_ERROR = 1;
	public static final int EXIT_INVALID_INPUT = 2;
	public static final int EXIT_UNEXPECTED_STATE = 3;
	public static final int EXIT_INTERNAL_ERROR = 4;
	public static final int EXIT_EXECUTE = 255;

	private static final long serialVersionUID = 1L;

	private final int status;

	public ExitException(int status) {
		this.status = status;
	}

	public ExitException(int status, Throwable cause) {
		super(cause);
		this.status = status;
	}

	public ExitException(int status, String message, Throwable cause) {
		super(message, cause);
		this.status = status;
	}

	public ExitException(int status, String s) {
		this(status, s, null);
	}

	public int getStatus() {
		return status;
	}
}
