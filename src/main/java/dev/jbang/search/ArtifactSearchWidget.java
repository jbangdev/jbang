package dev.jbang.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.artifact.Artifact;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp.Capability;

/**
 * A widget for searching artifacts. Initially from veles but changed to be Java
 * 8 compatiable and added additional features. Should/will move to jline4
 * alternative which/when available.
 */
public class ArtifactSearchWidget {
	private final Terminal terminal;
	private final Attributes attrs;
	private final Set<Artifact> packages;
	private final ArtifactSearch mavenCentralClient = SolrArtifactSearch.createCsc();

	public ArtifactSearchWidget(Terminal terminal) {
		this.terminal = terminal;
		this.attrs = terminal.getAttributes();
		this.packages = LocalMavenRepository.packages()
			.stream()
			.filter(p -> !p.getArtifactId().contains("-parent"))
			.collect(Collectors.toSet());
	}

	public Artifact search() {
		terminal.enterRawMode();

		Combobox<Fuzz.SearchResult<Artifact>> artifactCombobox = new Combobox<>(terminal);
		artifactCombobox.filterCompletions(this::fuzzySearchArtifacts);
		artifactCombobox.renderItem(r -> new AttributedString(
				String.format("%s:%s:%s", r.item().getGroupId(), r.highlightTarget(), r.item().getVersion()),
				AttributedStyle.DEFAULT));
		artifactCombobox.withPrefix("Search (Ctrl-U to search central): ");
		artifactCombobox.handle("search_remote", KeyMap.ctrl('u'), (g) -> {
			try {
				String query = g.query().toString();
				artifactCombobox.withPrefix("Querying central: ");
				artifactCombobox.render();
				ArtifactSearch.SearchResult result = mavenCentralClient.findArtifacts(query, 50);
				result.artifacts.forEach(a -> {
					packages.add(a);
				});

				artifactCombobox.withPrefix("Search (Ctrl-U to search central): ");
				List<Combobox.ComboboxAction> actions = new ArrayList<>();
				actions.add(new Combobox.UpdateCompletions());
				return actions;
			} catch (Exception e) {
				artifactCombobox.withPrefix("Failed - " + e.getMessage() + ": ");
				return Collections.<Combobox.ComboboxAction>emptyList();
			}
		});

		Combobox.ComboboxResult<Fuzz.SearchResult<Artifact>> artifactGav = artifactCombobox.prompt();

		terminal.setAttributes(attrs);
		terminal.puts(Capability.clear_screen);
		terminal.flush();

		return artifactGav.selection().item();
	}

	private List<Fuzz.SearchResult<Artifact>> fuzzySearchArtifacts(String query) {
		return Fuzz.search(packages, (gav) -> {
			SearchScorer scorer = SearchScorer.calculate(query, gav.getArtifactId());
			return new Fuzz.SearchResult<>(gav, scorer, query.length(), gav.getArtifactId().length());
		});
	}
}