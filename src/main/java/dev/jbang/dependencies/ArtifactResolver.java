package dev.jbang.dependencies;

import static dev.jbang.util.Util.infoMsg;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;

import dev.jbang.Settings;
import dev.jbang.cli.ExitException;
import dev.jbang.util.Util;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtimes;

public class ArtifactResolver implements Closeable {
	private final Context context;
	private final boolean downloadSources;

	public static class Builder {
		private List<MavenRepo> repositories;
		private int timeout;
		private boolean offline;
		private boolean ignoreTransitiveRepositories;
		private boolean withUserSettings;
		private Path localFolder;
		private Path settingsXml;
		private boolean updateCache;
		private boolean loggingEnabled;

		private boolean downloadSources = false;

		public static Builder create() {
			return new Builder();
		}

		private Builder() {
		}

		public Builder repositories(List<MavenRepo> repositories) {
			this.repositories = repositories;
			return this;
		}

		public Builder timeout(int timeout) {
			this.timeout = timeout;
			return this;
		}

		public Builder withUserSettings(boolean withUserSettings) {
			this.withUserSettings = withUserSettings;
			return this;
		}

		public Builder localFolder(Path localFolder) {
			this.localFolder = localFolder;
			return this;
		}

		public Builder settingsXml(Path settingsXml) {
			this.settingsXml = settingsXml;
			return this;
		}

		public Builder forceCacheUpdate(boolean updateCache) {
			this.updateCache = updateCache;
			return this;
		}

		public Builder offline(boolean offline) {
			this.offline = offline;
			return this;
		}

		public Builder ignoreTransitiveRepositories(boolean ignoreTransitiveRepositories) {
			this.ignoreTransitiveRepositories = ignoreTransitiveRepositories;
			return this;
		}

		public Builder logging(boolean logging) {
			this.loggingEnabled = logging;
			return this;
		}

		public Builder downloadSources(boolean downloadSources) {
			this.downloadSources = downloadSources;
			return this;
		}

		public ArtifactResolver build() {
			return new ArtifactResolver(this);
		}
	}

	private ArtifactResolver(Builder builder) {
		HashMap<String, String> userProperties = new HashMap<>();
		if (builder.timeout > 0) {
			userProperties.put(ConfigurationProperties.CONNECT_TIMEOUT, String.valueOf(builder.timeout));
		}

		// this is to avoid being blocked by some websites
		// because of the word "Java" in the User-Agent
		userProperties.put("aether.connector.userAgent", Util.getAgentString());

		final List<RemoteRepository> partialRepos; // always have reposes, at least Central if no user defined ones
		if (builder.repositories != null) {
			partialRepos = builder.repositories.stream().map(this::toRemoteRepo).collect(Collectors.toList());
		} else {
			partialRepos = Collections.singletonList(ContextOverrides.CENTRAL);
		}

		this.downloadSources = builder.downloadSources;
		final RepositoryListener listener = builder.loggingEnabled ? setupSessionLogging() : null;

		ContextOverrides.Builder overridesBuilder = ContextOverrides.create()
			.userProperties(userProperties)
			.offline(builder.offline)
			.ignoreArtifactDescriptorRepositories(
					builder.ignoreTransitiveRepositories)
			.withUserSettings(builder.withUserSettings)
			.withUserSettingsXmlOverride(builder.settingsXml)
			.withLocalRepositoryOverride(builder.localFolder)
			.repositories(partialRepos) // set reposes
										// explicitly, jbang
										// drives
			.addRepositoriesOp(
					ContextOverrides.AddRepositoriesOp.REPLACE) // ignore
																// settings.xml
																// reposes
			.snapshotUpdatePolicy(builder.updateCache
					? ContextOverrides.SnapshotUpdatePolicy.ALWAYS
					: null)
			.repositoryListener(listener);

		overridesBuilder.extraArtifactTypes(
				Collections.singletonList(new DefaultArtifactType("fatjar", "jar", null, "java", true, true)));

		this.context = Runtimes.INSTANCE.getRuntime().create(overridesBuilder.build());
	}

	@Override
	public void close() {
		context.close();
	}

	public void downloadSources(Artifact artifact) {
		try {
			context.repositorySystem()
				.resolveArtifact(context.repositorySystemSession(), new ArtifactRequest()
					.setArtifact(
							new SubArtifact(
									artifact,
									"sources",
									"jar"))
					.setRepositories(
							context.remoteRepositories()));
		} catch (ArtifactResolutionException e) {
			Util.verboseMsg("Could not resolve sources for " + artifact.toString());
		}
	}

