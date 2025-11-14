package dev.jbang.search;

import static dev.jbang.cli.BaseCommand.EXIT_UNEXPECTED_STATE;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.Terminal.Signal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp.Capability;

import dev.jbang.dependencies.ArtifactResolver;

/**
 * A widget for searching artifacts. Initially from veles but changed to be Java
 * 8 compatiable and added additional features. Should/will move to jline4
 * alternative which/when available.
 */
public class ArtifactSearchWidget {
	private final Terminal terminal;
	private final Attributes attrs;
	private final Set<Artifact> artifactsToConsider;
	private final ArtifactSearch mavenCentralClient = SolrArtifactSearch.createCsc();

	public ArtifactSearchWidget(Terminal terminal) {
		this.terminal = terminal;
		this.attrs = terminal.getAttributes();
		this.artifactsToConsider = localMavenArtifacts()
			.stream()
			.filter(p -> !p.getArtifactId().contains("-parent"))
			.collect(Collectors.toSet());
	}

	private static Set<Artifact> localMavenArtifacts() {
		Set<Artifact> packages = new HashSet<Artifact>();
		try {
			Path localMaven = ArtifactResolver.getLocalMavenRepo();
			if (!Files.exists(localMaven)) {
				return new HashSet<Artifact>();
			}
			Files.walkFileTree(localMaven, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
						throws IOException {
					if (Character.isDigit(dir.getFileName().toString().charAt(0))) {
						Path artifactDir = dir.getParent();
						String artifact = artifactDir.getFileName().toString();
						String group = localMaven.relativize(artifactDir.getParent())
							.toString()
							.replace(FileSystems.getDefault().getSeparator(), ".");
						packages.add(new DefaultArtifact(group, artifact, "", dir.getFileName().toString()));
						return FileVisitResult.SKIP_SUBTREE;
					}

					return FileVisitResult.CONTINUE;
				}
			});

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return packages;
	}

	void cleanup() {
		terminal.setAttributes(attrs);
		terminal.puts(Capability.clear_screen);
		terminal.flush();
	}

	public Artifact search() {
		terminal.enterRawMode();

		terminal.handle(Signal.INT, (e) -> {
			cleanup();
			System.exit(EXIT_UNEXPECTED_STATE);
		});

		Combobox<Fuzz.SearchFuzzedResult<Artifact>> artifactCombobox = new Combobox<>(terminal);
		artifactCombobox.filterCompletions(this::fuzzySearchArtifacts);
		artifactCombobox.renderItem(r -> new AttributedString(
				String.format(r.highlightTarget()),
				AttributedStyle.DEFAULT));
		String searchPrefix = "Search (TAB to search central): ";
		artifactCombobox.withPrefix(searchPrefix);
		artifactCombobox.handle("search_remote", "\t", (g) -> {
			try {
				String query = g.query().toString();
				artifactCombobox.withPrefix("Searching Maven Central....: ");
				artifactCombobox.render();

				int max = 200;
				ArtifactSearch.SearchResult result = mavenCentralClient.findArtifacts(query, max);
				while (result != null) {
					for (Artifact artifact : result.artifacts) {
						artifactsToConsider.add(artifact);
					}
					result = mavenCentralClient.findNextArtifacts(result);
				}

				artifactCombobox.withPrefix(searchPrefix);
				List<Combobox.ComboboxAction> actions = new ArrayList<>();
				actions.add(new Combobox.UpdateCompletions());
				return actions;
			} catch (Exception e) {
				artifactCombobox.withPrefix("Failed - " + e.getMessage() + ": ");
				return Collections.<Combobox.ComboboxAction>emptyList();
			}
		});

		Combobox.ComboboxResult<Fuzz.SearchFuzzedResult<Artifact>> artifactGav = artifactCombobox.prompt();

		cleanup();

		return artifactGav.selection().item();
	}

	private List<Fuzz.SearchFuzzedResult<Artifact>> fuzzySearchArtifacts(String query) {
		return Fuzz.search(artifactsToConsider, (gav) -> {
			String m = gav.getGroupId() + ":" + gav.getArtifactId() + ":" + gav.getVersion();
			SearchScorer scorer = SearchScorer.calculate(query, m);
			return new Fuzz.SearchFuzzedResult<>(gav, scorer, query.length(), m.length());
		});
	}
}