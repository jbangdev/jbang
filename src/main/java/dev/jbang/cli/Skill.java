package dev.jbang.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;

import dev.jbang.ExitException;
import dev.jbang.util.NetUtil;
import dev.jbang.util.UnpackUtil;
import dev.jbang.util.Util;

@CommandDefinition(name = "skill", description = "Install JBang agent skills (AI coding agent instructions).", groupCommands = {
		Skill.SkillInstall.class, Skill.SkillUninstall.class,
		Skill.SkillList.class }, generateHelp = true, helpGroup = "Configuration", helpSectionProvider = SkillHelpProvider.class)
public class Skill extends BaseCommand {

	static final String DEFAULT_REPO = "jbangdev/agent-skills";
	static final String DEFAULT_REF = "main";

	/** Provenance marker JBang writes into every skill it installs. */
	static final String MARKER = ".jbang-skill";

	/**
	 * The distinct skill locations we manage. Codex/OpenCode/Pi share a single
	 * directory, so we expose two targets, not one per tool. Add per-tool entries
	 * here if/when an agent needs its own location or settings.
	 */
	enum Agent {
		CLAUDE("claude", "Claude Code", ".claude"),
		AGENTS("agents", "Agents (Codex/OpenCode/Pi/...)", ".agents");

		final String token;
		final String label;
		final String dir;

		Agent(String token, String label, String dir) {
			this.token = token;
			this.label = label;
			this.dir = dir;
		}

		static Agent fromToken(String token) {
			for (Agent a : values()) {
				if (a.token.equalsIgnoreCase(token)) {
					return a;
				}
			}
			throw new ExitException(ExitException.EXIT_INVALID_INPUT,
					"Unknown agent '" + token + "'. Available: claude, agents");
		}
	}

	/** Status of one skill in one agent location. */
	enum Status {
		CURRENT("installed (up to date)"),
		MODIFIED("installed (modified/outdated)"),
		CONFLICT("conflict (not a JBang skill)"),
		MISSING("not installed"),
		AGENT_NOT_DETECTED("agent not detected");

		final String label;

		Status(String label) {
			this.label = label;
		}
	}

	@Override
	public Integer doCall() throws IOException {
		return missingSubcommand();
	}

	/**
	 * Agent skill directories we install into, keyed by a human label. With
	 * {@code project} the repo-local dirs under the current directory are used,
	 * otherwise the per-account dirs under the user home.
	 */
	static Map<String, Path> agentSkillDirs(boolean project, List<String> selected) {
		Path base = project ? Paths.get("").toAbsolutePath() : Paths.get(System.getProperty("user.home"));
		List<Agent> agents = new ArrayList<>();
		if (selected == null || selected.isEmpty()) {
			agents.addAll(Arrays.asList(Agent.values()));
		} else {
			for (String token : selected) {
				Agent a = Agent.fromToken(token);
				if (!agents.contains(a)) {
					agents.add(a);
				}
			}
		}
		Map<String, Path> dirs = new LinkedHashMap<>();
		for (Agent a : agents) {
			dirs.put(a.label, base.resolve(a.dir).resolve("skills"));
		}
		return dirs;
	}

	/** Render a path for display, abbreviating the user home as {@code ~}. */
	static String display(Path path) {
		String home = System.getProperty("user.home");
		String s = path.toString();
		if (home != null && !home.isEmpty() && s.startsWith(home)) {
			return "~" + s.substring(home.length());
		}
		return s;
	}

	/**
	 * Resolve a {@code --repo} value to a source-tarball URL. A value containing
	 * {@code ://} is used verbatim; otherwise it is a GitHub {@code owner/repo}
	 * shorthand with an optional {@code @ref} (branch, tag or commit).
	 */
	static String repoTarballUrl(String repo) {
		String spec = (repo == null || repo.isEmpty()) ? DEFAULT_REPO : repo;
		if (spec.contains("://")) {
			return spec;
		}
		String ref = DEFAULT_REF;
		int at = spec.indexOf('@');
		if (at >= 0) {
			ref = spec.substring(at + 1);
			spec = spec.substring(0, at);
		}
		return "https://github.com/" + spec + "/archive/" + ref + ".tar.gz";
	}

	/** Download the skills repo tarball (cached) and return its local path. */
	static Path fetchTarball(String repo) throws IOException {
		return NetUtil.downloadAndCacheFile(repoTarballUrl(repo));
	}

