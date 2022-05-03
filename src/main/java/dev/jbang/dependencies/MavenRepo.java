package dev.jbang.dependencies;

import java.util.Objects;

import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;

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

	public String getRepoId() {
		return id == null ? url : id;
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

	public void apply(ConfigurableMavenResolverSystem resolver) {
		resolver.withRemoteRepo(getRepoId(), getUrl(), "default");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		MavenRepo mavenRepo = (MavenRepo) o;
		return getRepoId().equals(mavenRepo.id) && url.equals(mavenRepo.url);
	}

	@Override
	public int hashCode() {
		return Objects.hash(getRepoId(), url);
	}

	@Override
	public String toString() {
		return String.format("%s=%s", id, url);
	}
}
