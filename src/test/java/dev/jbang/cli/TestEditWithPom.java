package dev.jbang.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.source.RunContext;
import dev.jbang.source.SourceSet;
import dev.jbang.source.TestSource;
import dev.jbang.util.Util;

public class TestEditWithPom extends BaseTest {

	final String main = "//usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n" +
			"//DEPS io.quarkus:quarkus-bom:2.0.0.Final@pom\n" +
			"//DEPS io.quarkus:quarkus-rest-client-reactive\n" +
			"//DEPS io.quarkus:quarkus-rest-client-reactive-jackson\n" +
			"//Q:CONFIG quarkus.banner.enabled=false\n" +
			"//Q:CONFIG quarkus.log.level=WARN\n" +
			"//JAVAC_OPTIONS -parameters\n" +
			"\n" +
			"import javax.ws.rs.client.ClientBuilder;\n" +
			"\n" +
			"import io.quarkus.runtime.QuarkusApplication;\n" +
			"import io.quarkus.runtime.annotations.QuarkusMain;\n" +
			"import javax.ws.rs.client.ClientBuilder;\n" +
			"\n" +
			"@QuarkusMain\n" +
			"public class main implements QuarkusApplication {\n" +
			"\n" +
			"    @Override\n" +
			"    public int run(String... args) throws Exception {\n" +
			"        System.out.println(\"start\");\n" +
			"        var result = ClientBuilder.newClient().target(\"https://google.com\").request();\n" +
			"        return 0;\n" +
			"    }\n" +
			"}";

	@Test
	void testEditWithPom() throws IOException {
		Path mainPath = TestSource.createTmpFileWithContent("", "main.java", main);
		assertTrue(mainPath.toFile().exists());
		RunContext ctx = RunContext.empty();
		SourceSet ss = (SourceSet) ctx.forResource(mainPath.toString());
		File project = new Edit().createProjectForEdit(ss, ctx, false);
		assertTrue(new File(project, "src/main.java").exists());

		File gradle = new File(project, "build.gradle");
		assert (gradle.exists());
		String buildGradle = Util.readString(gradle.toPath());
		assertThat(buildGradle, containsString("implementation platform")); // should be com.github

	}

}
