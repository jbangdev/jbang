package dev.jbang.search;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.artifact.Artifact;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.Terminal.Signal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp.Capability;

import dev.jbang.dependencies.ArtifactResolver;
import dev.jbang.search.Fuzz.SearchFuzzedResult;

/**
 * A widget for searching artifacts. Initially from veles but changed to be Java
 * 8 compatiable and added additional features. Should/will move to jline4
 * alternative which/when available.
 */
public class ArtifactSearchWidget {

	// Main label: a bit stronger
	private static final AttributedStyle LABEL_STYLE = AttributedStyle.DEFAULT.bold();

	// Hint: softer color, no bold
	private static final AttributedStyle HINT_STYLE = AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE);

	private final Terminal terminal;
	private final Attributes attrs;
	// private final Set<Artifact> _artifactsToConsider;

	// private Artifact selectedRootArtifact = null;

	private final ArtifactSearch mavenCentralClient = SolrArtifactSearch.createCsc();

	private final PhaseHandler phase;

	/** handles the different phases of the artifact search widget */
	static class PhaseHandler {

		List<Phase> phases = new ArrayList<>();
		int index = 0;

		PhaseHandler(Phase... phases) {
			this.phases = Arrays.asList(phases);
		}

		Phase current() {
			return phases.get(index);
		}

		Phase next(Artifact artifact) {
			if (index < phases.size() - 1) {
				index++;
				phases.get(index).setArtifact(artifact);
			}
			return phases.get(index);
		}

		Phase prev(Artifact artifact) {
			if (index > 0) {
				index--;
				phases.get(index).setArtifact(artifact);
			}
			return phases.get(index);
		}

	}

	/**
	 * Phase keeps track of the shared state and common operations for the different
	 * phases of the artifact search widget
	 */
	interface Phase {
		AttributedString prefix();

		void setArtifact(Artifact artifact);

		Set<Artifact> artifactsToConsider();

		void remoteFetch(String query) throws IOException;

		List<SearchFuzzedResult<Artifact>> fuzzySearch(String query);

		Artifact focusedArtifact();

		Integer selector(Fuzz.SearchFuzzedResult<Artifact> item, List<SearchFuzzedResult<Artifact>> matches);
	}

	PhaseHandler setupPhaseHandler() {

		Phase rootPhase = new Phase() {
			private Artifact focusedArtifact = null;
			Set<Artifact> artifactsToConsider = SearchUtil.localMavenArtifacts(ArtifactResolver.getLocalMavenRepo())
				.stream()
				.filter(p -> !p.getArtifactId().contains("-parent"))
				.collect(Collectors.toSet());

			public Artifact focusedArtifact() {
				return focusedArtifact;
			}

			@Override
			public AttributedString prefix() {
				AttributedStringBuilder sb = new AttributedStringBuilder();

				sb.style(LABEL_STYLE);
				sb.append("Artifact  ");

				sb.style(HINT_STYLE);
				sb.append("(Tab=search central · Enter=select · →=versions): ");

				return sb.toAttributedString();

				// return "Search (TAB to search central, ENTER to select, -> to pick version):
				// ";
			}

			@Override
			public void remoteFetch(String query) throws IOException {
				int max = 200; // limits how many artifacts we will iterate on
				int found = 0;

				ArtifactSearch.SearchResult result = mavenCentralClient.findArtifacts(query, max);
				while (result != null) {
					for (Artifact artifact : result.artifacts) {
						if (artifactsToConsider().add(artifact)) {
							found++;
						} else {

						}
					}
					result = found < max ? mavenCentralClient.findNextArtifacts(result) : null;
				}
			}

			@Override
			public List<SearchFuzzedResult<Artifact>> fuzzySearch(String query) {
				return Fuzz.search(artifactsToConsider(), (gav) -> {
					String m = gav.getGroupId() + ":" + gav.getArtifactId() + ":" + gav.getVersion();
					SearchScorer scorer = SearchScorer.calculate(query, m);
					return new Fuzz.SearchFuzzedResult<>(gav, scorer, query.length(), m.length());
				});
			}

			@Override
			public void setArtifact(Artifact artifact) {
				focusedArtifact = artifact;
			}

			@Override
			public Set<Artifact> artifactsToConsider() {
				return artifactsToConsider;
			}

			@Override
			public Integer selector(Fuzz.SearchFuzzedResult<Artifact> item,
					List<SearchFuzzedResult<Artifact>> matches) {
				for (int i = 0; i < matches.size(); i++) {
					Artifact candidate = matches.get(i).item();
					Artifact selected = item.item();
					if (candidate.getGroupId().equals(selected.getGroupId())
							&& candidate.getArtifactId().equals(selected.getArtifactId())) {
						return i;
					}
				}
				return -1;
			}

		};

		Phase versionSelectionPhase = new Phase() {
			Set<Artifact> artifactsToConsider = new HashSet<Artifact>();

			private Artifact selectedArtifact = null;

			@Override
			public AttributedString prefix() {
				AttributedStringBuilder sb = new AttributedStringBuilder();

				sb.style(LABEL_STYLE);
				sb.append("Version   ");

				sb.style(HINT_STYLE);
				sb.append("(Tab=versions from central · Enter=select · ←=back): ");

				return sb.toAttributedString();

			}

			@Override
			public void setArtifact(Artifact artifact) {
				artifactsToConsider.clear();
				artifactsToConsider.add(artifact);
				artifactsToConsider
					.addAll(SearchUtil.localMavenArtifactsVersions(ArtifactResolver.getLocalMavenRepo(), artifact));
				selectedArtifact = artifact;
			}

			@Override
			public List<SearchFuzzedResult<Artifact>> fuzzySearch(String query) {
				return Fuzz.search(artifactsToConsider(), (gav) -> {
					String m = gav.getGroupId() + ":" + gav.getArtifactId() + ":" + gav.getVersion();
					SearchScorer scorer = SearchScorer.calculate(query, m);
					return new Fuzz.SearchFuzzedResult<>(gav, scorer, query.length(), m.length());
				});
			}

			@Override
			public void remoteFetch(String query) throws IOException {
				int max = 200; // limits how many artifacts we will iterate on
				int found = 0;

				// ignoring the typed in query as we really only care about version of the
				// selected artifact
				String actualQuery = selectedArtifact.getGroupId() + ":" + selectedArtifact.getArtifactId() + ":"
						+ selectedArtifact.getVersion();

				ArtifactSearch.SearchResult result = mavenCentralClient.findArtifacts(actualQuery, max);
				while (result != null) {
					for (Artifact artifact : result.artifacts) {
						if (artifactsToConsider().add(artifact)) {
							found++;
						} else {

						}
					}
					result = found < max ? mavenCentralClient.findNextArtifacts(result) : null;
				}
			}

			@Override
			public Set<Artifact> artifactsToConsider() {
				return artifactsToConsider;
			}

			@Override
			public Artifact focusedArtifact() {
				return selectedArtifact;
			}

			@Override
			public Integer selector(Fuzz.SearchFuzzedResult<Artifact> item,
					List<SearchFuzzedResult<Artifact>> matches) {
				for (int i = 0; i < matches.size(); i++) {
					Artifact candidate = matches.get(i).item();
					Artifact selected = item.item();
					if (candidate.getGroupId().equals(selected.getGroupId())
							&& candidate.getArtifactId().equals(selected.getArtifactId())
							&& candidate.getVersion().equals(selected.getVersion())) {
						return i;
					}
				}
				return -1;
			}
		};

		return new PhaseHandler(rootPhase, versionSelectionPhase);
	}

	public ArtifactSearchWidget(Terminal terminal) {
		this.terminal = terminal;
		this.attrs = terminal.getAttributes();
		this.phase = this.setupPhaseHandler();
	}

	void cleanup() {
		terminal.setAttributes(attrs);
		terminal.puts(Capability.clear_screen);
		terminal.flush();
	}

	public Artifact search(String initialQuery) {
		terminal.enterRawMode();

		terminal.handle(Signal.INT, (e) -> {
			cleanup();
			System.exit(EXIT_UNEXPECTED_STATE);
		});

		Combobox<Fuzz.SearchFuzzedResult<Artifact>> artifactCombobox = new Combobox<>(terminal, initialQuery);
		artifactCombobox.filterCompletions(this::fuzzySearchArtifacts);
		artifactCombobox.selector((item, matches) -> phase.current().selector(item, matches));
		artifactCombobox.renderItem(r -> new AttributedString(
				String.format(r.highlightTarget()),
				AttributedStyle.DEFAULT));

		artifactCombobox.withPrefix(phase.current().prefix());

		artifactCombobox.handle("search_remote", "\t", (g) -> {
			try {
				String query = g.query().toString();
				AttributedString oldPrefix = artifactCombobox.prefix();
				artifactCombobox.withPrefix(new AttributedString("Searching Maven Central....: "));
				artifactCombobox.render();

				phase.current().remoteFetch(query);

				artifactCombobox.withPrefix(oldPrefix);
				List<Combobox.ComboboxAction> actions = new ArrayList<>();
				actions.add(new Combobox.UpdateCompletions());
				return actions;
			} catch (Exception e) {
				artifactCombobox.withPrefix(new AttributedString("Failed - " + e.getMessage() + ": "));
				return Collections.<Combobox.ComboboxAction>emptyList();
			}
		});

		artifactCombobox.handle("next_phase", "\u001b[C", (g) -> {
			if (g.matches().size() == 0) {
				return Collections.<Combobox.ComboboxAction>emptyList();
			}

			Fuzz.SearchFuzzedResult<Artifact> selected = g.matches().get(g.selectedIndex());
			phase.next(selected.item());

			artifactCombobox.withPrefix(phase.current().prefix());
			List<Combobox.ComboboxAction> actions = new ArrayList<>();
			actions.add(new Combobox.UpdateCompletions());
			actions.add(new Combobox.SelectItem<>(selected));
			return actions;
		});

		artifactCombobox.handle("previous_phase", "\u001b[D", (g) -> {

			Fuzz.SearchFuzzedResult<Artifact> selected = g.matches().get(g.selectedIndex());

			phase.prev(selected.item());

			artifactCombobox.withPrefix(phase.current().prefix());
			List<Combobox.ComboboxAction> actions = new ArrayList<>();
			actions.add(new Combobox.UpdateCompletions());
			actions.add(new Combobox.SelectItem<>(selected));
			return actions;
		});

		Combobox.ComboboxResult<Fuzz.SearchFuzzedResult<Artifact>> artifactGav = artifactCombobox.prompt();

		cleanup();

		return artifactGav.selection().item();
	}

	private List<Fuzz.SearchFuzzedResult<Artifact>> fuzzySearchArtifacts(String query) {

		return phase.current().fuzzySearch(query);

	}
}