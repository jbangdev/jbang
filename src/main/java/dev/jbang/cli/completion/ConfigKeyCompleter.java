package dev.jbang.cli.completion;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;

import dev.jbang.Configuration;
import dev.jbang.cli.AvailableOption;
import dev.jbang.cli.Config;

/**
 * Provides tab-completion for the {@code key} argument of {@code jbang config}
 * subcommands ({@code get}, {@code set}).
 * <p>
 * Completes from two sources:
 * <ul>
 * <li>Available option keys gathered from the CLI command tree (same as
 * {@code config list --show-available})</li>
 * <li>User-set keys from the merged configuration that don't match a known
 * option name</li>
 * </ul>
 * Candidates include tab-separated descriptions for fish/zsh display.
 */
public class ConfigKeyCompleter implements OptionCompleter<CompleterInvocation> {

	private static String described(String candidate, String description) {
		return candidate + "\t" + description;
	}

	@Override
	public void complete(CompleterInvocation inv) {
		String partial = inv.getGivenCompleteValue();
		if (partial == null) {
			partial = "";
		}

		Set<String> candidates = new TreeSet<>();
		Map<String, String> availableKeys = getAvailableKeys();

		// First pass: prefix match (standard completion behaviour)
		for (Map.Entry<String, String> entry : availableKeys.entrySet()) {
			String key = entry.getKey();
			String desc = entry.getValue();
			if (key.startsWith(partial)) {
				candidates.add(described(key, desc));
			}
		}

		// Also complete keys from the merged configuration (user-set values
		// that might not match known option names)
		try {
			Configuration cfg = Configuration.getMerged();
			for (String key : cfg.flatten().keySet()) {
				if (key.startsWith(partial)) {
					boolean alreadyPresent = availableKeys.containsKey(key);
					if (!alreadyPresent) {
						String val = cfg.get(key);
						candidates.add(described(key, val != null ? "= " + val : ""));
					}
				}
			}
		} catch (Exception e) {
			// best-effort
		}

		// Second pass: if prefix match found nothing and the partial is
		// non-empty, do a segment match. "debu" matches "run.debug",
		// "add." matches "alias.add.*", "catalog.add.*", etc.
		if (candidates.isEmpty() && !partial.isEmpty()) {
			for (Map.Entry<String, String> entry : availableKeys.entrySet()) {
				String key = entry.getKey();
				if (matchesSegment(key, partial)) {
					candidates.add(described(key, entry.getValue()));
				}
			}
			// Also check user-defined keys
			try {
				Configuration cfg = Configuration.getMerged();
				for (String key : cfg.flatten().keySet()) {
					if (!availableKeys.containsKey(key) && matchesSegment(key, partial)) {
						String val = cfg.get(key);
						candidates.add(described(key, val != null ? "= " + val : ""));
					}
				}
			} catch (Exception e) {
				// best-effort
			}
			// Tell aesh to replace the whole token rather than append,
			// since the candidates don't share the typed prefix.
			if (!candidates.isEmpty()) {
				inv.setIgnoreStartsWith(true);
				inv.setOffset(0);
			}
		}

		inv.addAllCompleterValues(candidates);

		// When there is exactly one candidate, strip the description so the
		// value is clean (aesh escapes spaces in single-candidate mode).
		if (candidates.size() == 1) {
			String single = candidates.iterator().next();
			int tab = single.indexOf('\t');
			if (tab >= 0) {
				inv.clearCompleterValues();
				inv.addCompleterValue(single.substring(0, tab));
			}
		}
	}

	/**
	 * Returns true if the partial matches any segment boundary in the key.
	 * <p>
	 * Examples:
	 * <ul>
	 * <li>{@code matchesSegment("run.debug", "debu")} → true (segment "debug"
	 * starts with "debu")</li>
	 * <li>{@code matchesSegment("alias.add.debug", "add.")} → true ("add.debug"
	 * starts with "add.")</li>
	 * <li>{@code matchesSegment("alias.add.debug", "add.deb")} → true</li>
	 * <li>{@code matchesSegment("run.debug", "xyz")} → false</li>
	 * </ul>
	 */
	static boolean matchesSegment(String key, String partial) {
		// Try matching at each dot boundary
		int idx = 0;
		while (true) {
			int dot = key.indexOf('.', idx);
			if (dot < 0) {
				break;
			}
			String suffix = key.substring(dot + 1);
			if (suffix.startsWith(partial)) {
				return true;
			}
			idx = dot + 1;
		}
		// Also try contains for multi-segment partials like "add.debu"
		// that might not align exactly to a segment start
		return key.contains("." + partial);
	}

	/**
	 * Gathers all known configuration keys with descriptions, using the same source
	 * as {@code config list --show-available}.
	 *
	 * @return map of key → description
	 */
	static Map<String, String> getAvailableKeys() {
		Map<String, String> keys = new TreeMap<>();
		for (AvailableOption opt : Config.getAvailableOptions()) {
			keys.put(opt.key, opt.description);
		}
		return keys;
	}
}
