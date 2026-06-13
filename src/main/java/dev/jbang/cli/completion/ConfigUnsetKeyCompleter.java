package dev.jbang.cli.completion;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;

import dev.jbang.Configuration;

/**
 * Provides tab-completion for the {@code key} argument of
 * {@code jbang config unset}. Only offers keys that are currently set in the
 * merged configuration, since unsetting a non-existent key is an error.
 * <p>
 * When the merged configuration has origin information, descriptions show the
 * source file where each key is defined.
 */
public class ConfigUnsetKeyCompleter implements OptionCompleter<CompleterInvocation> {

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
		Map<String, String> availableKeys = ConfigKeyCompleter.getAvailableKeys();

		try {
			Configuration cfg = Configuration.getMerged();

			// First pass: prefix match
			collectSetKeys(candidates, cfg, availableKeys, partial, true);

			// Second pass: segment match fallback when prefix found nothing
			if (candidates.isEmpty() && !partial.isEmpty()) {
				collectSetKeys(candidates, cfg, availableKeys, partial, false);
				if (!candidates.isEmpty()) {
					inv.setIgnoreStartsWith(true);
					inv.setOffset(0);
				}
			}
		} catch (Exception e) {
			// best-effort
		}

		inv.addAllCompleterValues(candidates);

		if (candidates.size() == 1) {
			String single = candidates.iterator().next();
			int tab = single.indexOf('\t');
			if (tab >= 0) {
				inv.clearCompleterValues();
				inv.addCompleterValue(single.substring(0, tab));
			}
		}
	}

	private void collectSetKeys(Set<String> candidates, Configuration cfg,
			Map<String, String> availableKeys, String partial, boolean prefixOnly) {
		Configuration current = cfg;
		while (current != null) {
			String origin = null;
			if (current.getStoreRef() != null) {
				origin = current.getStoreRef().getOriginalResource();
			}
			for (String key : current.keySet()) {
				boolean matches = prefixOnly
						? key.startsWith(partial)
						: ConfigKeyCompleter.matchesSegment(key, partial);
				if (matches) {
					String desc;
					if (origin != null) {
						desc = origin;
					} else if (availableKeys.containsKey(key)) {
						desc = availableKeys.get(key);
					} else {
						desc = "Set";
					}
					String prefix = key + "\t";
					boolean alreadyPresent = candidates.stream().anyMatch(c -> c.startsWith(prefix));
					if (!alreadyPresent) {
						candidates.add(described(key, desc));
					}
				}
			}
			current = current.getFallback();
		}
	}
}
