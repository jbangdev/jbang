package dev.jbang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestGrape extends BaseTest {

	public static final String EXAMPLES_FOLDER = "examples";
	static File examplesTestFolder;

	@BeforeAll
	static void init() throws URISyntaxException {
		URL examplesUrl = TestGrape.class.getClassLoader().getResource(EXAMPLES_FOLDER);
		examplesTestFolder = new File(new File(examplesUrl.toURI()).getAbsolutePath());
	}

	@Test
	void testFindGrabs() throws FileNotFoundException {

		String grabBlock = "@Grapes({\n" +
				"\t\t@Grab(\"log4j:log4j:1.2.17\"),\n" +
				"\t\t@Grab(group = \"org.hibernate\", module = \"hibernate-core\", version = \"5.4.10.Final\"),\n" +
				"\t\t@Grab(group=\"net.sf.json-lib\", module=\"json-lib\", version=\"2.2.3\", classifier=\"jdk15\"), // classifier\n"
				+
				"\t\t@Grab(group=\"org.restlet\", version=\"1.1.6\", module=\"org.restlet\")  // different order\n" +
				"\t\t@Grab(group=\"org.restlet\", version=\"1.1.6\", module=\"org.restlet\", ext=\"wonka\")  // different order\n"
				+
				"\t//\t@Grab(group=\"blah\", version=\"1.0\", module=\"borked\", ext=\"wonka\")  // commented\n"
				+
				"})\n";

		Script s = new Script(grabBlock, null, null);

		List<String> deps = s.collectDependencies();

		assertThat(deps, hasItem("org.hibernate:hibernate-core:5.4.10.Final"));
		assertThat(deps, hasItem("net.sf.json-lib:json-lib:2.2.3:jdk15"));
		assertThat(deps, hasItem("org.restlet:org.restlet:1.1.6"));
		assertThat(deps, hasItem("log4j:log4j:1.2.17"));
		assertThat(deps, not(hasItem("blah:borked:1.0@wonka")));

	}

}