	public List<ArtifactInfo> resolve(List<String> depIds) {
		context.repositorySystemSession().getData().set("depIds", depIds);
		// Maven is by default "forgiving" for dependency POM loading: here we want to
		// ensure that all enlisted deps exists for sure
		DefaultRepositorySystemSession strictSession = new DefaultRepositorySystemSession(
				context.repositorySystemSession());

		strictSession.setArtifactDescriptorPolicy(new SimpleArtifactDescriptorPolicy(false, false));
		try {
			Map<String, List<Dependency>> scopeDeps = depIds.stream()
				.map(coord -> toDependency(toArtifact(coord)))
				.collect(Collectors.groupingBy(Dependency::getScope));

			List<Dependency> deps = scopeDeps.getOrDefault(JavaScopes.COMPILE, Collections.emptyList());
			List<Dependency> managedDeps = deps.stream()
				.flatMap(d -> getManagedDependencies(strictSession, d).stream())
				.collect(Collectors.toList());

			if (scopeDeps.containsKey("import")) {
				// If there are any @pom artifacts we'll apply their
				// managed dependencies to the given dependencies BEFORE ordinary deps
				List<Dependency> boms = scopeDeps.getOrDefault("import", Collections.emptyList());
				List<Dependency> mdeps = boms.stream()
					.flatMap(d -> getManagedDependencies(strictSession, d).stream())
					.collect(Collectors.toList());
				deps = deps.stream().map(d -> applyManagedDependencies(d, mdeps)).collect(Collectors.toList());
				managedDeps.addAll(0, mdeps);
			}

			CollectRequest collectRequest = new CollectRequest()
				.setManagedDependencies(managedDeps)
				.setDependencies(deps)
				.setRepositories(context.remoteRepositories());

			DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
			DependencyResult dependencyResult = context.repositorySystem()
				.resolveDependencies(context.repositorySystemSession(),
						dependencyRequest);
			List<ArtifactResult> artifacts = dependencyResult.getArtifactResults();

			if (downloadSources) {
				Util.infoMsg("Resolving sources for dependencies...");
			}

			return artifacts.stream()
				.map(ar -> {
					if (downloadSources) {
						downloadSources(ar.getArtifact());
					}
					return ar.getArtifact();
				})
				.map(ArtifactResolver::toArtifactInfo)
				.collect(Collectors.toList());
		} catch (DependencyResolutionException ex) {
			throw new ExitException(1, "Could not resolve dependencies: " + ex.getMessage(), ex);
		}
	}

	private AbstractRepositoryListener setupSessionLogging() {
		return new AbstractRepositoryListener() {

			@Override
			public void metadataResolving(RepositoryEvent event) {
				Metadata md = event.getMetadata();
				printEvent(md.getGroupId(), md.getArtifactId(), md.getVersion(), md.getType(), null);
			}

			@Override
			public void metadataDownloading(RepositoryEvent event) {
				Metadata md = event.getMetadata();
				printEvent(md.getGroupId(), md.getArtifactId(), md.getVersion(), md.getType(), null);
			}

			@Override
			public void artifactResolving(RepositoryEvent event) {
				Artifact art = event.getArtifact();
				printEvent(art.getGroupId(), art.getArtifactId(), art.getVersion(), art.getExtension(),
						art.getClassifier());
			}

			@Override
			public void artifactResolved(RepositoryEvent event) {
				Artifact art = event.getArtifact();
				printEvent(art.getGroupId(), art.getArtifactId(), art.getVersion(), art.getExtension(),
						art.getClassifier());
			}

			@Override
			public void artifactDownloading(RepositoryEvent event) {
				Artifact art = event.getArtifact();
				printEvent(art.getGroupId(), art.getArtifactId(), art.getVersion(), art.getExtension(),
						art.getClassifier());
			}

			@SuppressWarnings("unchecked")
			private void printEvent(String groupId, String artId, String version, String type, String classifier) {
				RepositorySystemSession session = context.repositorySystemSession();
				List<String> depIds = (List<String>) session.getData().get("depIds");
				if (depIds == null) {
					return;
				}
				Set<String> ids = (Set<String>) session.getData()
					.computeIfAbsent("ids", () -> new HashSet<>(depIds));
				Set<String> printed = (Set<String>) session.getData()
					.computeIfAbsent("printed", () -> new HashSet<>());

				String id = coord(groupId, artId, null, null, classifier);
				if (!printed.contains(id)) {
					String coord = coord(groupId, artId, version, null, classifier);
					String pomcoord = coord(groupId, artId, version, "pom", null);
					if (ids.contains(id) || ids.contains(coord) || ids.contains(pomcoord) || Util.isVerbose()) {
						if (ids.contains(pomcoord)) {
							infoMsg("   " + pomcoord);
						} else {
							infoMsg("   " + coord);
						}
						printed.add(id);
					}
				}
			}

			private String coord(String groupId, String artId, String version, String type, String classifier) {
				String res = groupId + ":" + artId;
				if (version != null && !version.isEmpty()) {
					res += ":" + version;
				}
				if (classifier != null && classifier.length() > 0) {
					res += "-" + classifier;
				}
				if ("pom".equals(type)) {
					res += "@" + type;
				}

				return res;
			}
		};
	}

