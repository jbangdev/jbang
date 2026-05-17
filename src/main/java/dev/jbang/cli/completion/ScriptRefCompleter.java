package dev.jbang.cli.completion;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;

import dev.jbang.catalog.Catalog;
import dev.jbang.source.Source;
import dev.jbang.util.Util;

/**
 * Provides tab-completion for the {@code scriptOrFile} argument used by
 * commands such as {@code run}, {@code build}, {@code edit}, etc.
 * <p>
 * Completes:
 * <ul>
 * <li>Local files (directories + JBang-supported extensions)</li>
 * <li>Alias names from the merged catalog</li>
 * </ul>
 */
public class ScriptRefCompleter implements OptionCompleter<CompleterInvocation> {

	/** Extensions recognised by JBang (derived from {@link Source.Type}). */
	private static final Set<String> SCRIPT_EXTENSIONS;

	static {
		SCRIPT_EXTENSIONS = new HashSet<>(Source.Type.extensions());
		// Also accept files without extension (scripts with shebangs)
	}

	@Override
	public void complete(CompleterInvocation inv) {
		String partial = inv.getGivenCompleteValue();
		if (partial == null) {
			partial = "";
		}

		Set<String> candidates = new TreeSet<>(); // sorted for stable output

		completeFiles(candidates, partial);
		completeAliases(candidates, partial);

		inv.addAllCompleterValues(candidates);
		if (candidates.size() == 1) {
			String single = candidates.iterator().next();
			// Don't append a space after a directory — the user will keep navigating
			if (single.endsWith("/") || single.endsWith("\\")) {
				inv.setAppendSpace(false);
			}
		}
	}

	// ---- File completion -----------------------------------------------

	private void completeFiles(Set<String> candidates, String partial) {
		Path cwd = Util.getCwd();
		Path base;
		String prefix;

		int lastSep = Math.max(partial.lastIndexOf('/'), partial.lastIndexOf('\\'));
		if (lastSep >= 0) {
			prefix = partial.substring(0, lastSep + 1);
			base = cwd.resolve(prefix).normalize();
		} else {
			prefix = "";
			base = cwd;
		}

		if (!Files.isDirectory(base)) {
			return;
		}

		String namePrefix = partial.substring(prefix.length());

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(base)) {
			for (Path entry : stream) {
				String name = entry.getFileName().toString();
				if (name.startsWith(".")) {
					continue; // skip hidden files
				}
				if (!name.toLowerCase().startsWith(namePrefix.toLowerCase())) {
					continue;
				}
				if (Files.isDirectory(entry)) {
					candidates.add(prefix + name + "/");
				} else if (isScriptFile(name)) {
					candidates.add(prefix + name);
				}
			}
		} catch (IOException e) {
			// best-effort — ignore
		}
	}

	private boolean isScriptFile(String name) {
		int dot = name.lastIndexOf('.');
		if (dot < 0) {
			return false; // skip extension-less files in listings
		}
		String ext = name.substring(dot + 1).toLowerCase();
		return SCRIPT_EXTENSIONS.contains(ext);
	}

	// ---- Alias completion ----------------------------------------------

	private void completeAliases(Set<String> candidates, String partial) {
		try {
			Catalog merged = Catalog.getMerged(true, true);
			for (String name : merged.aliases.keySet()) {
				if (name.startsWith(partial)) {
					candidates.add(name);
				}
			}
		} catch (Exception e) {
			// best-effort — catalog may be unavailable
		}
	}
}
