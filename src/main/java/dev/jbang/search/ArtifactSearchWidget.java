package dev.jbang.search;

import static dev.tamboui.toolkit.Toolkit.*;

import java.io.IOError;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.eclipse.aether.artifact.Artifact;

import dev.jbang.dependencies.ArtifactResolver;
import dev.jbang.util.Util;

import dev.tamboui.style.Color;
import dev.tamboui.style.Modifier;
import dev.tamboui.style.Style;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.bindings.BindingSets;
import dev.tamboui.tui.bindings.Bindings;
import dev.tamboui.tui.bindings.KeyTrigger;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;

/**
 * Interactive TUI for searching and selecting Maven artifacts. Built with the
 * TamboUI toolkit DSL.
 */
public class ArtifactSearchWidget {

	private static final Style HIGHLIGHT_STYLE = Style.EMPTY.bg(Color.indexed(236)).addModifier(Modifier.BOLD);

	private final ArtifactSearch centralClient = SolrArtifactSearch.createCsc();

	// --- Phase ---
	private enum Phase {
		ARTIFACT, VERSION
	}

	private Phase phase = Phase.ARTIFACT;

	// --- State ---
	private final TextInputState searchInput = new TextInputState();
	private Set<Artifact> localArtifacts;
	private List<Fuzz.SearchFuzzedResult<Artifact>> artifactMatches = Collections.emptyList();
	private int artifactIndex = 0;

	private Artifact selectedArtifact = null;
	private List<Artifact> versionList = Collections.emptyList();
	private int versionIndex = 0;

	private final boolean offline = Util.isOffline();
	private String statusMessage = "";
	private SearchIntentClassifier.Intent currentIntent = SearchIntentClassifier.classify("");
	private boolean searchingCentral = false;
	private Artifact result = null;
	private ToolkitRunner runner;

	// --- Async search state ---
	private final AtomicLong searchGeneration = new AtomicLong(0);
	private ToolkitRunner.ScheduledAction pendingSearch = null;

	public ArtifactSearchWidget() {
		localArtifacts = SearchUtil.localMavenArtifacts(ArtifactResolver.getLocalMavenRepo())
			.stream()
			.filter(p -> !p.getArtifactId().contains("-parent"))
			.collect(Collectors.toSet());
	}

	/**
	 * Opens the interactive search TUI and returns the selected artifacts.
	 *
	 * @param initialQuery optional initial search text
	 * @return the selected Artifacts (one or more)
	 */
	public Artifact search(String initialQuery) {
		if (initialQuery != null && !initialQuery.isEmpty()) {
			searchInput.setText(initialQuery);
			searchInput.moveCursorToEnd();
			refreshArtifactMatches();
		}

		// Unbind Tab from focus cycling so our handler receives it
		Bindings bindings = BindingSets.standard()
			.toBuilder()
			.unbind(Actions.FOCUS_NEXT)
			.unbind(Actions.FOCUS_PREVIOUS)
			.unbind(Actions.QUIT)
			.bind(KeyTrigger.ctrl('c'), Actions.QUIT)
			.build();
		try (ToolkitRunner r = ToolkitRunner.builder().bindings(bindings).build()) {
			this.runner = r;
			r.run(this::render);
		} catch (Exception e) {
			throw new RuntimeException("Terminal error", e);
		}

		if (result == null) {
			throw new IOError(new Exception("User cancelled"));
		}
		return result;
	}

	// -------------------------------------------------------------------------
	// Render
	// -------------------------------------------------------------------------

	private Element render() {
		return dock()
			.top(renderSearchInput())
			.bottom(column(renderDetails(), renderHelpBar()))
			.center(renderMainArea())
			.id("main")
			.focusable()
			.onKeyEvent(this::handleKey);
	}