	/** Determine the status of skill {@code name} in the given agent skills dir. */
	static Status status(Path tarball, Path skillsDir, String name, boolean project) throws IOException {
		// per-account installs target only agents already present; a project scope
		// explicitly opts this repo in, so every agent is a valid target there.
		if (!project && !Files.isDirectory(skillsDir.getParent())) {
			return Status.AGENT_NOT_DETECTED;
		}
		Path target = resolveInside(skillsDir, name);
		if (!Files.exists(target)) {
			return Status.MISSING;
		}
		Path installedMd = target.resolve("SKILL.md");
		if (!Files.isRegularFile(installedMd)) {
			return Status.CONFLICT;
		}
		byte[] repoMd = UnpackUtil.readEntry(tarball, true, Paths.get(name, "SKILL.md"));
		if (repoMd != null && Arrays.equals(repoMd, Files.readAllBytes(installedMd))) {
			return Status.CURRENT;
		}
		return Status.MODIFIED;
	}

	static abstract class BaseSkillCommand extends BaseCommand {
		@Arguments(paramLabel = "skills", description = "Skill name(s); defaults to all skills in the repo")
		List<String> names;

		@Option(name = "project", hasValue = false, description = "Target the current project/repository (./.claude, ./.agents) instead of your user home.")
		boolean project;

		@OptionList(name = "agent", valueSeparator = ',', description = "Limit to agent(s): claude, agents (repeatable or comma-separated).")
		List<String> agentTokens;
	}

	@CommandDefinition(name = "install", description = "Install JBang agent skill(s) into detected agents.", generateHelp = true)
	public static class SkillInstall extends BaseSkillCommand {

		@Option(name = "force", hasValue = false, description = "Overwrite a skill that was modified or is not a JBang skill.")
		boolean force;

		@Option(name = "repo", description = "Source of the skills: a GitHub owner/repo[@ref] or a full tarball URL (default jbangdev/agent-skills).")
		String repo;

		@Override
		public Integer doCall() throws IOException {
			return installFromTarball(fetchTarball(repo), names, force, project, agentTokens,
					repo == null || repo.isEmpty() ? DEFAULT_REPO : repo);
		}
	}

	static int installFromTarball(Path tarball, List<String> names, boolean force, boolean project,
			List<String> agentTokens, String source) throws IOException {
		Set<String> available = UnpackUtil.listTopFolders(tarball, true);
		List<String> wanted = resolveNames(names, available);
		Map<String, Path> agents = agentSkillDirs(project, agentTokens);

		boolean detectedAny = false;
		boolean installedAny = false;
		boolean needForce = false;
		for (Map.Entry<String, Path> agent : agents.entrySet()) {
			Path skillsDir = agent.getValue();
			// per-account: only touch agents already present; project: target all
			if (!project && !Files.isDirectory(skillsDir.getParent())) {
				Util.verboseMsg("Skipping " + agent.getKey() + " (agent not detected)");
				continue;
			}
			detectedAny = true;
			for (String name : wanted) {
				Path target = resolveInside(skillsDir, name);
				Status st = status(tarball, skillsDir, name, project);
				switch (st) {
				case CURRENT:
					Util.infoMsg("Up to date: '" + name + "' in " + agent.getKey());
					break;
				case MISSING:
					install(tarball, name, target, source);
					Util.infoMsg("Installed '" + name + "' into " + agent.getKey() + " (" + display(target) + ")");
					installedAny = true;
					break;
				case MODIFIED:
				case CONFLICT:
					if (force) {
						Util.deletePath(target, true);
						install(tarball, name, target, source);
						Util.infoMsg(
								"Reinstalled '" + name + "' into " + agent.getKey() + " (" + display(target) + ")");
						installedAny = true;
					} else {
						Util.warnMsg(st.label + ": '" + name + "' in " + agent.getKey()
								+ " - use --force to overwrite (" + display(target) + ")");
						needForce = true;
					}
					break;
				default:
					break;
				}
			}
		}
		if (!detectedAny) {
			Util.warnMsg("No supported agents detected. Launch an agent first, or use --project inside a repository.");
			return ExitException.EXIT_GENERIC_ERROR;
		}
		if (!installedAny && needForce) {
			return ExitException.EXIT_GENERIC_ERROR;
		}
		return ExitException.EXIT_OK;
	}

	static void install(Path tarball, String name, Path target, String source) throws IOException {
		Files.createDirectories(target);
		UnpackUtil.unpack(tarball, target, true, Paths.get(name));
		Files.write(target.resolve(MARKER), ("repo=" + source + System.lineSeparator()).getBytes());
	}

