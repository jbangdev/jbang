package dev.jbang.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;

import dev.jbang.source.BuildContext;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.util.DigestUtil;
import dev.jbang.util.LockFileUtil;

@CommandDefinition(name = "lock", description = "Generate or refresh lock entries for script references", generateHelp = true, helpGroup = "Essentials")
public class Lock extends BaseBuildCommand {

	@Option(name = "lock-file", description = "Path to lock file (default: .jbang.lock)")
	Path lockFile;

	@Option(name = "algorithm", description = "Digest algorithm (default: sha256)", defaultValue = "sha256")
	String algorithm;

	@Override
	public Integer doCall() throws IOException {
		scriptMixin.validate(true);
		String ref = scriptMixin.scriptOrFile;
		DigestUtil.RefWithChecksum parsed = DigestUtil.splitRefAndChecksum(ref);
		ref = parsed.ref;

		ProjectBuilder pb = createBaseProjectBuilder();
		Project prj = pb.build(ref);

		String digest = DigestUtil.digestResource(prj, algorithm);
		Path effectiveLockFile = DigestUtil.resolveLockFile(lockFile, ref);
		List<String> sources = prj.getMainSourceSet()
			.getSources()
			.stream()
			.map(s -> s.getOriginalResource() == null ? "" : s.getOriginalResource())
			.collect(Collectors.toList());
		BuildContext bctx = BuildContext.forProject(prj);
		List<String> deps = bctx.resolveClassPath()
			.getArtifacts()
			.stream()
			.map(a -> a.getCoordinate() == null ? "" : a.getCoordinate().toCanonicalForm())
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toList());
		Map<String, String> depDigests = new LinkedHashMap<>();
		bctx.resolveClassPath().getArtifacts().forEach(a -> {
			if (a.getCoordinate() != null) {
				depDigests.put(a.getCoordinate().toCanonicalForm(),
						DigestUtil.digestPath(a.getFile(), "sha256"));
			}
		});
		LockFileUtil.write(effectiveLockFile, ref, digest, sources, deps, depDigests);
		info("Locked " + ref + " => " + digest + " in " + effectiveLockFile);
		return EXIT_OK;
	}
}