	private Element renderSearchInput() {
		String suffix = searchingCentral ? " \u23f3" : "";
		String intentLabel = currentIntent.label.isEmpty() ? "" : " \u2014 " + currentIntent.label;
		return panel(" Search" + suffix + intentLabel + " ",
				textInput(searchInput)
					.placeholder("Type to search...")
					.focusable(false)
					.cursorRequiresFocus(false))
			.rounded()
			.borderColor(searchingCentral ? Color.YELLOW : Color.CYAN);
	}

	private Element renderMainArea() {
		if (phase == Phase.VERSION) {
			return row(
					renderArtifactResults(),
					renderVersionPane());
		}
		return renderArtifactResults();
	}

	@SuppressWarnings("unchecked")
	private Element renderArtifactResults() {
		if (artifactMatches.isEmpty()) {
			String hint = searchInput.text().isEmpty()
					? "Start typing to search local Maven artifacts"
					: searchingCentral
							? "Searching Maven Central..."
							: "No matches found. Press Tab/F5 to search Maven Central.";
			return panel(" Results ", text("  " + hint).dim())
				.rounded()
				.borderColor(Color.DARK_GRAY);
		}

		List<StyledElement<?>> items = new ArrayList<>();
		for (int i = 0; i < artifactMatches.size(); i++) {
			items.add(highlightedGav(artifactMatches.get(i)));
		}

		String resultTitle = currentIntent.prefersCentral() && !currentIntent.label.isEmpty()
				? " Results \u2014 " + currentIntent.label + " (" + artifactMatches.size() + ") "
				: " Results (" + artifactMatches.size() + ") ";

		return list(items.toArray(new StyledElement[0]))
			.selected(artifactIndex)
			.highlightStyle(HIGHLIGHT_STYLE)
			.highlightSymbol("\u25b8 ")
			.title(resultTitle)
			.rounded()
			.borderColor(phase == Phase.ARTIFACT ? Color.CYAN : Color.DARK_GRAY);
	}

	private Element renderVersionPane() {
		if (versionList.isEmpty()) {
			String hint = searchingCentral
					? "Fetching versions..."
					: "No local versions.\nPress Tab/F5 to fetch from Central.";
			return panel(" Versions ",
					text("  " + hint).dim())
				.rounded()
				.borderColor(Color.YELLOW);
		}

		List<StyledElement<?>> items = new ArrayList<>();
		for (int i = 0; i < versionList.size(); i++) {
			items.add(text(versionList.get(i).getVersion()).green());
		}

		return list(items.toArray(new StyledElement[0]))
			.selected(versionIndex)
			.highlightStyle(HIGHLIGHT_STYLE.fg(Color.YELLOW))
			.highlightSymbol("\u25b8 ")
			.title(" Versions (" + versionList.size() + ") ")
			.rounded()
			.borderColor(Color.YELLOW);
	}

	private Element renderDetails() {
		if (phase == Phase.VERSION) {
			return renderVersionDetails();
		}
		return renderArtifactDetails();
	}

	private Element renderArtifactDetails() {
		if (artifactMatches.isEmpty() || artifactIndex >= artifactMatches.size()) {
			return spacer(0);
		}

		Artifact a = artifactMatches.get(artifactIndex).item();
		String gav = a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion();

		return panel(" Details ",
				row(
						column(
								row(text("Group:    ").dim(), text(a.getGroupId()).bold()),
								row(text("Artifact: ").dim(), text(a.getArtifactId()).bold()),
								row(text("Version:  ").dim(), text(a.getVersion()).green().bold())),
						spacer(),
						text("//DEPS " + gav).cyan()))
			.rounded()
			.borderColor(Color.DARK_GRAY)
			.length(5);
	}

	private Element renderVersionDetails() {
		String selectedVersion = versionIndex < versionList.size()
				? versionList.get(versionIndex).getVersion()
				: "?";
		String gav = selectedArtifact.getGroupId() + ":" + selectedArtifact.getArtifactId() + ":" + selectedVersion;

		return panel(" Add as ",
				text("//DEPS " + gav).cyan().bold())
			.rounded()
			.borderColor(Color.DARK_GRAY)
			.length(3);
	}

