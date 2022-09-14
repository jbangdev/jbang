package dev.jbang.dependencies;

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
	public String toString() {
		return String.format("%s=%s", id, url);
	}
}
