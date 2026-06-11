package dev.jbang.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.checksums.TrustedChecksumsSource;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmHelper;

import dev.jbang.dependencies.ArtifactInfo;
import dev.jbang.dependencies.JBangTrustedChecksumsSource;
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
		List<ArtifactInfo> artifacts = bctx.resolveClassPath().getArtifacts();
		List<String> deps = artifacts.stream()
			.map(a -> a.getCoordinate() == null ? ""
					: a.getCoordinate().toCanonicalForm())
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toList());

		// Write script digest, sources, and dep coordinates
		LockFileUtil.write(effectiveLockFile, ref, digest, sources, deps, null);

		// Record per-artifact checksums via TrustedChecksumsSource.Writer
		JBangTrustedChecksumsSource tcSource = new JBangTrustedChecksumsSource(effectiveLockFile, ref);
		TrustedChecksumsSource.Writer writer = tcSource.getTrustedArtifactChecksumsWriter(null);
		List<ChecksumAlgorithmFactory> checksumFactories = DigestUtil.getChecksumFactories(
				JBangTrustedChecksumsSource.algorithmNameToResolverName(algorithm));
		LocalRepository localRepo = new LocalRepository("local");

		for (ArtifactInfo ai : artifacts) {
			if (ai.getCoordinate() != null && ai.getFile() != null) {
				Artifact artifact = new DefaultArtifact(
						ai.getCoordinate().getGroupId(),
						ai.getCoordinate().getArtifactId(),
						ai.getCoordinate().getClassifier(),
						ai.getCoordinate().getType(),
						ai.getCoordinate().getVersion())
					.setFile(ai.getFile().toFile());

				Map<String, String> checksums = ChecksumAlgorithmHelper
					.calculate(ai.getFile().toFile(), checksumFactories);
				writer.addTrustedArtifactChecksums(artifact, localRepo, checksumFactories, checksums);
			}
		}

		info("Locked " + ref + " => " + digest + " in " + effectiveLockFile);
		return EXIT_OK;
	}
}