	private Element renderHelpBar() {
		String msg = statusMessage;
		if (phase == Phase.ARTIFACT) {
			return row(
					text(" \u2191\u2193").yellow().bold(), text(" Navigate  ").dim(),
					text("Enter").yellow().bold(), text(" Select  ").dim(),
					text("Tab/F5").yellow().bold(), text(offline ? " Central (off)  " : " Central  ").dim(),
					text("Esc").yellow().bold(), text(" Quit").dim(),
					spacer(),
					text(msg).green());
		} else {
			return row(
					text(" \u2191\u2193").yellow().bold(), text(" Navigate  ").dim(),
					text("Enter").yellow().bold(), text(" Select  ").dim(),
					text("Tab/F5").yellow().bold(), text(" Fetch versions  ").dim(),
					text("Esc").yellow().bold(), text(" Back").dim(),
					spacer(),
					text(msg).green());
		}
	}

	// -------------------------------------------------------------------------
	// Match highlighting
	// -------------------------------------------------------------------------

	private StyledElement<?> highlightedGav(Fuzz.SearchFuzzedResult<Artifact> match) {
		String highlighted = match.highlightTarget("\u0001", "\u0002");
		List<StyledElement<?>> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inHighlight = false;

		for (int i = 0; i < highlighted.length(); i++) {
			char c = highlighted.charAt(i);
			if (c == '\u0001') {
				if (current.length() > 0) {
					parts.add(text(current.toString()).dim());
					current.setLength(0);
				}
				inHighlight = true;
			} else if (c == '\u0002') {
				if (current.length() > 0) {
					parts.add(text(current.toString()).green().bold());
					current.setLength(0);
				}
				inHighlight = false;
			} else {
				current.append(c);
			}
		}
		if (current.length() > 0) {
			parts.add(inHighlight
					? text(current.toString()).green().bold()
					: text(current.toString()).dim());
		}

		return row(parts.toArray(new StyledElement[0]));
	}

	// -------------------------------------------------------------------------
	// Event handling
	// -------------------------------------------------------------------------

	private EventResult handleKey(KeyEvent event) {
		// Tab/F5 - search Central (async)
		if (event.code() == KeyCode.TAB || event.code() == KeyCode.F5) {
			searchCentralAsync();
			return EventResult.HANDLED;
		}

		if (phase == Phase.ARTIFACT) {
			return handleArtifactKey(event);
		} else {
			return handleVersionKey(event);
		}
	}

	private EventResult handleArtifactKey(KeyEvent event) {
		if (event.isUp()) {
			if (artifactIndex > 0) {
				artifactIndex--;
			}
			return EventResult.HANDLED;
		}
		if (event.isDown()) {
			if (artifactIndex < artifactMatches.size() - 1) {
				artifactIndex++;
			}
			return EventResult.HANDLED;
		}
		if (event.code() == KeyCode.ESCAPE) {
			if (searchInput.text().isEmpty()) {
				runner.quit();
			} else {
				searchInput.setText("");
				refreshArtifactMatches();
				statusMessage = "";
			}
			return EventResult.HANDLED;
		}

		// Enter - go to version phase
		if (event.isConfirm() && !artifactMatches.isEmpty()) {
			enterVersionPhase();
			return EventResult.HANDLED;
		}

		if (handleTextInputKey(searchInput, event)) {
			refreshArtifactMatches();
			// Auto-trigger Central for class/import/error intents (with debounce)
			if (currentIntent.prefersCentral() && !offline) {
				scheduleDebouncedCentralSearch();
			}
			return EventResult.HANDLED;
		}
		return EventResult.UNHANDLED;
	}

