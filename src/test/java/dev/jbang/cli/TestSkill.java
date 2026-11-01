package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.BaseTest;
import dev.jbang.util.UnpackUtil;

public class TestSkill extends BaseTest {

	/**
	 * Build a GitHub-style source tarball: one root folder, two skill folders + a
	 * stray file.
	 */
	private Path fixtureTarball(Path dir) throws IOException {
		Path tgz = dir.resolve("agent-skills.tar.gz");
		try (TarArchiveOutputStream out = new TarArchiveOutputStream(
				new GzipCompressorOutputStream(Files.newOutputStream(tgz)))) {
			addFile(out, "repo-main/README.md", "root readme");
			addFile(out, "repo-main/skilla/SKILL.md", "skill a");
			addFile(out, "repo-main/skillb/SKILL.md", "skill b");
			addFile(out, "repo-main/skillb/references/x.md", "ref x");
		}
		return tgz;
	}

	private void addFile(TarArchiveOutputStream out, String name, String content) throws IOException {
		byte[] data = content.getBytes(StandardCharsets.UTF_8);
		TarArchiveEntry entry = new TarArchiveEntry(name);
		entry.setSize(data.length);
		out.putArchiveEntry(entry);
		out.write(data);
		out.closeArchiveEntry();
	}

	/**
	 * Simulate a detected agent by creating its parent directory under the (temp)
	 * user home.
	 */
	private void detectAgents() throws IOException {
		Path home = Path.of(System.getProperty("user.home"));
		Files.createDirectories(home.resolve(".claude"));
		Files.createDirectories(home.resolve(".agents"));
	}

	@Test
	void listTopFoldersIgnoresRootAndStrayFiles(@TempDir Path dir) throws IOException {
		Set<String> folders = UnpackUtil.listTopFolders(fixtureTarball(dir), true);
		assertThat(folders, containsInAnyOrder("skilla", "skillb"));
	}

	@Test
	void repoTarballUrlResolvesShorthandsAndUrls() {
		assertEquals("https://github.com/jbangdev/agent-skills/archive/main.tar.gz", Skill.repoTarballUrl(null));
		assertEquals("https://github.com/acme/skills/archive/main.tar.gz", Skill.repoTarballUrl("acme/skills"));
		assertEquals("https://github.com/acme/skills/archive/dev.tar.gz", Skill.repoTarballUrl("acme/skills@dev"));
		assertEquals("https://example.com/my.tar.gz", Skill.repoTarballUrl("https://example.com/my.tar.gz"));
	}

	@Test
	void readEntryReturnsFileBytes(@TempDir Path dir) throws IOException {
		byte[] md = UnpackUtil.readEntry(fixtureTarball(dir), true, Path.of("skillb", "SKILL.md"));
		assertEquals("skill b", new String(md, StandardCharsets.UTF_8));
	}

	@Test
	void installAndUninstallAllSkills(@TempDir Path dir) throws IOException {
		detectAgents();
		Path tgz = fixtureTarball(dir);
		Path home = Path.of(System.getProperty("user.home")); // BaseTest points this at a temp dir

		int rc = Skill.installFromTarball(tgz, null, false, false, null, "test/repo");
		assertEquals(0, rc);

		Path claude = home.resolve(".claude").resolve("skills");
		Path agents = home.resolve(".agents").resolve("skills");
		for (Path base : Arrays.asList(claude, agents)) {
			assertThat(Files.isRegularFile(base.resolve("skilla").resolve("SKILL.md")), is(true));
			assertThat(Files.isRegularFile(base.resolve("skillb").resolve("SKILL.md")), is(true));
			assertThat(Files.isRegularFile(base.resolve("skillb").resolve("references").resolve("x.md")), is(true));
		}

		// status is CURRENT right after install
		assertEquals(Skill.Status.CURRENT, Skill.status(tgz, claude, "skilla", false));

		// marker was written by install
		assertThat(Files.isRegularFile(claude.resolve("skilla").resolve(".jbang-skill")), is(true));

		Skill.uninstall(Arrays.asList("skilla", "skillb"), false, null);
		assertThat(Files.exists(claude.resolve("skilla")), is(false));
		assertThat(Files.exists(agents.resolve("skillb")), is(false));
	}

	@Test
	void uninstallAllUsesMarkerAndSkipsForeign(@TempDir Path dir) throws IOException {
		detectAgents();
		Path tgz = fixtureTarball(dir);
		Path claude = Path.of(System.getProperty("user.home")).resolve(".claude").resolve("skills");

		Skill.installFromTarball(tgz, null, false, false, null, "test/repo");
		// a foreign skill of a different name, not installed by JBang (no marker)
		Path foreign = claude.resolve("foreign");
		Files.createDirectories(foreign);
		Files.writeString(foreign.resolve("SKILL.md"), "someone else");

		// uninstall with no names removes only marker-bearing dirs
		Skill.uninstall(null, false, null);
		assertThat(Files.exists(claude.resolve("skilla")), is(false));
		assertThat(Files.exists(claude.resolve("skillb")), is(false));
		assertThat(Files.isRegularFile(foreign.resolve("SKILL.md")), is(true));
	}

	@Test
	void agentOptionLimitsToOneTarget(@TempDir Path dir) throws IOException {
		detectAgents();
		Path tgz = fixtureTarball(dir);
		Path home = Path.of(System.getProperty("user.home"));

		Skill.installFromTarball(tgz, null, false, false, Arrays.asList("claude"), "test/repo");
		assertThat(Files.isRegularFile(home.resolve(".claude")
			.resolve("skills")
			.resolve("skilla")
			.resolve("SKILL.md")), is(true));
		// the agents target was not selected, so it stays empty
		assertThat(Files.exists(home.resolve(".agents").resolve("skills").resolve("skilla")), is(false));
	}

	@Test
	void installWithoutDetectedAgentsFails(@TempDir Path dir) throws IOException {
		// no ~/.claude or ~/.agents in the temp home -> nothing detected
		int rc = Skill.installFromTarball(fixtureTarball(dir), null, false, false, null, "test/repo");
		assertEquals(dev.jbang.ExitException.EXIT_GENERIC_ERROR, rc);
	}

	@Test
	void modifiedSkillNeedsForce(@TempDir Path dir) throws IOException {
		detectAgents();
		Path tgz = fixtureTarball(dir);
		Path home = Path.of(System.getProperty("user.home"));
		Path claude = home.resolve(".claude").resolve("skills");

		Skill.installFromTarball(tgz, null, false, false, null, "test/repo");
		// user edits the installed skill
		Files.writeString(claude.resolve("skilla").resolve("SKILL.md"), "edited");
		assertEquals(Skill.Status.MODIFIED, Skill.status(tgz, claude, "skilla", false));

		// without --force the edit survives
		Skill.installFromTarball(tgz, Arrays.asList("skilla"), false, false, null, "test/repo");
		assertEquals("edited", Files.readString(claude.resolve("skilla").resolve("SKILL.md")));

		// with --force it is restored
		Skill.installFromTarball(tgz, Arrays.asList("skilla"), true, false, null, "test/repo");
		assertEquals(Skill.Status.CURRENT, Skill.status(tgz, claude, "skilla", false));
	}
}
