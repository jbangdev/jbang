package dk.xam.jbang;

import java.util.Optional;

public class MavenRepo {

	private String id;
	private String url;
	private String user;
	private String password;

	public MavenRepo(String id, String url, String user, String password) {
		this.setId(id);
		this.setUrl(url);
		this.setUser(Optional.ofNullable(user).orElse(""));
		this.setPassword(Optional.ofNullable(password).orElse(""));
		// TODO Auto-generated constructor stub
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

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