	private EventResult handleVersionKey(KeyEvent event) {
		if (event.isUp()) {
			if (versionIndex > 0) {
				versionIndex--;
			}
			return EventResult.HANDLED;
		}
		if (event.isDown()) {
			if (versionIndex < versionList.size() - 1) {
				versionIndex++;
			}
			return EventResult.HANDLED;
		}
		if (event.code() == KeyCode.ESCAPE) {
			goBackToArtifactPhase();
			return EventResult.HANDLED;
		}
		// Enter - select version, done
		if (event.isConfirm()) {
			if (!versionList.isEmpty() && versionIndex < versionList.size()) {
				result = versionList.get(versionIndex);
				runner.quit();
			}
			return EventResult.HANDLED;
		}
		return EventResult.UNHANDLED;
	}

	// -------------------------------------------------------------------------
	// Phase transitions
	// -------------------------------------------------------------------------

	private void enterVersionPhase() {
		if (artifactIndex >= artifactMatches.size()) {
			return;
		}
		selectedArtifact = artifactMatches.get(artifactIndex).item();
		phase = Phase.VERSION;

		Set<Artifact> localVersions = SearchUtil.localMavenArtifactsVersions(
				ArtifactResolver.getLocalMavenRepo(), selectedArtifact);
		versionList = new ArrayList<>(localVersions);
		sortVersionsDesc(versionList);
		versionIndex = 0;
		statusMessage = "";
	}

	private void goBackToArtifactPhase() {
		phase = Phase.ARTIFACT;
		statusMessage = "";
	}

	// -------------------------------------------------------------------------
	// Search logic
	// -------------------------------------------------------------------------

	private void refreshArtifactMatches() {
		String query = searchInput.text();
		currentIntent = SearchIntentClassifier.classify(query);
		String localQuery = currentIntent.localQuery;
		if (localQuery.isEmpty()) {
			artifactMatches = Collections.emptyList();
		} else {
			List<Fuzz.SearchFuzzedResult<Artifact>> allMatches = Fuzz.search(localArtifacts, (artifact) -> {
				String target = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
				SearchScorer scorer = SearchScorer.calculate(localQuery, target);
				return new Fuzz.SearchFuzzedResult<>(artifact, scorer, localQuery.length(), target.length());
			});
			// Deduplicate by groupId:artifactId, keeping highest-scored entry
			artifactMatches = deduplicateByGA(allMatches);
		}
		artifactIndex = 0;
	}

	/**
	 * Deduplicates search results by groupId:artifactId, keeping the highest-scored
	 * entry for each unique g:a combination.
	 */
	private static List<Fuzz.SearchFuzzedResult<Artifact>> deduplicateByGA(
			List<Fuzz.SearchFuzzedResult<Artifact>> matches) {
		Map<String, Fuzz.SearchFuzzedResult<Artifact>> best = new LinkedHashMap<>();
		for (Fuzz.SearchFuzzedResult<Artifact> match : matches) {
			Artifact a = match.item();
			String key = a.getGroupId() + ":" + a.getArtifactId();
			Fuzz.SearchFuzzedResult<Artifact> existing = best.get(key);
			if (existing == null || match.similarity() > existing.similarity()) {
				best.put(key, match);
			}
		}
		return new ArrayList<>(best.values());
	}

	private void scheduleDebouncedCentralSearch() {
		// Cancel any pending debounced search
		if (pendingSearch != null) {
			pendingSearch.cancel();
			pendingSearch = null;
		}
		pendingSearch = runner.schedule(() -> {
			runner.runOnRenderThread(() -> {
				pendingSearch = null;
				searchCentralAsync();
			});
		}, Duration.ofMillis(400));
	}

