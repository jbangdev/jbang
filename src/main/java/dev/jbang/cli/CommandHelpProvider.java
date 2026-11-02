package dev.jbang.cli;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.aesh.command.HelpEntry;
import org.aesh.command.HelpSectionProvider;

/**
 * Base {@link HelpSectionProvider} that renders optional descriptive prose and
 * an {@code Examples:} block into a command's help text. aesh injects the
 * result of {@link #getHeader()} between the synopsis and the options table.
 * <p>
 * Subclasses override {@link #prose()} and/or {@link #examples()}. Text may use
 * the standard aesh variables, most usefully {@code ${COMMAND-FULL-NAME}} which
 * expands to the fully qualified command (e.g. {@code jbang run}).
 */
public abstract class CommandHelpProvider implements HelpSectionProvider {

	/** Optional descriptive paragraph(s). Return {@code null} for none. */
	protected String prose() {
		return null;
	}

	/**
	 * Example invocations, one per line, shown under an {@code Examples:} header.
	 * Use {@code ${COMMAND-FULL-NAME}} for the fully qualified command name.
	 */
	protected List<String> examples() {
		return Collections.emptyList();
	}

	@Override
	public String getHeader() {
		StringBuilder sb = new StringBuilder();
		String prose = prose();
		if (prose != null && !prose.trim().isEmpty()) {
			sb.append(prose.trim()).append('\n');
		}
		List<String> examples = examples();
		if (examples != null && !examples.isEmpty()) {
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append("Examples:\n");
			for (String ex : examples) {
				sb.append("  ").append(ex).append('\n');
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	@Override
	public String getFooter() {
		return null;
	}

	@Override
	public Map<String, List<HelpEntry>> getAdditionalSections() {
		return Collections.emptyMap();
	}
}
