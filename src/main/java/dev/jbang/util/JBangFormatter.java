package dev.jbang.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * This JUL Formatter is used to format JUL log messages in such a way that they
 * look similar to JBang log messages.
 */
public class JBangFormatter extends Formatter {
	private final Date dat = new Date();

	@Override
	public synchronized String format(LogRecord record) {
		dat.setTime(record.getMillis());
		String source;
		if (record.getSourceClassName() != null) {
			source = record.getSourceClassName();
			if (record.getSourceMethodName() != null) {
				source += " " + record.getSourceMethodName();
			}
		} else {
			source = record.getLoggerName();
		}
		String message = formatMessage(record);
		String throwable = "";
		if (record.getThrown() != null) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println();
			record.getThrown().printStackTrace(pw);
			pw.close();
			throwable = sw.toString();
		}
		String format = "[jbang] ";
		if (Util.isVerbose()) {
			format += "[%1$tF %1$tT] ";
		}
		if (record.getLevel().intValue() > Level.INFO.intValue()) {
			format += "[%4$s] ";
		}
		format += "%5$s%n";
		return String.format(format,
				dat,
				source,
				record.getLoggerName(),
				record.getLevel().getLocalizedName(),
				message,
				throwable);
	}
}