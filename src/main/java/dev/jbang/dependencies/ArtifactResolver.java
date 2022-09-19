package dev.jbang.dependencies;

import static dev.jbang.util.Util.infoMsg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Activation;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.DefaultAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;

import dev.jbang.cli.ExitException;
import dev.jbang.util.Util;

public class ArtifactResolver {
	private final List<RemoteRepository> repositories;
	private final int timeout;
	private final boolean offline;
	private final boolean withUserSettings;
	private final Path settingsXml;
	private final Path localFolderOverride;
	private final boolean updateCache;
	private final boolean loggingEnabled;

	private final RepositorySystem system;
	private final Settings settings;
	private final DefaultRepositorySystemSession session;

	public static class Builder {
		private List<MavenRepo> repositories;
		private int timeout;
		private boolean offline;
		private boolean withUserSettings;
		private Path localFolder;
		private Path settingsXml;
		private boolean updateCache;
		private boolean loggingEnabled;

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

		public Builder logging(boolean logging) {
			this.loggingEnabled = logging;
			return this;
		}

		public ArtifactResolver build() {
			return new ArtifactResolver(this);
		}
	}

	private ArtifactResolver(Builder builder) {
		this.timeout = builder.timeout;
		this.offline = builder.offline;
		this.withUserSettings = builder.withUserSettings;
		this.settingsXml = builder.settingsXml;
		this.localFolderOverride = builder.localFolder;
		this.updateCache = builder.updateCache;
		this.loggingEnabled = builder.loggingEnabled;

		system = newRepositorySystem();
		settings = newEffectiveSettings();
		session = newSession(system, settings);
		configureSession(session, settings);
		if (builder.repositories != null) {
			repositories = builder.repositories.stream().map(this::toRemoteRepo).collect(Collectors.toList());
		} else {
			repositories = newRepositories();
		}
	}

