package dev.jbang.cli;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.aesh.command.HelpEntry;
import org.aesh.command.HelpSectionProvider;

/**
 * Mix-in for commands that want descriptive prose and an {@code Examples:}
 * block in their help. A command implements this interface directly (aesh uses
 * the command itself as its {@link HelpSectionProvider} when no provider class
 * is annotated), overriding {@link #prose()} and/or {@link #examples()}. aesh
 * injects {@link #getHeader()} between the synopsis and the options table.
 * <p>
 * Text may use the standard aesh variables, most usefully
 * {@code ${COMMAND-FULL-NAME}} which expands to the fully qualified command
 * (e.g. {@code jbang run}).
 */
public interface CommandHelpProvider extends HelpSectionProvider {

	/** Optional descriptive paragraph(s). Return {@code null} for none. */
	default String prose() {
		return null;
	}

	/**
	 * Example invocations, one per line, shown under an {@code Examples:} header.
	 * Use {@code ${COMMAND-FULL-NAME}} for the fully qualified command name.
	 */
	default List<String> examples() {
		return Collections.emptyList();
	}

	@Override
	default String getHeader() {
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
	default String getFooter() {
		return null;
	}

	@Override
	default Map<String, List<HelpEntry>> getAdditionalSections() {
		return Collections.emptyMap();
	}
}
