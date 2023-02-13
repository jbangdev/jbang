package dev.jbang.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.source.sources.JavaSource;

public class TestProject extends BaseTest {

	@Test
	void testCDS() {
		Source source = new JavaSource("//CDS\nclass m { }", null);
		Source source2 = new JavaSource("class m { }", null);

		Project prj = ProjectBuilder.create().build(source);
		Project prj2 = ProjectBuilder.create().build(source2);

		assertTrue(prj.enableCDS());
		assertFalse(prj2.enableCDS());
	}
}
