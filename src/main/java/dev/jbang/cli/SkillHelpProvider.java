package dev.jbang.cli;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.aesh.command.HelpEntry;
import org.aesh.command.HelpSectionProvider;

/**
 * Extra explanatory help for the {@code jbang skill} command, describing what
 * it does and pointing at alternative skill installers.
 */
public class SkillHelpProvider implements HelpSectionProvider {

	@Override
	public String getHeader() {
		return "\n"
				+ "Install JBang's agent skills - instructions that teach AI coding agents\n"
				+ "(Claude Code, Codex, OpenCode, Pi, ...) how to install and use JBang.\n"
				+ "\n"
				+ "Skills are fetched from https://github.com/jbangdev/agent-skills and copied\n"
				+ "into each detected agent's skills directory:\n"
				+ "    ~/.claude/skills/<skill>/     (Claude Code)\n"
				+ "    ~/.agents/skills/<skill>/     (Codex / OpenCode / Pi / ...)\n"
				+ "Use --project to install into ./.claude and ./.agents in the current repository.\n"
				+ "\n"
				+ "Installs are safe to repeat: an up-to-date skill is left alone, a locally\n"
				+ "modified one is only replaced with --force, and each skill JBang installs is\n"
				+ "marked so 'uninstall' removes only what JBang put there and never a foreign skill.\n"
				+ "\n"
				+ "    jbang skill install      install the skills into detected agents\n"
				+ "    jbang skill list         show, per agent, which skills are installed\n"
				+ "    jbang skill uninstall    remove the skills JBang installed\n";
	}

	@Override
	public String getFooter() {
		return "\n"
				+ "Alternatively, install the same skills with a generic skills installer, e.g.:\n"
				+ "    npx skills add jbangdev/agent-skills\n";
	}

	@Override
	public Map<String, List<HelpEntry>> getAdditionalSections() {
		return Collections.emptyMap();
	}
}
