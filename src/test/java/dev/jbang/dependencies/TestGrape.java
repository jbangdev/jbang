package dev.jbang.dependencies;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileNotFoundException;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
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

		Source src = new JavaSource(grabBlock, null);
		Project prj = Project.builder().build(src);
		List<String> deps = prj.getMainSourceSet().getDependencies();

		assertThat(deps).contains("org.hibernate:hibernate-core:5.4.10.Final");
		assertThat(deps).contains("net.sf.json-lib:json-lib:2.2.3:jdk15");
		assertThat(deps).contains("org.restlet:org.restlet:1.1.6");
		assertThat(deps).contains("log4j:log4j:1.2.17");
		assertThat(deps).doesNotContain("blah:borked:1.0@wonka");

	}

}
