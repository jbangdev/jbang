package dev.jbang;

/**
 * Used when wanting to exit app from a command.
 */
public class ExitException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public static final int EXIT_OK = 0;
	public static final int EXIT_GENERIC_ERROR = 1;
	public static final int EXIT_INVALID_INPUT = 2;
	public static final int EXIT_UNEXPECTED_STATE = 3;
	public static final int EXIT_INTERNAL_ERROR = 4;
	public static final int EXIT_EXECUTE = 255;

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

	public static ExitException invalidInput(String message) {
		return new ExitException(EXIT_INVALID_INPUT, message);
	}

	public static ExitException invalidInput(String message, Throwable cause) {
		return new ExitException(EXIT_INVALID_INPUT, message, cause);
	}

	public static ExitException unexpectedState(String message) {
		return new ExitException(EXIT_UNEXPECTED_STATE, message);
	}

	public static ExitException unexpectedState(String message, Throwable cause) {
		return new ExitException(EXIT_UNEXPECTED_STATE, message, cause);
	}

	public static ExitException genericError(String message) {
		return new ExitException(EXIT_GENERIC_ERROR, message);
	}

	public static ExitException genericError(String message, Throwable cause) {
		return new ExitException(EXIT_GENERIC_ERROR, message, cause);
	}

	public static ExitException genericError(Throwable cause) {
		return new ExitException(EXIT_GENERIC_ERROR, cause);
	}

	public static ExitException internalError(String message) {
		return new ExitException(EXIT_INTERNAL_ERROR, message);
	}

	public static ExitException internalError(String message, Throwable cause) {
		return new ExitException(EXIT_INTERNAL_ERROR, message, cause);
	}
}