	/**
	 * Triggers an async Central search. The search runs on the scheduler thread and
	 * results are marshalled back to the render thread. A generation counter
	 * ensures stale results from earlier searches are discarded.
	 */
	private void searchCentralAsync() {
		if (searchingCentral) {
			return; // already in progress
		}
		if (offline) {
			statusMessage = "Offline mode \u2014 Central search disabled";
			return;
		}

		String query = searchInput.text();
		if (query.isEmpty() && phase == Phase.ARTIFACT) {
			return;
		}

		// Cancel any pending debounced search
		if (pendingSearch != null) {
			pendingSearch.cancel();
			pendingSearch = null;
		}

		searchingCentral = true;
		String intentLabel = currentIntent.label.isEmpty() ? "" : " (" + currentIntent.label + ")";
		statusMessage = "Searching Maven Central..." + intentLabel;

		final long gen = searchGeneration.incrementAndGet();
		final Phase currentPhase = phase;
		final Artifact currentArtifact = selectedArtifact;
		final String centralQuery = currentIntent.centralQuery;
		final Set<String> seenVersions = phase == Phase.VERSION
				? versionList.stream().map(Artifact::getVersion).collect(Collectors.toSet())
				: null;

		// Run on scheduler thread (non-blocking for UI)
		runner.schedule(() -> {
			try {
				if (currentPhase == Phase.VERSION && currentArtifact != null) {
					searchCentralVersions(gen, currentArtifact, seenVersions);
				} else {
					searchCentralArtifacts(gen, centralQuery);
				}
			} catch (Exception e) {
				runner.runOnRenderThread(() -> {
					if (searchGeneration.get() == gen) {
						searchingCentral = false;
						statusMessage = "Central failed: " + e.getMessage();
					}
				});
			}
		}, Duration.ZERO);
	}

	private void searchCentralArtifacts(long gen, String query) throws Exception {
		int found = 0;
		List<Artifact> centralResults = new ArrayList<>();
		ArtifactSearch.SearchResult searchResult = centralClient.findArtifacts(query, 200);
		while (searchResult != null) {
			for (Artifact a : searchResult.artifacts) {
				centralResults.add(a);
				found++;
			}
			searchResult = found < 200 ? centralClient.findNextArtifacts(searchResult) : null;
		}

		final int totalFound = found;
		final List<Artifact> finalResults = centralResults;
		runner.runOnRenderThread(() -> {
			if (searchGeneration.get() != gen) {
				return; // stale
			}
			for (Artifact a : finalResults) {
				localArtifacts.add(a);
			}
			refreshArtifactMatches();
			searchingCentral = false;
			statusMessage = "Central: added " + totalFound + " artifacts";
		});
	}

	private void searchCentralVersions(long gen, Artifact artifact, Set<String> seen) throws Exception {
		String versionQuery = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":";
		List<Artifact> newVersions = new ArrayList<>();
		int found = 0;
		ArtifactSearch.SearchResult searchResult = centralClient.findArtifacts(versionQuery, 200);
		while (searchResult != null) {
			for (Artifact a : searchResult.artifacts) {
				if (seen.add(a.getVersion())) {
					newVersions.add(a);
					found++;
				}
			}
			searchResult = found < 200 ? centralClient.findNextArtifacts(searchResult) : null;
		}

		final List<Artifact> finalNewVersions = newVersions;
		runner.runOnRenderThread(() -> {
			if (searchGeneration.get() != gen) {
				return; // stale
			}
			List<Artifact> merged = new ArrayList<>(versionList);
			merged.addAll(finalNewVersions);
			sortVersionsDesc(merged);
			versionList = merged;
			searchingCentral = false;
			statusMessage = "Found " + versionList.size() + " versions";
		});
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static void sortVersionsDesc(List<Artifact> versions) {
		org.eclipse.aether.version.VersionScheme scheme = new org.eclipse.aether.util.version.GenericVersionScheme();
		versions.sort((a, b) -> {
			try {
				return scheme.parseVersion(b.getVersion()).compareTo(scheme.parseVersion(a.getVersion()));
			} catch (Exception e) {
				return b.getVersion().compareTo(a.getVersion());
			}
		});
	}
}
