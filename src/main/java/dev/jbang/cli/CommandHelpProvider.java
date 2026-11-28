package dev.jbang.cli;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.aesh.command.DocFormat;
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
		return buildHeader(null);
	}

	@Override
	default String getHeader(DocFormat format) {
		return buildHeader(format);
	}

	/**
	 * Renders the prose and examples. For terminal help ({@code format == null})
	 * examples are shown as a two-space-indented list. For AsciiDoc and Markdown
	 * they are wrapped in a fenced source block so each example renders as its own
	 * copyable line instead of being collapsed into one paragraph.
	 */
	default String buildHeader(DocFormat format) {
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
			appendExamplesBlock(sb, examples, format);
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	static void appendExamplesBlock(StringBuilder sb, List<String> examples, DocFormat format) {
		if (format == DocFormat.ASCIIDOC) {
			sb.append("\n[source,bash]\n----\n");
			for (String ex : examples) {
				sb.append(ex).append('\n');
			}
			sb.append("----\n");
		} else if (format == DocFormat.MARKDOWN || format == DocFormat.SKILL) {
			sb.append("\n```bash\n");
			for (String ex : examples) {
				sb.append(ex).append('\n');
			}
			sb.append("```\n");
		} else {
			for (String ex : examples) {
				sb.append("  ").append(ex).append('\n');
			}
		}
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
