package dev.jbang.dependencies;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.io.FileNotFoundException;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.Project;
import dev.jbang.source.Source;
import dev.jbang.source.sources.JavaSource;

public class TestGrape extends BaseTest {

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

		Source src = new JavaSource(ResourceRef.forLiteral(grabBlock), null);
		Project prj = Project.builder().build(src);
		List<String> deps = prj.getMainSourceSet().getDependencies();

		assertThat(deps, hasItem("org.hibernate:hibernate-core:5.4.10.Final"));
		assertThat(deps, hasItem("net.sf.json-lib:json-lib:2.2.3:jdk15"));
		assertThat(deps, hasItem("org.restlet:org.restlet:1.1.6"));
		assertThat(deps, hasItem("log4j:log4j:1.2.17"));
		assertThat(deps, not(hasItem("blah:borked:1.0@wonka")));

	}

}
