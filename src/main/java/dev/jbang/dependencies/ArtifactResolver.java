package dev.jbang.dependencies;

import static dev.jbang.util.Util.infoMsg;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
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
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.artifact.SubArtifact;

import dev.jbang.cli.ExitException;
import dev.jbang.util.Util;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;

public class ArtifactResolver {
	private final Context context;
	private final boolean downloadSources;

	public static class Builder {
		private final Context rootContext;
		private List<MavenRepo> repositories;
		private boolean downloadSources = false;
		private boolean loggingEnabled = false;

		public static Builder create(Context rootContext) {
			return new Builder(rootContext);
		}

		private Builder(Context rootContext) {
			this.rootContext = rootContext;
		}

		public Builder repositories(List<MavenRepo> repositories) {
			this.repositories = repositories;
			return this;
		}

		public Builder downloadSources(boolean downloadSources) {
			this.downloadSources = downloadSources;
			return this;
		}

		public Builder logging(boolean logging) {
			this.loggingEnabled = logging;
			return this;
		}

		public ArtifactResolver build() {
			return new ArtifactResolver(this);
		}
	}

	private ArtifactResolver(Builder builder) {
		List<RemoteRepository> partialRepos;
		if (builder.repositories != null) {
			partialRepos = builder.repositories.stream().map(this::toRemoteRepo).collect(Collectors.toList());
		} else {
			partialRepos = null;
		}

		this.downloadSources = builder.downloadSources;
		this.context = builder.rootContext.customize(
				ContextOverrides.Builder.create()
										.repositories(partialRepos)
										.repositoryListener(builder.loggingEnabled ? listener() : null)
										.build());
	}

	public void downloadSources(Artifact artifact) {
		try {
			context	.repositorySystem()
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
		try {
			Map<String, List<Dependency>> scopeDeps = depIds.stream()
															.map(coord -> toDependency(toArtifact(coord)))
															.collect(Collectors.groupingBy(Dependency::getScope));

			List<Dependency> deps = scopeDeps.get(JavaScopes.COMPILE);
			List<Dependency> managedDeps = null;
			if (scopeDeps.containsKey("import")) {
				// If there are any @pom artifacts we'll apply their
				// managed dependencies to the given dependencies
				List<Dependency> boms = scopeDeps.get("import");
				List<Dependency> mdeps = boms	.stream()
												.flatMap(d -> getManagedDependencies(d).stream())
												.collect(Collectors.toList());
				deps = deps.stream().map(d -> applyManagedDependencies(d, mdeps)).collect(Collectors.toList());
				managedDeps = mdeps;
			}

			CollectRequest collectRequest = new CollectRequest()
																.setManagedDependencies(managedDeps)
																.setDependencies(deps)
																.setRepositories(context.remoteRepositories());

			DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
			DependencyResult dependencyResult = context	.repositorySystem()
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

	private AbstractRepositoryListener listener() {
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
				Set<String> ids = (Set<String>) session	.getData()
														.computeIfAbsent("ids", () -> new HashSet<>(depIds));
				Set<String> printed = (Set<String>) session	.getData()
															.computeIfAbsent("printed", HashSet::new);

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
			Optional<Artifact> ma = managedDeps	.stream()
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

	private List<Dependency> getManagedDependencies(Dependency dependency) {
		return resolveDescriptor(dependency.getArtifact()).getManagedDependencies();
	}

	private ArtifactDescriptorResult resolveDescriptor(Artifact artifact) {
		try {
			ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest()
																							.setArtifact(artifact)
																							.setRepositories(
																									context.remoteRepositories());
			return context	.repositorySystem()
							.readArtifactDescriptor(context.repositorySystemSession(), descriptorRequest);
		} catch (ArtifactDescriptorException ex) {
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
			}
		}
		return new DefaultArtifact(coord.getGroupId(), coord.getArtifactId(), cls, ext, coord.getVersion());
	}

	private static ArtifactInfo toArtifactInfo(Artifact artifact) {
		MavenCoordinate coord = new MavenCoordinate(artifact.getGroupId(), artifact.getArtifactId(),
				artifact.getVersion(), artifact.getClassifier(), artifact.getExtension());
		return new ArtifactInfo(coord, artifact.getFile().toPath());
	}
}