	@CommandDefinition(name = "uninstall", description = "Remove JBang agent skill(s) from agents.", generateHelp = true)
	public static class SkillUninstall extends BaseSkillCommand {

		@Override
		public Integer doCall() {
			return uninstall(names, project, agentTokens);
		}
	}

	static int uninstall(List<String> names, boolean project, List<String> agentTokens) {
		boolean any = false;
		for (Map.Entry<String, Path> agent : agentSkillDirs(project, agentTokens).entrySet()) {
			Path skillsDir = agent.getValue();
			// no names -> every JBang-installed skill we find (offline, marker-based)
			List<String> wanted = (names == null || names.isEmpty()) ? installedSkills(skillsDir) : names;
			for (String name : wanted) {
				Path target = resolveInside(skillsDir, name);
				if (!Files.isDirectory(target)) {
					continue;
				}
				// safety: only remove dirs JBang installed, never a foreign dir
				if (!Files.isRegularFile(target.resolve(MARKER))) {
					Util.warnMsg("Skipping '" + name + "' in " + agent.getKey()
							+ " - not a JBang-installed skill (" + display(target) + ")");
					continue;
				}
				Util.deletePath(target, true);
				Util.infoMsg("Removed '" + name + "' from " + agent.getKey() + " (" + display(target) + ")");
				any = true;
			}
		}
		if (!any) {
			Util.infoMsg("Nothing to remove.");
		}
		return ExitException.EXIT_OK;
	}

	/**
	 * Names of JBang-installed skills (those carrying the marker) in a skills dir.
	 */
	static List<String> installedSkills(Path skillsDir) {
		List<String> found = new ArrayList<>();
		if (!Files.isDirectory(skillsDir)) {
			return found;
		}
		try (Stream<Path> entries = Files.list(skillsDir)) {
			entries.filter(p -> Files.isRegularFile(p.resolve(MARKER)))
				.forEach(p -> found.add(p.getFileName().toString()));
		} catch (IOException e) {
			Util.verboseMsg("Could not list " + skillsDir + ": " + e.getMessage());
		}
		return found;
	}

	@CommandDefinition(name = "list", description = "List available skills and their status in each agent.", generateHelp = true)
	public static class SkillList extends BaseCommand {

		@Option(name = "project", hasValue = false, description = "Report on the current project/repository (./.claude, ./.agents) instead of your user home.")
		boolean project;

		@OptionList(name = "agent", valueSeparator = ',', description = "Limit to agent(s): claude, agents (repeatable or comma-separated).")
		List<String> agentTokens;

		@Option(name = "repo", description = "Source of the skills: a GitHub owner/repo[@ref] or a full tarball URL (default jbangdev/agent-skills).")
		String repo;

		@Override
		public Integer doCall() throws IOException {
			Path tarball = fetchTarball(repo);
			Set<String> available = UnpackUtil.listTopFolders(tarball, true);
			Map<String, Path> agents = agentSkillDirs(project, agentTokens);
			for (String name : new TreeSet<>(available)) {
				Util.infoMsg(name);
				for (Map.Entry<String, Path> agent : agents.entrySet()) {
					Path target = resolveInside(agent.getValue(), name);
					Status st = status(tarball, agent.getValue(), name, project);
					Util.infoMsg(String.format("  %-34s %-28s %s", agent.getKey(), st.label, display(target)));
				}
			}
			return ExitException.EXIT_OK;
		}
	}

	/**
	 * Resolves {@code name} against {@code base}, guaranteeing the result stays
	 * inside {@code base}. Protects against path traversal ("Zip Slip") when the
	 * name originates from an archive entry or user input.
	 */
	static Path resolveInside(Path base, String name) {
		Path target = base.resolve(name).normalize();
		if (!target.startsWith(base.normalize())) {
			throw new ExitException(ExitException.EXIT_INVALID_INPUT,
					"Invalid skill name '" + name + "': escapes the skills directory");
		}
		return target;
	}

	private static List<String> resolveNames(List<String> requested, Set<String> available) {
		if (requested == null || requested.isEmpty()) {
			return new ArrayList<>(available);
		}
		for (String name : requested) {
			if (!available.contains(name)) {
				throw new ExitException(ExitException.EXIT_INVALID_INPUT,
						"Unknown skill '" + name + "'. Available: " + String.join(", ", available));
			}
		}
		return requested;
	}
}
