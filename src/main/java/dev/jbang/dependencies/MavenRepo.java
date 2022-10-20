package dev.jbang.dependencies;

import java.util.Objects;

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

	@Override
	public String toString() {
		return String.format("%s=%s", id, url);
	}
}