	private Dependency applyManagedDependencies(Dependency d, List<Dependency> managedDeps) {
		Artifact art = d.getArtifact();
		if (art.getVersion().isEmpty()) {
			Optional<Artifact> ma = managedDeps.stream()
				.map(Dependency::getArtifact)
				.filter(a -> a.getGroupId().equals(art.getGroupId())
						&& a.getArtifactId().equals(art.getArtifactId()))
				.findFirst();
			if (ma.isPresent()) {
				return new Dependency(ma.get(), d.getScope(), d.getOptional(), d.getExclusions());
			}
		}
		return d;
	}

	private List<Dependency> getManagedDependencies(RepositorySystemSession session, Dependency dependency) {
		return resolveDescriptor(session, dependency.getArtifact()).getManagedDependencies();
	}

	private ArtifactDescriptorResult resolveDescriptor(RepositorySystemSession session, Artifact artifact) {
		try {
			if (artifact.getVersion().trim().isEmpty()) {
				return new ArtifactDescriptorResult(
						new ArtifactDescriptorRequest(artifact, context.remoteRepositories(), ""));
			}
			// one must resolve version, as it may be range; reading descriptor is possible
			// only from exact versions
			VersionRangeRequest versionRangeRequest = new VersionRangeRequest().setArtifact(artifact)
				.setRepositories(
						context.remoteRepositories());
			VersionRangeResult versionRangeResult = context.repositorySystem()
				.resolveVersionRange(session, versionRangeRequest);
			if (versionRangeResult.getVersions().isEmpty()) {
				throw new ExitException(1, "Could not resolve version range: " + artifact);
			}
			String version = versionRangeResult.getVersions()
				.get(versionRangeResult.getVersions().size() - 1)
				.toString();
			ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest()
				.setArtifact(
						artifact.setVersion(
								version))
				.setRepositories(
						context.remoteRepositories());
			return context.repositorySystem()
				.readArtifactDescriptor(session, descriptorRequest);
		} catch (VersionRangeResolutionException | ArtifactDescriptorException ex) {
			throw new ExitException(1, "Could not read artifact descriptor for " + artifact, ex);
		}
	}

	private RemoteRepository toRemoteRepo(MavenRepo repo) {
		return new RemoteRepository.Builder(repo.getId(), "default", repo.getUrl())
			.build();

	}

	private static Dependency toDependency(Artifact artifact) {
		return new Dependency(artifact,
				"pom".equalsIgnoreCase(artifact.getExtension()) ? "import" : JavaScopes.COMPILE);
	}

	private Artifact toArtifact(String coord) {
		return toArtifact(MavenCoordinate.fromString(coord));
	}

	private Artifact toArtifact(MavenCoordinate coord) {
		String cls = coord.getClassifier();
		String ext = coord.getType();
		if (coord.getType() != null) {
			ArtifactType type = context.repositorySystemSession().getArtifactTypeRegistry().get(coord.getType());
			if (type != null) {
				ext = type.getExtension();
				cls = Optional.ofNullable(cls).orElse(type.getClassifier());
				return new DefaultArtifact(coord.getGroupId(), coord.getArtifactId(), cls, ext, coord.getVersion(),
						type);
			}
		}
		return new DefaultArtifact(coord.getGroupId(), coord.getArtifactId(), cls, ext, coord.getVersion());
	}

	private static ArtifactInfo toArtifactInfo(Artifact artifact) {
		MavenCoordinate coord = new MavenCoordinate(artifact.getGroupId(), artifact.getArtifactId(),
				artifact.getVersion(), artifact.getClassifier(), artifact.getExtension());
		return new ArtifactInfo(coord, artifact.getFile().toPath());
	}

	public static Path getLocalMavenRepo() {
		try (ArtifactResolver ar = Builder.create().localFolder(Settings.getJBangLocalMavenRepoOverride()).build()) {
			return ar.context
				.repositorySystemSession()
				.getLocalRepository()
				.getBasedir()
				.toPath();
		}
	}
}