	public List<ArtifactInfo> resolve(List<String> depIds) {
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
																.setRepositories(repositories);

			DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
			DependencyResult dependencyResult = system.resolveDependencies(session, dependencyRequest);
			List<ArtifactResult> artifacts = dependencyResult.getArtifactResults();

			return artifacts.stream()
							.map(ArtifactResult::getArtifact)
							.map(ArtifactResolver::toArtifactInfo)
							.collect(Collectors.toList());
		} catch (DependencyResolutionException ex) {
			throw new ExitException(1, "Could not resolve dependencies: " + ex.getMessage(), ex);
		}
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
																									repositories);
			return system.readArtifactDescriptor(session, descriptorRequest);
		} catch (ArtifactDescriptorException ex) {
			throw new ExitException(1, "Could not read artifact descriptor for " + artifact, ex);
		}
	}

	private RemoteRepository toRemoteRepo(MavenRepo repo) {
		return new RemoteRepository.Builder(repo.getId(), "default", repo.getUrl())
																					.setPolicy(getUpdatePolicy())
																					.build();
	}

	private static Dependency toDependency(Artifact artifact) {
		return new Dependency(artifact,
				"pom".equalsIgnoreCase(artifact.getExtension()) ? "import" : JavaScopes.COMPILE);
	}

	private static Artifact toArtifact(String coord) {
		return toArtifact(MavenCoordinate.fromString(coord));
	}

	private static Artifact toArtifact(MavenCoordinate coord) {
		return new DefaultArtifact(coord.getGroupId(), coord.getArtifactId(), coord.getClassifier(),
				coord.getPackaging(), coord.getVersion());
	}

	private static ArtifactInfo toArtifactInfo(Artifact artifact) {
		MavenCoordinate coord = new MavenCoordinate(artifact.getGroupId(), artifact.getArtifactId(),
				artifact.getVersion(), artifact.getClassifier(), artifact.getExtension());
		return new ArtifactInfo(coord, artifact.getFile().toPath());
	}

	private RepositorySystem newRepositorySystem() {
		DefaultServiceLocator locator = MavenRepositorySystemUtils	.newServiceLocator()
																	.addService(RepositoryConnectorFactory.class,
																			BasicRepositoryConnectorFactory.class)
																	.addService(TransporterFactory.class,
																			FileTransporterFactory.class)
																	.addService(TransporterFactory.class,
																			HttpTransporterFactory.class);
		return locator.getService(RepositorySystem.class);
	}

	private Settings newEffectiveSettings() {
		DefaultSettingsBuilderFactory factory = new DefaultSettingsBuilderFactory();
		DefaultSettingsBuilder builder = factory.newInstance();

		SettingsBuildingRequest settingsBuilderRequest = new DefaultSettingsBuildingRequest();
		settingsBuilderRequest.setSystemProperties(System.getProperties());

		if (withUserSettings) {
			// find the settings
			Path settingsFile = settingsXml;
			if (settingsFile == null) {
				Path userSettings = Paths.get(System.getProperty("user.home"), ".m2", "settings.xml");
				if (Files.exists(userSettings)) {
					settingsFile = userSettings.toAbsolutePath();
				}
			}
			if (settingsFile != null) {
				settingsBuilderRequest.setUserSettingsFile(settingsFile.toFile());
			}
		}

		// read it
		SettingsBuildingResult settingsBuildingResult;
		try {
			settingsBuildingResult = builder.build(settingsBuilderRequest);
		} catch (SettingsBuildingException e) {
			throw new RuntimeException(e);
		}

		return settingsBuildingResult.getEffectiveSettings();
	}

	private DefaultRepositorySystemSession newSession(RepositorySystem system, Settings settings) {
		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
		LocalRepository localRepo = newLocalRepository(settings);
		session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
		return session;
	}

	private LocalRepository newLocalRepository(Settings set) {
		String localRepository = localFolderOverride != null ? localFolderOverride.toString() : null;
		if (localRepository == null && set != null)
			localRepository = set.getLocalRepository();
		if (localRepository == null)
			localRepository = System.getProperty("user.home") + File.separator + ".m2" + File.separator + "repository";
		else if (!new File(localRepository).isAbsolute())
			localRepository = Util.getCwd().resolve(localRepository).toAbsolutePath().toString();
		return new LocalRepository(localRepository);
	}

	private void configureSession(DefaultRepositorySystemSession session, Settings settings) {
		// set up mirrors
		DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
		for (Mirror mirror : settings.getMirrors()) {
			mirrorSelector.add(mirror.getId(), mirror.getUrl(), mirror.getLayout(), false, false,
					mirror.getMirrorOf(), mirror.getMirrorOfLayouts());
		}
		session.setMirrorSelector(mirrorSelector);

		// set up proxies
		DefaultProxySelector proxySelector = new DefaultProxySelector();
		for (Proxy proxy : settings.getProxies()) {
			if (proxy.isActive()) {
				AuthenticationBuilder authBuilder = new AuthenticationBuilder();
				authBuilder.addUsername(proxy.getUsername()).addPassword(proxy.getPassword());
				proxySelector.add(
						new org.eclipse.aether.repository.Proxy(proxy.getProtocol(), proxy.getHost(), proxy.getPort(),
								authBuilder.build()),
						proxy.getNonProxyHosts());
			}
		}
		session.setProxySelector(proxySelector);

		// set up authentication
		DefaultAuthenticationSelector authenticationSelector = new DefaultAuthenticationSelector();
		for (Server server : settings.getServers()) {
			AuthenticationBuilder authBuilder = new AuthenticationBuilder();
			authBuilder.addUsername(server.getUsername()).addPassword(server.getPassword());
			authBuilder.addPrivateKey(server.getPrivateKey(), server.getPassphrase());
			authenticationSelector.add(server.getId(), authBuilder.build());

			if (server.getConfiguration() != null) {
				Xpp3Dom dom = (Xpp3Dom) server.getConfiguration();
				for (int i = dom.getChildCount() - 1; i >= 0; i--) {
					Xpp3Dom child = dom.getChild(i);
					if ("httpHeaders".equals(child.getName())) {
						HashMap<String, String> headers = new HashMap<>();
						Xpp3Dom[] properties = child.getChildren("property");
						for (Xpp3Dom property : properties) {
							headers.put(property.getChild("name").getValue(), property.getChild("value").getValue());
						}
						session.setConfigProperty(ConfigurationProperties.HTTP_HEADERS + "." + server.getId(), headers);
					}
				}
			}

			session.setConfigProperty("aether.connector.perms.fileMode." + server.getId(),
					server.getFilePermissions());
			session.setConfigProperty("aether.connector.perms.dirMode." + server.getId(),
					server.getDirectoryPermissions());
		}
		session.setAuthenticationSelector(authenticationSelector);

		if (loggingEnabled) {
			session.setRepositoryListener(new AbstractRepositoryListener() {
				@Override
				public void artifactDownloading(RepositoryEvent event) {
					if ("jar".equalsIgnoreCase(event.getArtifact().getExtension())) {
						infoMsg("          " + event.getArtifact());
					}
				}

				@Override
				public void artifactResolving(RepositoryEvent event) {
					if (Util.isVerbose() && "jar".equalsIgnoreCase(event.getArtifact().getExtension())) {
						infoMsg("          [" + event.getArtifact() + "]");
					}
				}
			});
		}

		session.setUpdatePolicy(getUpdatePolicy().getUpdatePolicy());

		// connection settings
		session.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, timeout);
		session.setOffline(offline || settings.isOffline());
	}

	private List<RemoteRepository> newRepositories() {
		List<RemoteRepository> repos = new ArrayList<>();
		repos.add(toRemoteRepo(new MavenRepo("central", "https://repo1.maven.org/maven2/")));
		return repos;
	}

	private List<RemoteRepository> getRepositoriesFromProfile(Settings set) {
		List<RemoteRepository> repos = new ArrayList<>();
		if (set != null) {
			Set<String> activeProfiles = getActiveProfiles(set);
			for (String profileId : activeProfiles) {
				Profile profile = set.getProfilesAsMap().get(profileId);
				if (profile != null) {
					addReposFromProfile(repos, profile);
				}
			}
		}
		return repos;
	}

	private static Set<String> getActiveProfiles(Settings set) {
		Set<String> activeProfiles = new HashSet<>(set.getActiveProfiles());
		for (Profile profile : set.getProfiles()) {
			Activation activation = profile.getActivation();
			if (activation != null) {
				if (activation.isActiveByDefault())
					activeProfiles.add(profile.getId());
			}
		}
		return activeProfiles;
	}

	private void addReposFromProfile(List<RemoteRepository> repos, Profile profile) {
		String policy = getUpdatePolicy().getUpdatePolicy();
		for (Repository repo : profile.getRepositories()) {
			RemoteRepository.Builder remoteRepo = new RemoteRepository.Builder(repo.getId(), repo.getLayout(),
					repo.getUrl());

			// policies
			org.apache.maven.settings.RepositoryPolicy repoReleasePolicy = repo.getReleases();
			if (repoReleasePolicy != null) {
				String updatePolicy = repoReleasePolicy.getUpdatePolicy();
				if (updatePolicy == null || updatePolicy.isEmpty()) {
					updatePolicy = policy;
				}
				RepositoryPolicy releasePolicy = new RepositoryPolicy(repoReleasePolicy.isEnabled(), updatePolicy,
						repoReleasePolicy.getChecksumPolicy());
				remoteRepo.setReleasePolicy(releasePolicy);
			}

			org.apache.maven.settings.RepositoryPolicy repoSnapshotPolicy = repo.getSnapshots();
			if (repoSnapshotPolicy != null) {
				String updatePolicy = repoSnapshotPolicy.getUpdatePolicy();
				// This is the default anyway and saves us a message on STDERR
				if (updatePolicy == null || updatePolicy.isEmpty()) {
					updatePolicy = policy;
				}
				RepositoryPolicy snapshotPolicy = new RepositoryPolicy(repoSnapshotPolicy.isEnabled(), updatePolicy,
						repoSnapshotPolicy.getChecksumPolicy());
				remoteRepo.setSnapshotPolicy(snapshotPolicy);
			}

			// auth, proxy and mirrors are done in the session
			repos.add(remoteRepo.build());
		}
	}

	private RepositoryPolicy getUpdatePolicy() {
		if (updateCache) {
			return new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS,
					RepositoryPolicy.CHECKSUM_POLICY_WARN);
		} else {
			return new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_DAILY,
					RepositoryPolicy.CHECKSUM_POLICY_WARN);
		}
	}
}
