package dev.jbang.dependencies;

import java.util.Objects;

import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenRemoteRepositories;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenRemoteRepository;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenUpdatePolicy;

public class MavenRepo {

	private String id;
	private String url;

	public MavenRepo(String id, String url) {
		this.setId(id);
		this.setUrl(url);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public void apply(ConfigurableMavenResolverSystem resolver, boolean updateCache) {
		String name = getId() == null ? getUrl() : getId();
		MavenRemoteRepository repository = MavenRemoteRepositories.createRemoteRepository(name, getUrl(), "default");
		if (updateCache) {
			repository.setUpdatePolicy(MavenUpdatePolicy.UPDATE_POLICY_ALWAYS);
		}
		resolver.withRemoteRepo(repository);
	}

	@Override
	public String toString() {
		return String.format("%s=%s", id, url);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		MavenRepo mavenRepo = (MavenRepo) o;
		return Objects.equals(id, mavenRepo.id) && Objects.equals(url, mavenRepo.url);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, url);
	}
}
