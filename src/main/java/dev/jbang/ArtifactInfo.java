package dev.jbang;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinates;

/**
 * Interface describing artifact coordinates and its resolved physical location.
 */
public interface ArtifactInfo {
	MavenCoordinate getCoordinate();

	File asFile();

	default public String toExternalString() {
		String path = asFile().getAbsolutePath();

		try {
			path = URLEncoder.encode(path, StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException e) {
			Util.errorMsg("Could not encode " + path, e);
		}

		return getCoordinate().toCanonicalForm() + "=" + path;
	}

	static public ArtifactInfo fromExternalString(String externalString) {

		MavenCoordinate coordinate = MavenCoordinates.createCoordinate(
				externalString.substring(0, externalString.indexOf("=")));
		File file = new File(externalString.substring(externalString.indexOf("=") + 1, externalString.length()));

		return new ArtifactInfo() {

			@Override
			public MavenCoordinate getCoordinate() {
				return coordinate;
			}

			@Override
			public File asFile() {
				return file;
			}
		};
	}

}
